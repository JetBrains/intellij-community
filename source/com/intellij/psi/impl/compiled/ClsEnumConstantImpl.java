package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsEnumConstantImpl extends ClsFieldImpl implements PsiEnumConstant {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsEnumConstantImpl");

  public ClsEnumConstantImpl(ClsClassImpl parent, int startOffset) {
    super(parent, startOffset);
  }

  public ClsEnumConstantImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }

    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    buffer.append(' ');
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiField mirror = (PsiField)SourceTreeToPsiMap.treeElementToPsi(element);
    if (getDocComment() != null) {
        ((ClsElementImpl)getDocComment()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
  }

  public PsiExpressionList getArgumentList() {
    return null;
  }

  public PsiMethod resolveMethod() {
    return null;
  }

  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  public PsiEnumConstantInitializer getInitializingClass() {
    return null;
  }

  public PsiMethod resolveConstructor() {
    return null;
  }


  @NotNull
  public PsiType getType() {
    return getManager().getElementFactory().createType(getContainingClass());
  }

  public PsiTypeElement getTypeElement() {
    return null;
  }

  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return true;
  }

  public boolean hasModifierProperty(String name) {
    return (PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name));
  }
}
