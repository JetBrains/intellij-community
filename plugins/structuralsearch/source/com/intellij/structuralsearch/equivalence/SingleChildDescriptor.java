package com.intellij.structuralsearch.equivalence;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class SingleChildDescriptor {
  private final MyType myType;
  private final PsiElement myElement;
  private final EquivalenceDescriptor myParentDescriptor;

  public SingleChildDescriptor(@NotNull MyType type, @Nullable PsiElement element, @NotNull EquivalenceDescriptor parentDescriptor) {
    myType = type;
    myElement = element;
    myParentDescriptor = parentDescriptor;
  }

  @NotNull
  public MyType getType() {
    return myType;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  @Nullable
  public ChildRole getRole() {
    return myParentDescriptor.getRole(myElement);
  }

  public static enum MyType {
    DEFAULT,
    OPTIONALLY,
    OPTIONALLY_IN_PATTERN,
    CHILDREN,
    CHILDREN_OPTIONALLY,
    CHILDREN_OPTIONALLY_IN_PATTERN,
    CHILDREN_IN_ANY_ORDER
  }
}
