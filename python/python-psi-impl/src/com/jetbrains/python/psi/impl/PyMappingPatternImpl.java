package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PyMappingPatternImpl extends PyElementImpl implements PyMappingPattern, PsiListLikeElement {
  public PyMappingPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyMappingPattern(this);
  }

  @Override
  public @NotNull List<? extends PyKeyValuePattern> getComponents() {
    return Arrays.asList(findChildrenByClass(PyKeyValuePattern.class));
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    ArrayList<PyType> keyTypes = new ArrayList<>();
    ArrayList<PyType> valueTypes = new ArrayList<>();
    for (PyKeyValuePattern it : getComponents()) {
      PyType type = context.getType(it);
      if (type instanceof PyTupleType tupleType) {
        keyTypes.add(tupleType.getElementType(0));
        valueTypes.add(tupleType.getElementType(1));
      }
    }
    //keyTypes.add(null);
    //valueTypes.add(null);
    return wrapInMappingType(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes), this);
  }

  private static @Nullable PyType wrapInMappingType(@Nullable PyType keyType, @Nullable PyType valueType, @NotNull PsiElement resolveAnchor) {
    keyType = PyLiteralType.upcastLiteralToClass(keyType);
    valueType = PyLiteralType.upcastLiteralToClass(valueType);
    final PyClass sequence = PyPsiFacade.getInstance(resolveAnchor.getProject()).createClassByQName("typing.Mapping", resolveAnchor);
    return sequence != null ? new PyCollectionTypeImpl(sequence, false, Arrays.asList(keyType, valueType)) : null;
  }
}
