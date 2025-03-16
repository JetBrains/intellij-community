package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.jetbrains.python.psi.PyUtil.multiResolveTopPriority;
import static com.jetbrains.python.psi.impl.PyCapturePatternImpl.*;

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
      final PyType captureType = PyCapturePatternImpl.getCaptureType(this, context);
      return Ref.deref(PyTypeAssertionEvaluator.createAssertionType(captureType, instanceType, true, context));
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
        if (resolveTypeMember(classType, keywordPattern.getKeyword(), context) == null) {
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
    final PyType captureType = getCaptureType(this, context);
    final PyType patternType = context.getType(this);
    return PyTypeUtil.toStream(captureType).anyMatch(it -> PyTypeChecker.match(patternType, it, context));
  }
  
  static boolean canExcludeArgumentPatternType(@NotNull PyPattern pattern, @NotNull TypeEvalContext context) {
    final var captureType = getCaptureType(pattern, context);
    final var patternType = context.getType(pattern);
    // For class pattern arguments, we need to ensure that the argument pattern covers its capture type fully
    if (Ref.deref(PyTypeAssertionEvaluator.createAssertionType(captureType, patternType, false, context)) instanceof PyNeverType) {
      // in case the argument pattern is also class pattern with arguments
      return pattern.canExcludePatternType(context);
    }
    return false;
  }
}
