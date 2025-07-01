package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.util.PsiTreeUtil;
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
  public boolean canExcludePatternType(@NotNull TypeEvalContext context) {
    return false;
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    ArrayList<PyType> keyTypes = new ArrayList<>();
    ArrayList<PyType> valueTypes = new ArrayList<>();
    for (PyKeyValuePattern it : getComponents()) {
      keyTypes.add(context.getType(it.getKeyPattern()));
      if (it.getValuePattern() != null) {
        valueTypes.add(context.getType(it.getValuePattern()));
      }
    }

    PyType patternMappingType = wrapInMappingType(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes), this);

    PyType captureTypes = PyCaptureContext.getCaptureType(this, context);
    PyType filteredType = PyTypeUtil.toStream(captureTypes).filter(captureType -> {
      var mappingType = PyTypeUtil.convertToType(captureType, "typing.Mapping", this, context);
      if (mappingType == null) return false;
      if (!PyTypeChecker.match(mappingType, patternMappingType, context)) return false;
      return true;
    }).collect(PyTypeUtil.toUnion());

    return filteredType == null ? patternMappingType : filteredType;
  }

  @Override
  public @Nullable PyType getCaptureTypeForChild(@NotNull PyPattern pattern, @NotNull TypeEvalContext context) {
    final var sequenceMember = PsiTreeUtil.findFirstParent(pattern, el -> this == el.getParent());
    if (sequenceMember instanceof PyDoubleStarPattern) {
      var mappingType = PyTypeUtil.convertToType(context.getType(this), "typing.Mapping", pattern, context);
      if (mappingType instanceof PyCollectionType collectionType) {
        final PyClass dict = PyBuiltinCache.getInstance(pattern).getClass("dict");
        return dict != null ? new PyCollectionTypeImpl(dict, false, collectionType.getElementTypes()) : null;
      }
      return null;
    }
    else if (sequenceMember instanceof PyKeyValuePattern keyValuePattern) {
      return PyTypeUtil.toStream(PyCaptureContext.getCaptureType(this, context)).map(type -> {
        if (type instanceof PyTypedDictType typedDictType) {
          if (context.getType(keyValuePattern.getKeyPattern()) instanceof PyLiteralType l &&
              l.getExpression() instanceof PyStringLiteralExpression str) {
            return typedDictType.getElementType(str.getStringValue());
          }
        }

        PyType mappingType = PyTypeUtil.convertToType(type, "typing.Mapping", pattern, context);
        if (mappingType == null) {
          return PyNeverType.NEVER;
        }
        else if (mappingType instanceof PyCollectionType collectionType) {
          return collectionType.getElementTypes().get(1);
        }
        return null;
      }).collect(PyTypeUtil.toUnion());
    }
    return null;
  }

  private static @Nullable PyType wrapInMappingType(@Nullable PyType keyType, @Nullable PyType valueType, @NotNull PsiElement resolveAnchor) {
    keyType = PyLiteralType.upcastLiteralToClass(keyType);
    valueType = PyLiteralType.upcastLiteralToClass(valueType);
    final PyClass sequence = PyPsiFacade.getInstance(resolveAnchor.getProject()).createClassByQName("typing.Mapping", resolveAnchor);
    return sequence != null ? new PyCollectionTypeImpl(sequence, false, Arrays.asList(keyType, valueType)) : null;
  }
}
