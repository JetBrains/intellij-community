package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.PrimitiveType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: lex
 * Date: Sep 2, 2003
 * Time: 11:25:59 AM
 */
public class JVMNameUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.JVMNameUtil");

  public static String getPrimitiveSignature(String typeName) {
    if(PsiType.BOOLEAN.getCanonicalText().equals(typeName)) {
      return "Z";
    }
    else if (PsiType.BYTE.getCanonicalText().equals(typeName)) {
      return "B";
    }
    else if (PsiType.CHAR.getCanonicalText().equals(typeName)) {
      return "C";
    }
    else if (PsiType.SHORT.getCanonicalText().equals(typeName)) {
      return "S";
    }
    else if (PsiType.INT.getCanonicalText().equals(typeName)) {
      return "I";
    }
    else if (PsiType.LONG.getCanonicalText().equals(typeName)) {
      return "J";
    }
    else if (PsiType.FLOAT.getCanonicalText().equals(typeName)) {
      return "F";
    }
    else if (PsiType.DOUBLE.getCanonicalText().equals(typeName)) {
      return "D";
    }
    else if (PsiType.VOID.getCanonicalText().equals(typeName)) {
      return "V";
    }
    return null;
  }

  private static void appendJVMSignature(JVMNameBuffer buffer , PsiType type)
    throws EvaluateException {
    final PsiType psiType = TypeConversionUtil.erasure(type);
    if (psiType instanceof PsiArrayType) {
      buffer.append(new JVMRawText("["));
      appendJVMSignature(buffer, ((PsiArrayType) psiType).getComponentType());
    }
    else if (psiType instanceof PsiClassType) {
      buffer.append("L");

      final JVMName jvmName = getJVMQualifiedName(psiType);

      if(jvmName instanceof JVMRawText) {
        buffer.append(((JVMRawText)jvmName).getName().replace('.','/'));
      } else {
        buffer.append(new JVMName() {
          public String getName(DebugProcessImpl process) throws EvaluateException {
            return jvmName.getName(process).replace('.','/');
          }

          public String getDisplayName(DebugProcessImpl debugProcess) {
            return jvmName.getDisplayName(debugProcess);
          }
        });
      }
      buffer.append(";");
    }
    else if (psiType instanceof PsiPrimitiveType) {
      buffer.append(getPrimitiveSignature(psiType.getCanonicalText()));
    }
    else {
      LOG.assertTrue(false, "unknown type " + type.getCanonicalText());
    }
  }

  private static class JVMNameBuffer {
    List<JVMName> myList = new ArrayList<JVMName>();

    public void append(JVMName evaluator){
      LOG.assertTrue(evaluator != null);
      myList.add(evaluator);
    }

    public void append(char name){
      append(Character.toString(name));
    }

    public void append(String text){
      myList.add(getJVMRawText(text));
    }

    public JVMName toName() {
      final List<JVMName> optimised = new ArrayList<JVMName>();
      for (Iterator iterator = myList.iterator(); iterator.hasNext();) {
        JVMName evaluator = (JVMName) iterator.next();
        if(evaluator instanceof JVMRawText && optimised.size() > 0 && optimised.get(optimised.size() - 1) instanceof JVMRawText){
          JVMRawText nameEvaluator = (JVMRawText) optimised.get(optimised.size() - 1);
          nameEvaluator.setName(nameEvaluator.getName() + ((JVMRawText)evaluator).getName());
        } else {
          optimised.add(evaluator);
        }
      }

      if(optimised.size() == 1) return optimised.get(0);
      if(optimised.size() == 0) return new JVMRawText("");

      return new JVMName() {
        String myName = null;
        public String getName(DebugProcessImpl process) throws EvaluateException {
          if(myName == null){
            String name = "";
            for (Iterator iterator = optimised.iterator(); iterator.hasNext();) {
              JVMName nameEvaluator = (JVMName) iterator.next();
              name += nameEvaluator.getName(process);
            }
            myName = name;
          }
          return myName;
        }

        public String getDisplayName(DebugProcessImpl debugProcess) {
          if(myName == null) {
            String displayName = "";
            for (Iterator iterator = optimised.iterator(); iterator.hasNext();) {
              JVMName nameEvaluator = (JVMName) iterator.next();
              displayName += nameEvaluator.getDisplayName(debugProcess);
            }
            return displayName;
          }
          return myName;
        }
      };
    }
  }

  private static class JVMRawText implements JVMName {
    private String myText;

    public JVMRawText(String text) {
      myText = text;
    }

    public String getName(DebugProcessImpl process) throws EvaluateException {
      return myText;
    }

    public String getDisplayName(DebugProcessImpl debugProcess) {
      return myText;
    }

    public String getName() {
      return myText;
    }

    public void setName(String name) {
      myText = name;
    }
  }

  private static class JVMClassAt implements JVMName {
    private final SourcePosition mySourcePosition;

    public JVMClassAt(SourcePosition sourcePosition) {
      mySourcePosition = sourcePosition;
    }

    public String getName(DebugProcessImpl process) throws EvaluateException {
      List<ReferenceType> allClasses = process.getPositionManager().getAllClasses(mySourcePosition);
      if(allClasses.size() > 0) {
        return allClasses.get(0).name();
      }

      throw EvaluateExceptionUtil.createEvaluateException("JVM class name is unknown - class is not prepared : " + getDisplayName(process));
    }

    public String getDisplayName(DebugProcessImpl debugProcess) {
      return getClassDisplayName(debugProcess, mySourcePosition);
    }
  }

  public static JVMName getJVMRawText(String qualifiedName) {
    return new JVMRawText(qualifiedName);
  }

  public static JVMName getJVMQualifiedName(PsiType psiType) {
    if(psiType instanceof PsiArrayType) {
      JVMName jvmName = getJVMQualifiedName(((PsiArrayType)psiType).getComponentType());
      JVMNameBuffer buffer = new JVMNameBuffer();
      buffer.append(jvmName);
      buffer.append("[]");
      return buffer.toName();
    }

    PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
    if (psiClass == null) {
      return getJVMRawText(psiType.getCanonicalText());
    } else {
      return getJVMQualifiedName(psiClass);
    }
  }

  public static JVMName getJVMQualifiedName(PsiClass psiClass) {
    if(PsiUtil.isLocalOrAnonymousClass(psiClass)) {
      return new JVMClassAt(SourcePosition.createFromElement(psiClass));
    }
    else {
      return getJVMRawText(getNonAnonymousClassName(psiClass));
    }
  }

  public static String getNonAnonymousClassName(PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if(parentClass != null) {
      return getNonAnonymousClassName(parentClass) + "$" + aClass.getName();
    }
    else {
      return aClass.getQualifiedName();
    }
  }

  public static JVMName getJVMSignature(PsiMethod method) throws EvaluateException {
    JVMNameBuffer signature = new JVMNameBuffer();
    signature.append("(");
    PsiParameterList paramList = method.getParameterList();
    if (paramList != null) {
      PsiParameter[] params = paramList.getParameters();
      for (int idx = 0; idx < params.length; idx++) {
        PsiParameter psiParameter = params[idx];
        appendJVMSignature(signature, psiParameter.getType());
      }
    }
    signature.append(")");
    if (!method.isConstructor()) {
      appendJVMSignature(signature, method.getReturnType());
    }
    else {
      signature.append(new JVMRawText("V"));
    }
    return signature.toName();
  }

  public static PsiClass getClassAt(SourcePosition sourceOffset) {
    PsiElement element = sourceOffset.getFile().findElementAt(sourceOffset.getOffset());
    PsiClass parentClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    return parentClass;
  }

  public static String getClassDisplayName(DebugProcessImpl debugProcess, SourcePosition classAt) {
    PsiClass psiClass = getClassAt(classAt);

    if(psiClass != null && psiClass.getQualifiedName() != null) return psiClass.getQualifiedName();

    if(debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(classAt);
      if(allClasses.size() > 0) {
        return allClasses.get(0).name();
      }
    }
    return "Class at " + classAt.getFile().getName() + ":" + classAt.getLine();
  }

  public static PsiClass getTopLevelParentClass(PsiClass psiClass) {
    PsiClass result = psiClass;
    PsiClass parent = psiClass;
    for(;parent!= null; parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) {
      result = parent;
    }
    return result;
  }

}
