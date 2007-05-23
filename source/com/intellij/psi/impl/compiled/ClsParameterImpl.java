package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
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

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiTypeElement getTypeElement() {
    return myType;
  }

  @NotNull
  public PsiType getType() {
    return myType.getType();
  }

  public PsiModifierList getModifierList() {
    synchronized (PsiLock.LOCK) {
      if (myModifierList == null) {
        myModifierList = new ClsModifierListImpl(this, 0);
      }
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

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    ClsAnnotationImpl[] annotations = getAnnotations();
    for (ClsAnnotationImpl annotation : annotations) {
      annotation.appendMirrorText(indentLevel, buffer);
      buffer.append(" ");
    }
    ((ClsElementImpl)getTypeElement()).appendMirrorText(indentLevel, buffer);
    buffer.append(" ");
    buffer.append(getMirrorName());
  }

  private String getMirrorName() {
    synchronized (PsiLock.LOCK) {
      if (myMirrorName == null) {
        @NonNls String name = getName();
        if (name != null) return name;

        String[] nameSuggestions = getManager().getCodeStyleManager().suggestVariableName(VariableKind.PARAMETER, null,
            null, getType())
            .names;
        name = "p";
        if (nameSuggestions.length > 0) {
          name = nameSuggestions[0];
        }

        PsiParameter[] parms = ((PsiParameterList) getParent()).getParameters();
        AttemptsLoop:
        while (true) {
          for (PsiParameter parm : parms) {
            if (parm == this) break AttemptsLoop;
            String name1 = ((ClsParameterImpl) parm).getMirrorName();
            if (name.equals(name1)) {
              name = nextName(name);
              continue AttemptsLoop;
            }
          }
        }
        myMirrorName = name;
      }
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
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
      ((ClsElementImpl)getTypeElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeElement()));
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "PsiParameter";
  }

  @NotNull
  public PsiElement getDeclarationScope() {
    // only method parameters exist in compiled code
    return getParent().getParent();
  }

  public boolean isVarArgs() {
    return ((PsiMethod) myParent.getParent()).isVarArgs() && myIdx == myParent.getParametersCount() - 1;
  }

  @NotNull
  public ClsAnnotationImpl[] getAnnotations() {
    return ((ClsMethodImpl)myParent.getParent()).getParameterAnnotations(myIdx);
  }
}
