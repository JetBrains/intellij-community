package com.intellij.psi.impl.source;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class PsiTypeCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiTypeCodeFragment {
  private final boolean myAllowEllipsis;

  public PsiTypeCodeFragmentImpl(Project manager,
                                 boolean isPhysical,
                                 boolean allowEllipsis,
                                 @NonNls String name,
                                 CharSequence text) {
    super(manager, TYPE_TEXT, isPhysical, name, text);
    myAllowEllipsis = allowEllipsis;
  }

  @NotNull
  public PsiType getType()
    throws TypeSyntaxException, NoTypeException {
    PsiType type;
    class SyntaxError extends RuntimeException {}
    try {
      accept(new PsiRecursiveElementVisitor() {
        @Override public void visitErrorElement(PsiErrorElement element) {
          throw new SyntaxError();
        }
      });
    }
    catch(SyntaxError e) {
      throw new TypeSyntaxException();
    }
    PsiElement child = getFirstChild();
    while (child != null && !(child instanceof PsiTypeElement)) {
      child = child.getNextSibling();
    }
    final PsiTypeElement typeElement1 = (PsiTypeElement)child;
    PsiTypeElement typeElement = typeElement1;
    if (typeElement == null) {
      throw new NoTypeException();
    }
    type = typeElement.getType();
    PsiElement sibling = typeElement.getNextSibling();
    while (sibling instanceof PsiWhiteSpace) {
      sibling = sibling.getNextSibling();
    }
    if (sibling instanceof PsiJavaToken && "...".equals(sibling.getText())) {
      if (myAllowEllipsis) return new PsiEllipsisType(type);
      else throw new TypeSyntaxException();
    } else {
      return type;
    }
  }

  public boolean isVoidValid() {
    return getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null ||
           getOriginalFile() != null && getOriginalFile().getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null;
  }
}
