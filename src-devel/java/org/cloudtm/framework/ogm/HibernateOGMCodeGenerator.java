package org.cloudtm.framework.ogm;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

import dml.CodeGenerator;
import dml.CompilerArgs;
import dml.DomainClass;
import dml.DomainEntity;
import dml.DomainModel;
import dml.ExternalizationElement;
import dml.Role;
import dml.Slot;
import dml.ValueType;

public class HibernateOGMCodeGenerator extends CodeGenerator {

    protected static final String DOMAIN_CLASS_ROOT = "org.cloudtm.framework.ogm.AbstractDomainObject";
    protected PrintWriter ormWriter;
    protected ArrayList<Slot> ormSlots;
    protected ArrayList<Role> ormRoleManyToOne;
    protected ArrayList<Role> ormRoleOneToMany;
    protected ArrayList<Role> ormRoleOneToOne;
    protected ArrayList<Role> ormRoleManyToMany;
    protected ArrayList<String> ormTransientSlots;

    public HibernateOGMCodeGenerator(CompilerArgs compArgs, DomainModel domainModel) {
	super(compArgs, domainModel);
    }

    @Override
    public void generateCode() {
        File file = new File(getBaseDirectoryFor("") + "/META-INF/orm.xml");
        file.getParentFile().mkdirs();

        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            this.ormWriter = new PrintWriter(fileWriter, true);
            ormBeginFile();
            super.generateCode();
            ormGenerateNonBaseClasses(getDomainModel().getClasses());
            ormEndFile();
        } catch (IOException ioe) {
            throw new Error("Can't open file " + file);
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    protected void generateBaseClass(DomainClass domClass, PrintWriter out) {
        ormBeginBaseClass(domClass);
        super.generateBaseClass(domClass, out);
        ormEndBaseClass();
        // ormGenerateNonBaseClass(domClass);
    }

    @Override
    protected void generateBaseClassBody(DomainClass domClass, PrintWriter out) {
        generateStaticSlots(domClass, out);
        newline(out);

        generateSlots(domClass.getSlots(), out);
        newline(out);
        newline(out);
        generateRoleSlots(domClass.getRoleSlots(), out);

        generateDefaultConstructor(domClass.getBaseName(), out);

        generateSlotsAccessors(domClass.getSlots(), out);

        generateRoleSlotsMethods(domClass.getRoleSlots(), out);
    }

    @Override
    protected void generateStaticRoleSlots(Role role, PrintWriter out) {
        onNewline(out);

        Role otherRole = role.getOtherRole();

        // The Role slot
        String roleType = getRoleType(role);
        printWords(out, "public", "static", roleType, getRoleHandlerName(role, false), "=", "new", roleType);
        print(out, "(");
        print(out, getRoleArgs(role));
        print(out, ")");
        newBlock(out);
        if (role.getMultiplicityUpper() == 1) {
            generateRoleMethodAdd(role, otherRole, out);
            generateRoleMethodRemove(role, otherRole, out);
        } else {
            generateRoleClassGetter(role, otherRole, out);
        }
        generateRoleMethodGetInverseRole(role, otherRole, out);
        closeBlock(out, false);
        println(out, ";");
    }

    protected void generateDefaultConstructor(String classname, PrintWriter out) {
        printMethod(out, "public", "", classname);
        startMethodBody(out);
        endMethodBody(out);
    }

    protected void generateRoleMethodAdd(Role role, Role otherRole, PrintWriter out) {
        boolean multOne = (role.getMultiplicityUpper() == 1);
        
        String otherRoleTypeFullName = getTypeFullName(otherRole.getType());
        String roleTypeFullName = getTypeFullName(role.getType());

        printMethod(out, "public", "void", "add",
                    makeArg(otherRoleTypeFullName, "o1"),
                    makeArg(roleTypeFullName, "o2"),
                    makeArg(makeGenericType("dml.runtime.Relation",otherRoleTypeFullName,roleTypeFullName), "relation"));
        startMethodBody(out);
        print(out, "if (o1 != null)");
        newBlock(out);
        println(out, roleTypeFullName + " old2 = ((" + otherRole.getType().getBaseName() + ")o1)." + role.getName() + ";");
        print(out, "if (o2 != old2)");
        newBlock(out);
        println(out, "relation.remove(o1, old2);");
        println(out, otherRole.getType().getBaseName() + " o1Base = (" + otherRole.getType().getBaseName() + ")o1;");
        print(out, "o1Base." + role.getName() + " = o2;");
        closeBlock(out, false);
        closeBlock(out, false);
        endMethodBody(out);
    }

    protected void generateRoleMethodRemove(Role role, Role otherRole, PrintWriter out) {
        boolean multOne = (role.getMultiplicityUpper() == 1);
        
        String otherRoleTypeFullName = getTypeFullName(otherRole.getType());
        String roleTypeFullName = getTypeFullName(role.getType());

        printMethod(out, "public", "void", "remove",
                    makeArg(otherRoleTypeFullName, "o1"),
                    makeArg(roleTypeFullName, "o2"));
        startMethodBody(out);
        print(out, "if (o1 != null)");
        newBlock(out);
        println(out, otherRole.getType().getBaseName() + " o1Base = (" + otherRole.getType().getBaseName() + ")o1;");
        print(out, "o1Base." + role.getName() + " = null;");
        closeBlock(out, false);
        endMethodBody(out);
    }

    // In this class this method is only invoked when the role's multiplicity is > 1
    @Override
    protected void generateRoleClassGetter(Role role, Role otherRole, PrintWriter out) {
        print(out, "public ");
        print(out, makeGenericType("dml.runtime.RelationBaseSet", getTypeFullName(role.getType())));
        print(out, " ");
        print(out, "getSet(");
        print(out, getTypeFullName(otherRole.getType()));
        print(out, " o1)");
        startMethodBody(out);
        // println(out, "if (o1 instanceof dml.runtime.RelationBaseSet) {");
        // print(out, "  return (dml.runtime.RelationBaseSet)((");
        // print(out, otherRole.getType().getBaseName());
        // print(out, ")o1).");
        // print(out, role.getName());
        // println(out, ";");
        // println(out, "} else {");
        // print(out, "  return (dml.runtime.RelationBaseSet)((");
        // print(out, otherRole.getType().getBaseName());
        // print(out, ")o1).");
        // print(out, role.getName());
        // println(out, ";");

        print(out, "return (dml.runtime.RelationBaseSet)((");
        print(out, otherRole.getType().getBaseName());
        print(out, ")o1).");
        print(out, role.getName());
        print(out, ";");
        endMethodBody(out);
    }    

    protected void generateRoleMethodGetInverseRole(Role role, Role otherRole, PrintWriter out) {
        // the getInverseRole method
        String inverseRoleType = makeGenericType("dml.runtime.Role",
                                                 getTypeFullName(role.getType()),
                                                 getTypeFullName(otherRole.getType()));
        printMethod(out, "public", inverseRoleType, "getInverseRole");
        startMethodBody(out);
        print(out, "return ");
        if (otherRole.getName() == null) {
            print(out, "new ");
            print(out, getRoleType(otherRole));
            print(out, "(this)");
        } else {
            print(out, getRoleHandlerName(otherRole, true));
        }
        print(out, ";");
        endMethodBody(out);
    }

    // @Override
    // protected void generateSlots(Iterator slotsIter, PrintWriter out) {
    //     while (slotsIter.hasNext()) {
    //         generateSlot((Slot) slotsIter.next(), out);
    //     }
    // }

    @Override
    protected void generateSlot(Slot slot, PrintWriter out) {
        ormAddSlot(slot);
        onNewline(out);
        // printWords(out, "private", getReferenceType(slot.getTypeName()), slot.getName());
        printWords(out, "private", slot.getTypeName(), slot.getName());
        print(out, ";");
    }

    @Override
    protected void generateGetterBody(String slotName, String typeName, PrintWriter out) {
        print(out, "return " + getSlotExpression(slotName) + ";");
    }

    @Override
    protected void generateSetterBody(String setterName, String slotName, String typeName, PrintWriter out) {
        print(out, getSlotExpression(slotName) + " = " + slotName + ";");
    }

    @Override
    protected void generateRoleSlot(Role role, PrintWriter out) {
        ormAddRole(role);
        onNewline(out);
        if (role.getMultiplicityUpper() == 1) {
            printWords(out, "private", getTypeFullName(role.getType()), role.getName());
            // ormGenerateRoleSlotMultOne(role);
        } else {
            printWords(out, "private", getSetTypeDeclarationFor(role), role.getName());
            printWords(out, "=", getNewRoleStarSlotExpressionWithEmptySet(role));

            println(out, ";");
            onNewline(out);
            printWords(out, "private", getSetTypeDeclarationFor(role), addHibernateToSlotName(role.getName()));
            printWords(out, "=", "new", makeGenericType("java.util.HashSet", getTypeFullName(role.getType())) + "()");
            // ormGenerateRoleSlotMultMany(role);
        }
        println(out, ";");
    }

    protected String getSetTypeDeclarationFor(Role role) {
        String elemType = getTypeFullName(role.getType());
        return makeGenericType("java.util.Set", elemType);
        // String thisType = getTypeFullName(role.getOtherRole().getType());
        // return makeGenericType(getRelationAwareBaseTypeFor(role), thisType, elemType);
    }

    protected String getConcreteSetTypeDeclarationFor(Role role) {
        String elemType = getTypeFullName(role.getType());
        // return makeGenericType("java.util.Set", elemType);
        String thisType = getTypeFullName(role.getOtherRole().getType());
        return makeGenericType(getRelationAwareBaseTypeFor(role), thisType, elemType);
    }

    @Override
    protected void generateRoleSlotMethodsMultStar(Role role, PrintWriter out) {
        super.generateRoleSlotMethodsMultStar(role, out);

        String paramListType = makeGenericType("java.util.List", getTypeFullName(role.getType()));

        generateIteratorMethod(role, out);
        generateRoleMultGetterSetter(role, out);
    }

    protected void generateIteratorMethod(Role role, PrintWriter out) {
        newline(out);
        printFinalMethod(out, "public", makeGenericType("java.util.Iterator", getTypeFullName(role.getType())), "get"
        	+ capitalize(role.getName()) + "Iterator");
        startMethodBody(out);
        printWords(out, "return", getSlotExpression(role.getName()));
        print(out, ".iterator();");
        endMethodBody(out);
    }

    protected void generateRoleMultGetterSetter(Role role, PrintWriter out) {
        generateRoleMultGetter(role, out);
        generateRoleMultSetter(role, out);
    }

    protected void generateRoleMultGetter(Role role, PrintWriter out) {
        newline(out);
        // printFinalMethod(out, "public", makeGenericType("java.util.Set", getTypeFullName(role.getType())), "get"
        printFinalMethod(out, "public", getSetTypeDeclarationFor(role), "get" + capitalize(role.getName()));
        startMethodBody(out);
        printWords(out, "return", getSlotExpression(role.getName()) + ";");
        endMethodBody(out);

        newline(out);
        printFinalMethod(out, "public", getSetTypeDeclarationFor(role), "get" + addHibernateToSlotName(capitalize(role.getName())));
        startMethodBody(out);
        printWords(out, "return", "((" + getRelationAwareTypeFor(role) + ")" + getSlotExpression(role.getName()) + ").getToHibernate();");
        endMethodBody(out);

    }

    protected void generateRoleMultSetter(Role role, PrintWriter out) {
        newline(out);
        printFinalMethod(out, "private", "void", "set" + addHibernateToSlotName(capitalize(role.getName())),
                         makeArg(getSetTypeDeclarationFor(role), role.getName()));
        startMethodBody(out);
        printWords(out, "((" + getRelationAwareTypeFor(role) + ")this." + role.getName() + ").setFromHibernate(" + role.getName() + ");");
        endMethodBody(out);

        // // NOT REALLY NEEDED
        // printFinalMethod(out, "private", "void", "set" + capitalize(role.getName()),
        //                  makeArg(getSetTypeDeclarationFor(role), role.getName()));
        // startMethodBody(out);
        // // printWords(out, "System.out.println(\"Hello from JAPAN!!!!!!!!!!!!!!!!: " + role.getName() + "\");");
        // printWords(out, "this." + role.getName(), "=", getNewRoleStarSlotExpression(role) + ";");
        // // printWords(out, "this." + role.getName(), "=", role.getName() + ";");
        // endMethodBody(out);
    }

    @Override
    protected String getNewRoleStarSlotExpression(Role role) {
        return getNewRoleStarSlotExpressionWithBackingSet(role, role.getName());
    }

    protected String getNewRoleStarSlotExpressionWithEmptySet(Role role) {
        StringBuilder buf = new StringBuilder();
        buf.append("new ");
        buf.append(makeGenericType("java.util.HashSet", getTypeFullName(role.getType())));
        buf.append("()");
        return getNewRoleStarSlotExpressionWithBackingSet(role, buf.toString());
    }

    protected String getNewRoleStarSlotExpressionWithBackingSet(Role role, String theSet) {
        StringBuilder buf = new StringBuilder();

        // generate the relation aware collection
        String thisType = getTypeFullName(role.getOtherRole().getType());
        buf.append("new ");
        buf.append(getRelationAwareTypeFor(role));
        buf.append("(");
        buf.append(theSet);
        buf.append(", ");
        buf.append("(");
        buf.append(thisType);
        buf.append(")this, ");
        buf.append(getRelationSlotNameFor(role));
        buf.append(")");

        return buf.toString();
    }

    @Override
    protected String getRoleOneBaseType() {
        return "dml.runtime.Role";
    }

    @Override
    protected String getDomainClassRoot() {
        return DOMAIN_CLASS_ROOT;
    }

    @Override
    protected String getRelationAwareBaseTypeFor(Role role) {
        // FIXME: handle other types of collections other than sets
        return "org.cloudtm.framework.ogm.RelationSet";
    }

    protected String addHibernateToSlotName(String slotName) {
        return slotName + "$via$hibernate";
    }

    ///////////////////////////////////////////////////////////////////////////
    // Below are methods specific to the generation of the ORM mapping XML file

    protected void ormBeginFile() {
        StringBuilder buf = new StringBuilder();
        buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n");
        buf.append("<entity-mappings\n");
        buf.append("    xmlns=\"http://java.sun.com/xml/ns/persistence/orm\"\n");
        buf.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        buf.append("    xsi:schemaLocation=\"http://java.sun.com/xml/ns/persistence/orm orm_2_0.xsd\"\n");
        buf.append("    version=\"2.0\">\n\n");

        buf.append("    <mapped-superclass class=\"" + getDomainClassRoot() + "\" access=\"FIELD\" metadata-complete=\"true\">\n");
        buf.append("        <attributes>\n");
        buf.append("            <id name=\"oid\">\n");
        buf.append("                  <generated-value strategy=\"AUTO\"/>\n");
        buf.append("            </id>\n");
        buf.append("        </attributes>\n");
        buf.append("    </mapped-superclass>\n\n");
        this.ormWriter.print(buf.toString());
    }

    protected void ormBeginBaseClass(DomainClass domClass) {
        this.ormSlots = new ArrayList<Slot>();
        this.ormRoleManyToOne = new ArrayList<Role>();
        this.ormRoleOneToMany = new ArrayList<Role>();
        this.ormRoleOneToOne = new ArrayList<Role>();
        this.ormRoleManyToMany = new ArrayList<Role>();
        this.ormTransientSlots = new ArrayList<String>();

        StringBuilder buf = new StringBuilder();
        buf.append("    <mapped-superclass class=\"");
        buf.append(domClass.getPackageName());
        buf.append(".");
        buf.append(domClass.getBaseName());
        buf.append("\" metadata-complete=\"true\">\n");
        buf.append("        <attributes>\n");
        this.ormWriter.print(buf.toString());
    }

    protected void ormEndBaseClass() {
        // slots must be dumped in this order per the schema definition :-/
        for (Slot slot : this.ormSlots) {
            ormGenerateSlot(slot);
        }
        for (Role role : this.ormRoleManyToOne) {
            ormGenerateRoleManyToOne(role);
        }
        for (Role role : this.ormRoleOneToMany) {
            ormGenerateRoleOneToMany(role);
        }
        for (Role role : this.ormRoleOneToOne) {
            ormGenerateRoleOneToOne(role);
        }
        for (Role role : this.ormRoleManyToMany) {
            ormGenerateRoleManyToMany(role);
        }
        for (String name : this.ormTransientSlots) {
            ormGenerateTransient(name);
        }
        

        StringBuilder buf = new StringBuilder();
        buf.append("        </attributes>\n");
        buf.append("    </mapped-superclass>\n");
        this.ormWriter.println(buf.toString());
    }

    protected void ormGenerateSlot(Slot slot) {
        StringBuilder buf = new StringBuilder();
        buf.append("            <basic name=\"");
        buf.append(slot.getName());
        buf.append("\"/>");
        this.ormWriter.println(buf.toString());
    }

    protected void ormGenerateRoleManyToOne(Role role) {
        StringBuilder buf = new StringBuilder();
        buf.append("            <many-to-one name=\"");
        buf.append(role.getName());
        buf.append("\" target-entity=\"");
        buf.append(getTypeFullName(role.getType()));
        buf.append("\">");
        // buf.append(ormGetCascade());
        buf.append("</many-to-one>");
        this.ormWriter.println(buf.toString());
    }

    protected void ormGenerateRoleOneToMany(Role role) {
        StringBuilder buf = new StringBuilder();
        buf.append("            <one-to-many name=\"");
        buf.append(addHibernateToSlotName(role.getName()));
        buf.append("\" target-entity=\"");
        buf.append(getTypeFullName(role.getType()));
        buf.append("\" mapped-by=\"");
        buf.append(role.getOtherRole().getName());
        // buf.append("\" collection-type=\"org.cloudtm.framework.ogm.RelationSet\" access=\"PROPERTY\"/>");
        buf.append("\" access=\"PROPERTY\">");
        // buf.append(ormGetCascade());
        buf.append("</one-to-many>");
        this.ormWriter.println(buf.toString());
    }

    protected void ormGenerateRoleOneToOne(Role role) {
        StringBuilder buf = new StringBuilder();
        buf.append("            <one-to-one fetch=\"LAZY\" name=\"");
        buf.append(role.getName());
        buf.append("\">");
        // buf.append(ormGetCascade());
        buf.append("</one-to-one>");
        this.ormWriter.println(buf.toString());
    }

    protected void ormGenerateRoleManyToMany(Role role) {
        StringBuilder buf = new StringBuilder();
        buf.append("            <many-to-many name=\"");
        buf.append(addHibernateToSlotName(role.getName()));
        buf.append("\" access=\"PROPERTY\">");
        // buf.append(ormGetCascade());
        buf.append("</many-to-many>");
        this.ormWriter.println(buf.toString());
    }

    protected void ormGenerateTransient(String name) {
        StringBuilder buf = new StringBuilder();
        buf.append("            <transient name=\"");
        buf.append(name);
        buf.append("\" />");
        this.ormWriter.println(buf.toString());
    }

    protected String ormGetCascade() {
        return "<cascade><cascade-persist/><cascade-merge/><cascade-refresh/><cascade-detach/></cascade>";
    }

    protected String ormGetRoleMultiplicity(Role role) {
        return (role.getMultiplicityUpper() == 1) ? "one" : "many";
    }

    protected void ormGenerateNonBaseClasses(Iterator classesIter) {
        StringBuilder buf = new StringBuilder();
        while (classesIter.hasNext()) {
            buf.append("    <entity class=\"");
            buf.append(getEntityFullName((DomainClass) classesIter.next()));
            buf.append("\" metadata-complete=\"true\"/>\n");
        }
        this.ormWriter.println(buf.toString());
    }

    protected void ormEndFile() {
        this.ormWriter.println("</entity-mappings>");
    }

    protected void ormAddSlot(Slot slot) {
        this.ormSlots.add(slot);
    }

    protected void ormAddRole(Role role) {
        Role otherRole = role.getOtherRole();

        if (role.getMultiplicityUpper() == 1) {
            if (otherRole.getMultiplicityUpper() == 1) {
                this.ormRoleOneToOne.add(role);
            } else {
                this.ormRoleManyToOne.add(role);
            }
        } else {
            this.ormTransientSlots.add(role.getName());
            if (otherRole.getMultiplicityUpper() == 1) {
                this.ormRoleOneToMany.add(role);
            } else {
                this.ormRoleManyToMany.add(role);
            }
        }


        // ArrayList<Role> theList =
        //     (role.getMultiplicityUpper() == 1
        //      ? (otherRole.getMultiplicityUpper() == 1 ?
        //         this.ormRoleOneToOne : this.ormRoleManyToOne)
        //      : (otherRole.getMultiplicityUpper() == 1 ?
        //         this.ormRoleOneToMany : this.ormRoleManyToMany));
        // theList.add(role);
             
        

        // if (role.getMultiplicityUpper() == 1) {
        //     if (otherRole.getMultiplicityUpper() == 1) {
        //         this.ormRoleOneToOne.add(role);
        //     } else {
        //         this.ormRoleManyToOne.add(role);
        //     }
        // } else {
        //     if (otherRole.getMultiplicityUpper() == 1) {
        //         this.ormRoleOneToMany.add(role);
        //     } else {
        //         this.ormRoleManyToMany.add(role);
        //     }
        // }
    }
}
