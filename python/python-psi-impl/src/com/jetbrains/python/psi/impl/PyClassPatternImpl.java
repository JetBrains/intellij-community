package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTypeProvider;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyUtil.multiResolveTopPriority;

public class PyClassPatternImpl extends PyElementImpl implements PyClassPattern {
  public PyClassPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClassPattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    final PyType type = context.getType(getClassNameReference());
    if (type instanceof PyClassType classType) {
      final PyType instanceType = classType.toInstance();
      final PyType captureType = PyCaptureContext.getCaptureType(this, context);
      return Ref.deref(PyTypeAssertionEvaluator.createAssertionType(captureType, instanceType, true, true, context));
    }
    return null;
  }

  @Override
  public boolean canExcludePatternType(@NotNull TypeEvalContext context) {
    final var refName = getClassNameReference();
    final var resolved = getFirstItem(multiResolveTopPriority(refName.getReference()));
    if (!(resolved instanceof PyClass)) {
      // def foo(clz: type[Clz], x):
      //     match x:
      //         case clz():  # <-- can actually be a subclass of Clz, so doesn't subtract
      return false;
    }

    if (!(context.getType(refName) instanceof PyClassType classType)) return false;

    final List<PyPattern> arguments = getArgumentList().getPatterns();
    if (SPECIAL_BUILTINS.contains(classType.getClassQName())) {
      if (arguments.isEmpty()) return true;
      if (arguments.size() > 1) return false;
      return arguments.get(0).canExcludePatternType(context);
    }

    List<String> matchArgs = getMatchArgs(classType, context);
    for (int i = 0; i < arguments.size(); i++) {
      final var member = arguments.get(i);
      if (member instanceof PyKeywordPattern keywordPattern)  {
        if (getMemberType(classType, keywordPattern.getKeyword(), context) == null) {
          return false;
        }
        final var valuePattern = keywordPattern.getValuePattern();
        if (valuePattern != null && !canExcludeArgumentPatternType(valuePattern, context)) {
          return false;
        }
      }
      else {
        if (matchArgs == null) return false;
        if (i >= matchArgs.size()) return false;
        if (!canExcludeArgumentPatternType(member, context)) {
          return false;
        }
      }
    }
    final PyType captureType = PyCaptureContext.getCaptureType(this, context);
    final PyType patternType = context.getType(this);
    return PyTypeUtil.toStream(captureType).anyMatch(it -> PyTypeChecker.match(patternType, it, context));
  }

  @Override
  public @Nullable PyType getCaptureTypeForChild(@NotNull PyPattern pattern, @NotNull TypeEvalContext context) {
    pattern = as(PsiTreeUtil.findFirstParent(pattern, el -> this.getArgumentList() == el.getParent()), PyPattern.class);
    if (pattern == null) return null;
    
    if (pattern instanceof PyKeywordPattern keywordPattern) {
      if (context.getType(this) instanceof PyClassType classType) {
        return Ref.deref(getMemberType(classType, keywordPattern.getKeyword(), context));
      }
      return null;
    }
    
    final List<PyPattern> arguments = getArgumentList().getPatterns();
    int index = arguments.indexOf(pattern);
    if (index < 0) return null;

    // capture type can be a union like: list[int] | list[str]
    return PyTypeUtil.toStream(context.getType(this)).map(type -> {
      if (type instanceof PyClassType classType) {
        if (SPECIAL_BUILTINS.contains(classType.getClassQName())) {
          if (index == 0) {
            return classType;
          }
          return null;
        }

        List<String> matchArgs = getMatchArgs(classType, context);
        if (matchArgs == null || index >= matchArgs.size()) return null;

        final Ref<PyType> memberType = getMemberType(classType, matchArgs.get(index), context);
        if (memberType == null) return null;

        return PyTypeChecker.substitute(memberType.get(), PyTypeChecker.unifyReceiver(classType, context), context);
      }
      return null;
    }).collect(PyTypeUtil.toUnion());
  }

  static boolean canExcludeArgumentPatternType(@NotNull PyPattern pattern, @NotNull TypeEvalContext context) {
    final var captureType = PyCaptureContext.getCaptureType(pattern, context);
    final var patternType = context.getType(pattern);
    // For class pattern arguments, we need to ensure that the argument pattern covers its capture type fully
    if (Ref.deref(PyTypeAssertionEvaluator.createAssertionType(captureType, patternType, false, true, context)) instanceof PyNeverType) {
      // in case the argument pattern is also class pattern with arguments
      return pattern.canExcludePatternType(context);
    }
    return false;
  }

  private static @Nullable List<@NotNull String> getMatchArgs(@NotNull PyClassType type, @NotNull TypeEvalContext context) {
    final PyClass cls = type.getPyClass();
    List<String> matchArgs = cls.getOwnMatchArgs();
    if (matchArgs == null) {
      matchArgs = PyNamedTupleTypeProvider.Companion.getGeneratedMatchArgs(type, context);
    }
    if (matchArgs == null) {
      matchArgs = PyDataclassTypeProvider.Companion.getGeneratedMatchArgs(cls, context);
    }
    
    return matchArgs;
  }

  @Nullable
  static Ref<PyType> getMemberType(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<PyTypedResolveResult> results = type.getMemberTypes(name, null, AccessDirection.READ, resolveContext);
    return ContainerUtil.isEmpty(results) ? null : Ref.create(getFirstItem(results).getType());
  }
}
