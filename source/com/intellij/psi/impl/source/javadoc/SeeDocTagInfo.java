package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
class SeeDocTagInfo implements JavadocTagInfo {
  private String myName;
  private boolean myInline;

  public SeeDocTagInfo(String name, boolean isInline) {
    myName = name;
    myInline = isInline;
  }

  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  public String getName() {
    return myName;
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    if (place instanceof PsiDocToken) {
      PsiDocToken token = (PsiDocToken) place;
      if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        return getPossibleMethodsAndFields(context, place, prefix);
      } else if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
        if (token.getPrevSibling() == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
        final String methodName = token.getPrevSibling().getText();

        PsiElement targetContext = getTargetContext(context, place);

        List result = new ArrayList();
        final PsiMethod[] methods = PsiDocMethodOrFieldRef.getAllMethods(targetContext, place);
        for (int i = 0; i < methods.length; i++) {
          final PsiMethod method = methods[i];
          if (method.getName().equals(methodName)) {
            result.add(method);
          }
        }
        return result.toArray(new Object[result.size()]);
      } else if (token.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN && place.getParent() instanceof PsiDocMethodOrFieldRef) {
        return getPossibleMethodsAndFields(context, place, prefix);
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] getPossibleMethodsAndFields(PsiElement context, PsiElement place, String prefix) {
    List result = new ArrayList();

    PsiElement targetContext = getTargetContext(context, place);

    final PsiMethod[] methods = PsiDocMethodOrFieldRef.getAllMethods(targetContext, place);
    for (int i = 0; i < methods.length; i++) {
      result.add(methods[i]);
    }

    final PsiVariable[] variables = PsiDocMethodOrFieldRef.getAllVariables(targetContext, place);
    for (int i = 0; i < variables.length; i++) {
      result.add(variables[i]);
    }

    return result.toArray(new Object[result.size()]);
  }

  private PsiElement getTargetContext(PsiElement context, PsiElement place) {
    PsiElement targetContext = context;

    if (place.getParent() instanceof PsiDocMethodOrFieldRef) {
      PsiDocMethodOrFieldRef methodRef = (PsiDocMethodOrFieldRef) place.getParent();

      final IElementType firstChildType = methodRef.firstChild.getElementType();
      if (firstChildType == ElementType.JAVA_CODE_REFERENCE || firstChildType == ElementType.REFERENCE_EXPRESSION) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) SourceTreeToPsiMap.treeElementToPsi(methodRef.firstChild);
        final PsiElement element = referenceElement.resolve();
        if (element instanceof PsiClass) {
          targetContext = element.getFirstChild();
        }
      }
    }
    return targetContext;
  }

  public boolean isValidInContext(PsiElement element) {
    if (myInline && myName.equals("linkplain"))
      return element.getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_4) >= 0;

    return true;
  }

  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }

  public boolean isInline() {
    return myInline;
  }
}
