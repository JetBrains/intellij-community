package com.intellij.codeInsight.generation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author dsl
 */
public class GenerateEqualsHelper implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateEqualsHelper");
  private PsiClass myClass;
  private PsiField[] myEqualsFields;
  private PsiField[] myHashCodeFields;
  private HashSet myNonNullSet;
  private final PsiElementFactory myFactory;
  private final PsiManager myManager;
  private String myParameterName;
  private static final String BASE_OBJECT_PARAMETER_NAME = "object";
  private final PsiClass myJavaLangObject;
  private PsiType myObjectType;
  private PsiType myClassType;
  private String myClassInstanceName;

  private static final com.intellij.util.containers.HashMap PRIMITIVE_HASHCODE_FORMAT = new com.intellij.util.containers.HashMap();
  private final boolean mySuperHasHashCode;
  private CodeStyleManager myCodeStyleManager;
  private final Project myProject;

  public static class NoObjectClassException extends Exception{
  }

  public GenerateEqualsHelper(Project project, PsiClass aClass, PsiField[] equalsFields, PsiField[] hashCodeFields,
                              PsiField[] nonNullFields) throws NoObjectClassException {
    myClass = aClass;
    myEqualsFields = equalsFields;
    myHashCodeFields = hashCodeFields;
    myProject = project;

    myNonNullSet = new HashSet();
    for (int i = 0; i < nonNullFields.length; i++) {
      PsiField field = nonNullFields[i];
      myNonNullSet.add(field);
    }
    myManager = PsiManager.getInstance(project);

    myFactory = myManager.getElementFactory();
    myJavaLangObject = myManager.findClass("java.lang.Object", aClass.getResolveScope());
    if (myJavaLangObject == null){
      throw new NoObjectClassException();
    }

    boolean tmp = superMethodExists(getHashCodeSignature(myProject));
    mySuperHasHashCode = tmp;
    myCodeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
  }

  private String getUniqueLocalVarName(String base, PsiField[] fields) {
    String id = base;
    int index = 0;
    while (true) {
      if (index > 0) {
        id = base + index;
      }
      index++;
      boolean anyEqual = false;
      for (int i = 0; i < fields.length; i++) {
        PsiField equalsField = fields[i];
        if (id.equals(equalsField.getName())) {
          anyEqual = true;
          break;
        }
        ;
      }
      if (!anyEqual) break;
    }


    return id;
  }

  public void run() {
    try {
      final PsiElement[] members = generateMembers();
      for (int i = 0; i < members.length; i++) {
        PsiElement member = members[i];
        myClass.add(member);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public PsiElement[] generateMembers() throws IncorrectOperationException {
    PsiMethod equals = null;
    if (myEqualsFields != null && findMethod(myClass, getEqualsSignature(myProject)) == null) {
      equals = createEquals();
    }

    PsiMethod hashCode = null;
    if (myHashCodeFields != null && findMethod(myClass, getHashCodeSignature(myProject)) == null) {
      if (myHashCodeFields.length > 0) {
        hashCode = createHashCode();
      } else {
        if (!mySuperHasHashCode) {
          final PsiMethod trivialHashCode =
                  myFactory.createMethodFromText("public int hashCode() {\nreturn 0;\n}", null);
          hashCode = (PsiMethod) myCodeStyleManager.reformat(trivialHashCode);
        }
      }
    }
    if (hashCode != null && equals != null) {
      return new PsiMethod[]{equals, hashCode};
    } else if (equals != null) {
      return new PsiMethod[]{equals};
    } else if (hashCode != null) {
      return new PsiMethod[]{hashCode};
    } else {
      return null;
    }
  }


  private PsiMethod createEquals() throws IncorrectOperationException {
    CodeStyleManager codeStyleManager = myCodeStyleManager;
    myObjectType = myFactory.createType(myJavaLangObject);
    String[] nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, myObjectType).names;
    final String objectBaseName = nameSuggestions.length > 0 ? nameSuggestions[0] : BASE_OBJECT_PARAMETER_NAME;
    myParameterName = getUniqueLocalVarName(objectBaseName, myEqualsFields);
    myClassType = myFactory.createType(myClass);
    nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, myClassType).names;
    String instanceBaseName = nameSuggestions.length > 0 ? nameSuggestions[0] : BASE_OBJECT_PARAMETER_NAME;
    if (instanceBaseName.equals(objectBaseName)) {
      instanceBaseName = "instance";
    }
    myClassInstanceName = getUniqueLocalVarName(instanceBaseName, myEqualsFields);

    StringBuffer buffer = new StringBuffer();
    buffer.append("public boolean equals(Object " + myParameterName + ") {\n");
    addEqualsPrologue(buffer);
    if (myEqualsFields.length > 0) {
      addClassInstance(buffer);

      ArrayList equalsFields = new ArrayList();
      for (int i = 0; i < myEqualsFields.length; i++) {
        equalsFields.add(myEqualsFields[i]);
      }
      Collections.sort(equalsFields, EqualsFieldsComparator.INSTANCE);

      for (Iterator iterator = equalsFields.iterator(); iterator.hasNext();) {
        PsiField field = (PsiField) iterator.next();

        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          if (field.getType() instanceof PsiArrayType) {
            addArrayEquals(buffer, field);
          } else if (field.getType() instanceof PsiPrimitiveType) {
              addPrimitiveFieldComparison(buffer, field);
            } else {
              addFieldComparison(buffer, field);
            }
        }
      }
    }
    buffer.append("\nreturn true;\n}");
    PsiMethod result = myFactory.createMethodFromText(buffer.toString(), null);

    PsiMethod method = (PsiMethod) myCodeStyleManager.reformat(result);
    method = (PsiMethod) myCodeStyleManager.shortenClassReferences(method);
    return method;
  }


  private static final MessageFormat ARRAY_COMPARER_MF =
          new MessageFormat("if(!java.util.Arrays.equals({1}, {0}.{1})) return false;\n");
  private static final MessageFormat FIELD_COMPARER_MF =
          new MessageFormat("if({1}!=null ? !{1}.equals({0}.{1}) : {0}.{1}!= null)return false;\n");
  private static final MessageFormat NON_NULL_FIELD_COMPARER_MF =
          new MessageFormat("if(!{1}.equals({0}.{1}))return false;\n");
  private static final MessageFormat PRIMITIVE_FIELD_COMPARER_MF =
          new MessageFormat("if({1}!={0}.{1})return false;\n");


  private void addArrayEquals(StringBuffer buffer, PsiField field) {
    final PsiType fieldType = field.getType();
    if (isNestedArray(fieldType)) {
      buffer.append(" // Compare nested arrays - values of " + field.getName() + " here\n");
      return;
    }
    if (isArrayOfObjects(fieldType)) {
      buffer.append(" // Probably incorrect - comparing Object[] arrays with Arrays.equals\n");
    }

    ARRAY_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
  }

  private Object[] getComparerFormatParameters(PsiField field) {
    return new Object[]{myClassInstanceName, field.getName()};
  }


  private void addFieldComparison(StringBuffer buffer, PsiField field) {
    boolean canBeNull = !myNonNullSet.contains(field);
    final String name = field.getName();
    final String instanceFieldRef = myClassInstanceName + "." + name;
    if (canBeNull) {
      FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
      /*buffer.append("if(");
      buffer.append(name + " != null ? !");
      buffer.append(name);
      buffer.append(".equals(");
      buffer.append(instanceFieldRef);
      buffer.append(") : ");
      buffer.append(instanceFieldRef + "!= null");
      buffer.append(") return false;\n");*/
    } else {
      NON_NULL_FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
      /*buffer.append("if(!" + name + ".equals(" + instanceFieldRef + ")) return false;\n");*/
    }
  }

  private void addPrimitiveFieldComparison(StringBuffer buffer, PsiField field) {
    PRIMITIVE_FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
  }

  private void addInstanceOfToText(StringBuffer buffer, String returnValue) {
    buffer.append("if(!(" + myParameterName + " instanceof " + myClass.getName() + ")) " +
            "return " + returnValue + ";\n");
  }

  private void addEqualsPrologue(StringBuffer buffer) {
    buffer.append("if(this==" + myParameterName + ") return true;\n");
    if (!superMethodExists(getEqualsSignature(myProject))) {
      addInstanceOfToText(buffer, "false");
    } else {
      addInstanceOfToText(buffer, "false");
      buffer.append("if(!super.equals(");
      buffer.append(myParameterName);
      buffer.append(")) return false;\n");
    }
  }

  private void addClassInstance(StringBuffer buffer) {
    buffer.append("\n");
    // A a = (A) object;
    buffer.append("final ");
    buffer.append(myClass.getName());
    buffer.append(" " + myClassInstanceName + " = (");
    buffer.append(myClass.getName());
    buffer.append(")");
    buffer.append(myParameterName);
    buffer.append(";\n\n");
  }


  private boolean superMethodExists(MethodSignature methodSignature) {
    LOG.assertTrue(myClass.isValid());
    PsiMethod superEquals = MethodSignatureUtil.findMethodBySignature(myClass, methodSignature, true);
    if (superEquals == null) return true;
    if (superEquals.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    return !superEquals.getContainingClass().getQualifiedName().equals("java.lang.Object");
  }

  private PsiMethod createHashCode() throws IncorrectOperationException {
    StringBuffer buffer = new StringBuffer();
    buffer.append("public int hashCode() {\n");

    if(!mySuperHasHashCode && myHashCodeFields.length == 1) {
      PsiField field = myHashCodeFields[0];
      final String tempName = addTempForOneField(field, buffer);
      buffer.append("return ");
      if(field.getType() instanceof PsiPrimitiveType) {
        addPrimitiveFieldHashCode(buffer, field, tempName);
      } else {
        addFieldHashCode(buffer, field);
      }
      buffer.append(";\n}");
    } else if (myHashCodeFields.length > 0) {
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myCodeStyleManager.getProject());
      final String resultName = getUniqueLocalVarName(
              settings.LOCAL_VARIABLE_NAME_PREFIX + "result", myHashCodeFields);

      buffer.append("int ");
      buffer.append(resultName);

      boolean resultAssigned = false;
      if (mySuperHasHashCode) {
        buffer.append(" = ");
        addSuperHashCode(buffer);
        resultAssigned = true;
      }
      buffer.append(";\n");
      String tempName = addTempDeclaration(buffer);
      for (int i = 0; i < myHashCodeFields.length; i++) {
        PsiField field = myHashCodeFields[i];

        addTempAssignment(field, buffer, tempName);
        buffer.append(resultName);
        buffer.append(" = ");
        if (resultAssigned) {
          buffer.append("29*");
          buffer.append(resultName);
          buffer.append(" + ");
        }
        if (field.getType() instanceof PsiPrimitiveType) {
          addPrimitiveFieldHashCode(buffer, field, tempName);
        } else {
          addFieldHashCode(buffer, field);
        }
        buffer.append(";\n");
        resultAssigned = true;
      }
      buffer.append("return ");
      buffer.append(resultName);
      buffer.append(";\n}");
    } else {
      buffer.append("return 0;\n}");
    }
    PsiMethod hashCode = myFactory.createMethodFromText(buffer.toString(), null);
    return (PsiMethod) myCodeStyleManager.reformat(hashCode);
  }

  private void addTempAssignment(PsiField field, StringBuffer buffer, String tempName) {
    if (field.getType() == PsiType.DOUBLE) {
      buffer.append(tempName);
      addTempForDoubleInitialization(field, buffer);
    }
  }

  private void addTempForDoubleInitialization(PsiField field, StringBuffer buffer) {
    buffer.append(" = " + field.getName() + " != +0.0d ? Double.doubleToLongBits(");
    buffer.append(field.getName());
    buffer.append(") : 0l;\n");
  }

  private String addTempDeclaration(StringBuffer buffer) {
    for (int i = 0; i < myHashCodeFields.length; i++) {
      PsiField hashCodeField = myHashCodeFields[i];
      if (PsiType.DOUBLE == hashCodeField.getType()) {
        final String name = getUniqueLocalVarName("temp", myHashCodeFields);
        buffer.append("long " + name + ";\n");
        return name;
      }
    }
    return null;
  }

  private String addTempForOneField(PsiField field, StringBuffer buffer) {
    if(field.getType() == PsiType.DOUBLE) {
      final String name = getUniqueLocalVarName("temp", myHashCodeFields);
      buffer.append("final long " + name);
      addTempForDoubleInitialization(field, buffer);
      return name;
    } else {
      return null;
    }
  }

  private void addPrimitiveFieldHashCode(StringBuffer buffer, PsiField field, String tempName) {
    MessageFormat format = (MessageFormat) PRIMITIVE_HASHCODE_FORMAT.get(field.getType().getCanonicalText());
    buffer.append(format.format(new Object[]{field.getName(), tempName}));
/*
    String boxedTypeName = (String) BOXED_OF_PRIMITIVE.get(field.getType().getCanonicalText());
    LOG.assertTrue(boxedTypeName != null);
    buffer.append("new ");
    buffer.append(boxedTypeName);
    buffer.append("(");
    buffer.append(field.getName());
    buffer.append(").hashCode()");
*/
  }

  private void addFieldHashCode(StringBuffer buffer, PsiField field) {
    final String name = field.getName();
    if (myNonNullSet.contains(field)) {
      buffer.append(name);
      buffer.append(".hashCode()");
    } else {
      buffer.append("(");
      buffer.append(name);
      buffer.append(" != null ? ");
      buffer.append(name);
      buffer.append(".hashCode() : 0)");
    }
  }

  private void addSuperHashCode(StringBuffer buffer) {
    if (mySuperHasHashCode) {
      buffer.append("super.hashCode()");
    } else {
      buffer.append("0");
    }
  }


  public void invoke() {
    ApplicationManager.getApplication().runWriteAction(this);
  }

  static PsiMethod findMethod(PsiClass aClass, MethodSignature signature) {
    return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
  }

  static class EqualsFieldsComparator implements Comparator {
    public static final EqualsFieldsComparator INSTANCE = new EqualsFieldsComparator();

    public int compare(Object o1, Object o2) {
      PsiField f1 = (PsiField) o1;
      PsiField f2 = (PsiField) o2;

      if (f1.getType() instanceof PsiPrimitiveType && !(f2.getType() instanceof PsiPrimitiveType)) return -1;
      if (!(f1.getType() instanceof PsiPrimitiveType) && f2.getType() instanceof PsiPrimitiveType) return 1;
      return f1.getName().compareTo(f2.getName());
    }
  }

  static {
    final MessageFormat castFormat = new MessageFormat("(int) {0}");
    PRIMITIVE_HASHCODE_FORMAT.put("byte", castFormat);
    PRIMITIVE_HASHCODE_FORMAT.put("short", castFormat);
    PRIMITIVE_HASHCODE_FORMAT.put("int", new MessageFormat("{0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("long", new MessageFormat("(int) ({0} ^ ({0} >>> 32))"));
    PRIMITIVE_HASHCODE_FORMAT.put("boolean", new MessageFormat("({0} ? 1 : 0)"));

    PRIMITIVE_HASHCODE_FORMAT.put("float", new MessageFormat("{0} != +0.0f ? Float.floatToIntBits({0}) : 0"));
    PRIMITIVE_HASHCODE_FORMAT.put("double", new MessageFormat("(int) ({1} ^ ({1} >>> 32))"));

    PRIMITIVE_HASHCODE_FORMAT.put("char", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("void", new MessageFormat("0"));
    PRIMITIVE_HASHCODE_FORMAT.put("void", new MessageFormat("({0} ? 1 : 0)"));
  }

  public static boolean isNestedArray(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType) aType).getComponentType();
    if (componentType == null) return false;
    if (componentType instanceof PsiArrayType) return true;
    return false;
  }

  public static boolean isArrayOfObjects(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType) aType).getComponentType();
    if (componentType == null) return false;
    final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
    if (psiClass == null) return false;
    final String qName = psiClass.getQualifiedName();
    return "java.lang.Object".equals(qName);
  }

  public static MethodSignature getHashCodeSignature(Project project) {
    return MethodSignatureUtil.createMethodSignature("hashCode",(PsiType[])null,null,PsiSubstitutor.EMPTY);
  }

  public static MethodSignature getEqualsSignature(Project project) {
    return MethodSignatureUtil.createMethodSignature("equals",new PsiType[]{PsiType.getJavaLangObject(PsiManager.getInstance(project))},null,PsiSubstitutor.EMPTY);
  }
}
