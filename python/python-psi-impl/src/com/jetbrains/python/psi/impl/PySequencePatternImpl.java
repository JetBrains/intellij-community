package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.types.PyLiteralType.upcastLiteralToClass;

public class PySequencePatternImpl extends PyElementImpl implements PySequencePattern, PsiListLikeElement {
  public PySequencePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySequencePattern(this);
  }

  @Override
  public @NotNull List<? extends PyPattern> getComponents() {
    return getElements();
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    final PyType sequenceCaptureType = getSequenceCaptureType(this, context);
    boolean isHomogeneous = !(sequenceCaptureType instanceof PyTupleType tupleType) || tupleType.isHomogeneous();
    final ArrayList<PyType> types = new ArrayList<>();
    for (PyPattern pattern : getElements()) {
      if (pattern instanceof PySingleStarPattern starPattern) {
        types.addAll(starPattern.getCapturedTypesFromSequenceType(sequenceCaptureType, context));
      }
      else {
        types.add(context.getType(pattern));
      }
    }
    PyType expectedType = isHomogeneous ? wrapInSequenceType(PyUnionType.union(types), this) : PyTupleType.create(this, types);
    PyType captureType = PyCapturePatternImpl.getCaptureType(this, context);
    if (captureType == null) return expectedType;
    return PyTypeUtil.toStream(captureType)
      .map(it -> {
        if (PyTypeChecker.match(expectedType, it, context)) {
          return it;
        }
        return expectedType;
      })
      .collect(PyTypeUtil.toUnion());
  }

  static @Nullable PyType wrapInListType(@Nullable PyType elementType, @NotNull PsiElement resolveAnchor) {
    final PyClass list = PyBuiltinCache.getInstance(resolveAnchor).getClass("list");
    return list != null ? new PyCollectionTypeImpl(list, false, Collections.singletonList(upcastLiteralToClass(elementType))) : null;
  }

  @Nullable
  public static PyType wrapInSequenceType(@Nullable PyType elementType, @NotNull PsiElement resolveAnchor) {
    final PyClass sequence = PyPsiFacade.getInstance(resolveAnchor.getProject()).createClassByQName("typing.Sequence", resolveAnchor);
    return sequence != null ? new PyCollectionTypeImpl(sequence, false, Collections.singletonList(upcastLiteralToClass(elementType))) : null;
  }

  /**
   * Similar to {@link PyCapturePatternImpl#getCaptureType(PyPattern, TypeEvalContext)},
   * but only chooses types that would match to typing.Sequence, and have correct length
   */
  @Nullable
  static PyType getSequenceCaptureType(@NotNull PySequencePattern pattern, @NotNull TypeEvalContext context) {
    final PyType captureTypes = PyCapturePatternImpl.getCaptureType(pattern, context);
    final boolean hasStar = ContainerUtil.exists(pattern.getElements(), it -> it instanceof PySingleStarPattern);

    List<PyType> types = new ArrayList<>();
    for (PyType captureType : PyTypeUtil.toStream(captureTypes)) {
      if (captureType instanceof PyClassType classType &&
          ArrayUtil.contains(classType.getClassQName(), "str", "bytes", "bytearray")) continue;

      PyType sequenceType = PyTypeUtil.convertToType(captureType, "typing.Sequence", pattern, context);
      if (sequenceType == null) continue;

      if (captureType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
        final List<PyPattern> elements = pattern.getElements();
        if (hasStar && elements.size() <= tupleType.getElementCount() || elements.size() == tupleType.getElementCount()) {
          types.add(captureType);
        }
      }
      else {
        types.add(captureType);
      }
    }
    return PyUnionType.union(types);
  }
}
