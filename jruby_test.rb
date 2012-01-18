require 'java'

CURRENT_PATH = File.expand_path File.dirname(__FILE__)
DML_CONF_PATH = File.join(CURRENT_PATH, 'src', 'common', 'dml')
ISPN_CONF_PATH = File.join(CURRENT_PATH, 'config')

# load the jars
LIB_PATH = File.join(CURRENT_PATH, 'lib')
Dir[File.join(LIB_PATH, '*.jar')].each{|jar|
  #next if jar.match(/fenix|jvstm/)
  puts "Loading JAR: #{jar}"
  require jar
}

#require File.join(LIB_PATH, 'jvstm.jar')
#require File.join(LIB_PATH, 'fenix-framework-r53358.jar')

$CLASSPATH << LIB_PATH

# load the domain model jar
DIST_PATH = File.join(CURRENT_PATH, 'dist')
require File.join(DIST_PATH, 'geograph-domain.jar')
$CLASSPATH << DIST_PATH


# Load Fenix Framework
FenixConfig = Java::PtIstFenixframework::Config
FenixFramework = Java::PtIstFenixframework::FenixFramework

# Load the domain models
FenixGeoObject = Java::ItAlgoGeographDomain::GeoObject
FenixRoot = Java::ItAlgoGeographDomain::Root

# Load the CloudTM glue framework
CloudTmInit = Java::OrgCloudtmFramework::Init
CloudTmTxSystem = Java::OrgCloudtmFramework::TxSystem
CloudTmConfig = Java::OrgCloudtmFramework::CloudtmConfig


# In order to bypass the use of the constructor with closure, that causes problems
# in the jruby binding.
# Here we open the Fenix Config class and we define a method that permits to
# valorize the same protected variables managed by the standard constructor.
class FenixConfig
  # Accepts an hash of params, keys are instance variables of FenixConfig class
  # and values are used to valorize these variables.
  def init params
    params.each do |name, value|
      set_param(name, value)
    end
  end

  private

  # Sets an instance variable value.
  def set_param(name, value)
    # Jruby doesn't offer accessors for the protected variables.
    field = self.java_class.declared_field name
    field.accessible = true
    field.set_value Java.ruby_to_java(self), Java.ruby_to_java(value)
  end
end

class CloudTmTransactionManager
  #cattr_accessor :manager

  class << self
    def manager
      @manager
    end

    def manager=(man)
      @manager = man
    end
  end
end

# This is the Fenix Framework loader. It provides a simple way to
# run the framework initialization process.
class FenixLoader
  # Load the Fenix Framework.
  # Options:
  # => dml: the dml file name
  # => conf: the configuration file name
  # => root: the root class
  def self.load(options)
    config = FenixConfig.new
    config.init(
      :domainModelPath => File.join(DML_CONF_PATH, options[:dml]),
      :dbAlias => File.join(ISPN_CONF_PATH, options[:conf]),
      :rootClass => FenixRoot.java_class,
      :repositoryType => FenixConfig::RepositoryType::INFINISPAN
    )

    CloudTmInit.initializeTxSystem(config, CloudTmConfig::Framework::FENIX)
    CloudTmTransactionManager.manager = CloudTmTxSystem.getManager
  end
end


class CloudTmGeoObject < FenixGeoObject
  class << self

    def create attrs = {}
      instance = nil
      manager = CloudTmTransactionManager.manager
      manager.withTransaction do

        instance = FenixGeoObject.new
        attrs.each do |attr, value|
          instance.send("set#{attr.to_s.camelize}", value)
        end
        manager.save instance
        instance.set_root manager.getRoot
        java.lang.Void
      end
      instance
    end
    
    def all
      manager = CloudTmTransactionManager.manager
      geo_objects = []
      manager.withTransaction do
        geo_objects = manager.getRoot.getGeoObjects
      end
      geo_objects
    end

  end
end


FenixLoader.load({
  :dml => 'geograph.dml',
  :conf => 'infinispanNoFile.xml'
})


geo_objects = CloudTmGeoObject.all
puts "Found #{geo_objects.inspect} in the cache"

geo_object = CloudTmGeoObject.create({
  :latitude => 45,
  :longitude => 23
})

puts "Created geo object: lat = #{geo_object.latitude} - lon = #{geo_object.longitude}"
