package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;

public class ClsParameterImpl extends ClsElementImpl implements PsiParameter, ClsModifierListOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsParameterImpl");

  private final PsiParameterList myParent;
  private final PsiTypeElement myType;
  private final int myIdx;
  private PsiModifierList myModifierList = null;
  private String myMirrorName = null;
  private String myName = null;
  public static final ClsParameterImpl[] EMPTY_ARRAY = new ClsParameterImpl[0];

  public ClsParameterImpl(PsiParameterList parent, PsiTypeElement type, int idx) {
    myParent = parent;
    myType = type;
    myIdx = idx;
  }

  public PsiElement[] getChildren() {
    return new PsiElement[]{getModifierList(), getTypeElement()};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public String getName() {
    if (myName == null) {
      ClsMethodImpl method = (ClsMethodImpl)getDeclarationScope();
      if (method.getRepositoryId() < 0) return null;
      PsiMethod sourceMethod = (PsiMethod)method.getNavigationElement();
      if (sourceMethod == method) return null;
      myName = sourceMethod.getParameterList().getParameters()[myIdx].getName();
    }
    return myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiTypeElement getTypeElement() {
    return myType;
  }

  public PsiType getType() {
    return myType.getType();
  }

  public PsiModifierList getModifierList() {
    synchronized (PsiLock.LOCK) {
      if (myModifierList == null) {
        myModifierList = new ClsModifierListImpl(this, 0);
      }
      ;
    }
    return myModifierList;
  }

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return false;
  }

  public Object computeConstantValue() {
    return null;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  public String getMirrorText() {
    StringBuffer buffer = new StringBuffer();
    ClsAnnotationImpl[] annotations = getAnnotations();
    for (int i = 0; i < annotations.length; i++) {
      buffer.append(annotations[i].getMirrorText());
      buffer.append(" ");
    }
    buffer.append(((ClsElementImpl)getTypeElement()).getMirrorText());
    buffer.append(" ");
    buffer.append(getMirrorName());
    return buffer.toString();
  }

  private String getMirrorName() {
    synchronized (PsiLock.LOCK) {
      if (myMirrorName == null) {
        String name = getName();
        if (name != null) return name;

        String[] nameSuggestions = getManager().getCodeStyleManager().suggestVariableName(VariableKind.PARAMETER, null,
                                                                                          null, getType())
          .names;
        name = "p";
        if (nameSuggestions.length > 0) {
          name = nameSuggestions[0];
        }

        PsiParameter[] parms = ((PsiParameterList)getParent()).getParameters();
        AttemptsLoop:
        while (true) {
          for (int i = 0; i < parms.length; i++) {
            if (parms[i] == this) break AttemptsLoop;
            String name1 = ((ClsParameterImpl)parms[i]).getMirrorName();
            if (name.equals(name1)) {
              name = nextName(name);
              continue AttemptsLoop;
            }
          }
        }
        myMirrorName = name;
      }
      ;
    }
    return myMirrorName;
  }

  private static String nextName(String name) {
    int count = 0;
    while (true) {
      if (count == name.length()) break;
      char c = name.charAt(name.length() - count - 1);
      if ('0' <= c && c <= '9') {
        count++;
      }
      else {
        break;
      }
    }

    try {
      int n = count > 0 ? Integer.parseInt(name.substring(name.length() - count)) : 0;
      n++;
      return name.substring(0, name.length() - count) + n;
    }
    catch (NumberFormatException e) {
      LOG.assertTrue(false);
      return null;
    }
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiParameter mirror = (PsiParameter)SourceTreeToPsiMap.treeElementToPsi(element);
    ((ClsElementImpl)getModifierList()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
    ((ClsElementImpl)getTypeElement()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getTypeElement()));
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "PsiParameter";
  }

  public PsiElement getDeclarationScope() {
    // only method parameters exist in compiled code
    return getParent().getParent();
  }

  public boolean isVarArgs() {
    if (((PsiMethod)myParent.getParent()).isVarArgs()) {
      return myIdx == myParent.getParameters().length - 1;
    }

    return false;
  }

  public ClsAnnotationImpl[] getAnnotations() {
    return ((ClsMethodImpl)myParent.getParent()).getParameterAnntations(myIdx);
  }
}
