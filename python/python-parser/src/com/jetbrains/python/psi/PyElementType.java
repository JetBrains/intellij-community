// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyPsiElementFactory;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class PyElementType extends IElementType implements PyPsiCreatingElementType {
  private final @NotNull Function<? super ASTNode, ? extends PsiElement> myPsiCreator;

  private final String mySpecialMethodName;

  public PyElementType(@NotNull @NonNls String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = node -> { throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");};
    mySpecialMethodName = null;
  }

  public PyElementType(@NotNull @NonNls String debugName, @NotNull Function<? super ASTNode, ? extends PsiElement> creator) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = creator;
    mySpecialMethodName = null;
  }

  public PyElementType(@NotNull @NonNls String debugName, @NotNull @NonNls String specialMethodName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = node -> { throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");};
    mySpecialMethodName = specialMethodName;
  }

  /**
   * An element type that asks {@link PyPsiElementFactory#current()} for its PSI class each time it creates an element.
   * {@code constructor} picks the creator from the factory, for example {@code PyPsiElementFactory::getArgumentListConstructor}.
   */
  public static @NotNull PyElementType viaFactory(
    @NotNull @NonNls String debugName,
    @NotNull Function<? super PyPsiElementFactory, ? extends Function<? super ASTNode, ? extends PsiElement>> constructor
  ) {
    return new PyElementType(debugName, node -> constructor.apply(PyPsiElementFactory.current()).apply(node));
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return myPsiCreator.apply(node);
  }

  /**
   * @return name of special method for operation marked by this token; e.g. "__add__" for "+".
   */
  public @Nullable String getSpecialMethodName() {
    return mySpecialMethodName;
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }
}
