package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PyAstElement extends NavigatablePsiElement {
  default <T extends PyAstElement> T @NotNull [] childrenToPsi(TokenSet filterSet, T[] array) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    return PyPsiUtilsCore.nodesToPsi(nodes, array);
  }

  @Nullable
  default <T extends PyAstElement> T childToPsi(TokenSet filterSet, int index) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    if (nodes.length <= index) {
      return null;
    }
    //noinspection unchecked
    return (T)nodes[index].getPsi();
  }

  @Nullable
  default <T extends PyAstElement> T childToPsi(IElementType elType) {
    final ASTNode node = getNode().findChildByType(elType);
    if (node == null) {
      return null;
    }

    //noinspection unchecked
    return (T)node.getPsi();
  }

  @Nullable
  default <T extends PyAstElement> T childToPsi(@NotNull TokenSet elTypes) {
    final ASTNode node = getNode().findChildByType(elTypes);
    //noinspection unchecked
    return node != null ? (T)node.getPsi() : null;
  }

  @NotNull
  default <T extends PyAstElement> T childToPsiNotNull(TokenSet filterSet, int index) {
    final PyAstElement child = childToPsi(filterSet, index);
    if (child == null) {
      throw new RuntimeException("child must not be null: expression text " + getText());
    }
    //noinspection unchecked
    return (T)child;
  }

  @NotNull
  default <T extends PyAstElement> T childToPsiNotNull(IElementType elType) {
    final PyAstElement child = childToPsi(elType);
    if (child == null) {
      throw new RuntimeException("child must not be null; expression text " + getText());
    }
    //noinspection unchecked
    return (T)child;
  }

  /**
   * Returns a child of specified type. The default implementation takes it from AST.
   * Implementation classes that also inherit StubBasedPsiElementBase have this method overridden and may take it from stubs if available.
   * See {@link com.intellij.extapi.psi.StubBasedPsiElementBase#getStubOrPsiChild(IStubElementType)}
   */
  default @Nullable <Psi extends PsiElement> Psi getStubOrPsiChild(@NotNull IStubElementType<? extends StubElement, Psi> elementType) {
    final ASTNode childNode = getNode().findChildByType(elementType);
    if (childNode != null) {
      //noinspection unchecked
      return (Psi)childNode.getPsi();
    }
    return null;
  }

  /**
   * Returns a not-null child of specified type. The default implementation takes it from AST.
   * Implementation classes that also inherit StubBasedPsiElementBase have this method overridden and may take it from stubs if available.
   * See {@link com.intellij.extapi.psi.StubBasedPsiElementBase#getRequiredStubOrPsiChild(IStubElementType)}
   */
  default @NotNull <S extends StubElement<?>, Psi extends PsiElement> Psi getRequiredStubOrPsiChild(@NotNull IStubElementType<S, Psi> elementType) {
    Psi result = getStubOrPsiChild(elementType);
    if (result == null) {
      throw new AssertionError("Missing required child of type " + elementType);
    }
    return result;
  }

  default @Nullable <E extends PsiElement> E getStubOrPsiParentOfType(@NotNull Class<E> parentClass) {
    return PsiTreeUtil.getParentOfType(this, parentClass);
  }
}
