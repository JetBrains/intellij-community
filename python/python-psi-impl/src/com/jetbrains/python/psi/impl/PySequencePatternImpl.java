package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.impl.PyClassPatternImpl.canExcludeArgumentPatternType;
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
        types.addAll(getCapturedTypesFromSequenceType(starPattern, sequenceCaptureType, context));
      }
      else {
        types.add(context.getType(pattern));
      }
    }
    PyType expectedType = isHomogeneous ? wrapInSequenceType(PyUnionType.union(types), this) : PyTupleType.create(this, types);
    if (sequenceCaptureType == null) return expectedType;
    return PyTypeUtil.toStream(sequenceCaptureType)
      .map(it -> {
        if (PyTypeChecker.match(expectedType, it, context)) {
          return it;
        }
        return expectedType;
      })
      .collect(PyTypeUtil.toUnion());
  }
  
  @Override
  public boolean canExcludePatternType(@NotNull TypeEvalContext context) {
    for (var p : getElements()) {
      if (!canExcludeArgumentPatternType(p, context)) {
        return false;
      }
    }
    for (var type : PyTypeUtil.toStream(getSequenceCaptureType(this, context))) {
      if (!(type instanceof PyTupleType tupleType) || tupleType.isHomogeneous()) {
        // Not clear how to do this accurately, so for now matching 
        // types like list[Something] will not exclude type in any case
        return false;
      }
    }
    return true;
  }

  @Override
  public @Nullable PyType getCaptureTypeForChild(@NotNull PyPattern pattern, @NotNull TypeEvalContext context) {
    final PyType sequenceType = getSequenceCaptureType(this, context);
    if (sequenceType == null) return null;

    // This is done to skip group- and as-patterns
    final var sequenceMember = PsiTreeUtil.findFirstParent(pattern, el -> this == el.getParent());
    if (sequenceMember instanceof PySingleStarPattern starPattern) {
      final PyType iteratedType = PyTypeUtil.toStream(sequenceType)
        .flatMap(it -> getCapturedTypesFromSequenceType(starPattern, it, context).stream()).collect(PyTypeUtil.toUnion());
      return wrapInListType(iteratedType, pattern);
    }
    final List<PyPattern> elements = getElements();
    final int idx = elements.indexOf(sequenceMember);
    final int starIdx = ContainerUtil.indexOf(elements, it2 -> it2 instanceof PySingleStarPattern);

    return PyTypeUtil.toStream(sequenceType).map(it -> {
      if (it instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
        if (starIdx == -1 || idx < starIdx) {
          return tupleType.getElementType(idx);
        }
        else {
          final int starSpan = tupleType.getElementCount() - elements.size();
          return tupleType.getElementType(idx + starSpan);
        }
      }
      var upcast = PyTypeUtil.convertToType(it, "typing.Sequence", pattern, context);
      if (upcast instanceof PyCollectionType collectionType) {
        return collectionType.getIteratedItemType();
      }
      return null;
    }).collect(PyTypeUtil.toUnion());
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
   * Similar to {@link PyCaptureContext#getCaptureType(PyPattern, TypeEvalContext)},
   * but only chooses types that would match to typing.Sequence, and have correct length
   */
  @Nullable
  static PyType getSequenceCaptureType(@NotNull PySequencePattern pattern, @NotNull TypeEvalContext context) {
    final PyType captureTypes = PyCaptureContext.getCaptureType(pattern, context);
    final boolean hasStar = ContainerUtil.exists(pattern.getElements(), it -> it instanceof PySingleStarPattern);

    List<PyType> types = new ArrayList<>();
    for (PyType captureType : PyTypeUtil.toStream(captureTypes)) {
      if (captureType instanceof PyClassType classType &&
          ArrayUtil.contains(classType.getClassQName(), "str", "bytes", "bytearray")) continue;

      PyType sequenceType = PyTypeUtil.convertToType(captureType, "typing.Sequence", pattern, context);
      if (sequenceType == null) continue;

      if (captureType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
        final List<PyPattern> elements = pattern.getElements();
        List<PyType> tupleElementTypes = tupleType.getElementTypes();
        int unpackedTupleIndex = ContainerUtil.indexOf(tupleElementTypes, it -> it instanceof PyUnpackedTupleType);
        if (unpackedTupleIndex != -1) {
          PyUnpackedTupleType unpackedTupleType = (PyUnpackedTupleType)tupleElementTypes.get(unpackedTupleIndex);
          assert unpackedTupleType.isUnbound();
          int variadicElementsCount = elements.size() - tupleElementTypes.size() + 1;
          if (variadicElementsCount >= 0) {
            List<PyType> adjustedTupleElementTypes = new ArrayList<>(elements.size());
            adjustedTupleElementTypes.addAll(tupleElementTypes.subList(0, unpackedTupleIndex));
            for (int i = 0; i < variadicElementsCount; i++) {
              adjustedTupleElementTypes.add(unpackedTupleType.getElementTypes().get(0));
            }
            adjustedTupleElementTypes.addAll(tupleElementTypes.subList(unpackedTupleIndex + 1, tupleElementTypes.size()));
            types.add(new PyTupleType(tupleType.getPyClass(), adjustedTupleElementTypes, false));
          }
        } else {
          if (hasStar && elements.size() <= tupleType.getElementCount() || elements.size() == tupleType.getElementCount()) {
            types.add(captureType);
          }
        }
      }
      else {
        types.add(captureType);
      }
    }
    return PyUnionType.union(types);
  }

  private static @NotNull List<@Nullable PyType> getCapturedTypesFromSequenceType(@NotNull PySingleStarPattern starPattern,
                                                                                  @Nullable PyType sequenceType,
                                                                                  @NotNull TypeEvalContext context) {
    if (starPattern.getParent() instanceof PySequencePattern sequenceParent) {
      final int idx = sequenceParent.getElements().indexOf(starPattern);
      if (sequenceType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
        return tupleType.getElementTypes().subList(idx, idx + tupleType.getElementCount() - sequenceParent.getElements().size() + 1);
      }
      var upcast = PyTypeUtil.convertToType(sequenceType, "typing.Sequence", starPattern, context);
      if (upcast instanceof PyCollectionType collectionType) {
        return Collections.singletonList(collectionType.getIteratedItemType());
      }
    }
    return List.of();
  }
}
