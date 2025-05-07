package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class PySingleStarPatternImpl extends PyElementImpl implements PySingleStarPattern {
  public PySingleStarPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySingleStarPattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    return null;
  }

  @Override
  public @NotNull List<@Nullable PyType> getCapturedTypesFromSequenceType(@Nullable PyType sequenceType, @NotNull TypeEvalContext context) {
    if (getParent() instanceof PySequencePattern sequenceParent) {
      final int idx = sequenceParent.getElements().indexOf(this);
      if (sequenceType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
        return tupleType.getElementTypes().subList(idx, idx + tupleType.getElementCount() - sequenceParent.getElements().size() + 1);
      }
      var upcast = PyTypeUtil.convertToType(sequenceType, "typing.Sequence", this, context);
      if (upcast instanceof PyCollectionType collectionType) {
        return Collections.singletonList(collectionType.getIteratedItemType());
      }
    }
    return List.of();
  }
}
