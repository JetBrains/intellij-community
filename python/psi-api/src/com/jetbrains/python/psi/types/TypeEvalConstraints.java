package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * A pack of constraints that limit behavior of {@link com.jetbrains.python.psi.types.TypeEvalContext}.
 * Any two  {@link com.jetbrains.python.psi.types.TypeEvalContext}s may share their cache if their constraints are equal and no PSI changes
 * happened between their creation.
 * <p/>
 * This class created to support hash/equals for context.
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings("PackageVisibleField")
  // This is an utility class to be used only in package. Fields are open to type less code
class TypeEvalConstraints {
  final boolean myAllowDataFlow;
  final boolean myAllowStubToAST;
  @Nullable final PsiFile myOrigin;

  /**
   * @see com.jetbrains.python.psi.types.TypeEvalContext
   */
  TypeEvalConstraints(final boolean allowDataFlow, final boolean allowStubToAST, @Nullable final PsiFile origin) {
    myAllowDataFlow = allowDataFlow;
    myAllowStubToAST = allowStubToAST;
    myOrigin = origin;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeEvalConstraints)) return false;

    TypeEvalConstraints that = (TypeEvalConstraints)o;

    if (myAllowDataFlow != that.myAllowDataFlow) return false;
    if (myAllowStubToAST != that.myAllowStubToAST) return false;
    if (myOrigin != null ? !myOrigin.equals(that.myOrigin) : that.myOrigin != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAllowDataFlow ? 1 : 0);
    result = 31 * result + (myAllowStubToAST ? 1 : 0);
    result = 31 * result + (myOrigin != null ? myOrigin.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("TypeEvalConstraints(%b, %b, %s)", myAllowDataFlow, myAllowStubToAST, myOrigin);
  }
}
