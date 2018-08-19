// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.override;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author Alexey.Ivanov
 */
public class PyOverrideImplementUtil {

  @Nullable
  public static PyClass getContextClass(@NotNull final Editor editor, @NotNull final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      // are we in whitespace after last class? PY-440
      final PsiElement lastChild = file.getLastChild();
      if (lastChild != null &&
          offset >= lastChild.getTextRange().getStartOffset() &&
          offset <= lastChild.getTextRange().getEndOffset()) {
        element = lastChild;
      }
    }
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (pyClass == null && element instanceof PsiWhiteSpace && element.getPrevSibling() instanceof PyClass) {
      return (PyClass)element.getPrevSibling();
    }
    return pyClass;
  }

  public static void chooseAndOverrideMethods(@NotNull Project project,
                                              @NotNull Editor editor,
                                              @NotNull PyClass cls,
                                              @NotNull TypeEvalContext context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

    PyPsiUtils.assertValid(cls);
    ApplicationManager.getApplication().assertReadAccessAllowed();

    chooseAndOverrideOrImplementMethods(project, editor, cls, getAllSuperMethods(cls, context), false);
  }

  public static void chooseAndImplementMethods(@NotNull Project project,
                                               @NotNull Editor editor,
                                               @NotNull PyClass cls,
                                               @NotNull TypeEvalContext context) {
    chooseAndImplementMethods(project, editor, cls, getAllSuperAbstractMethods(cls, context));
  }

  public static void chooseAndImplementMethods(@NotNull Project project,
                                               @NotNull Editor editor,
                                               @NotNull PyClass cls,
                                               @NotNull Collection<PyFunction> methods) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

    PyPsiUtils.assertValid(cls);
    ApplicationManager.getApplication().assertReadAccessAllowed();

    chooseAndOverrideOrImplementMethods(project, editor, cls, methods, true);
  }

  private static void chooseAndOverrideOrImplementMethods(@NotNull Project project,
                                                          @NotNull Editor editor,
                                                          @NotNull PyClass cls,
                                                          @NotNull Collection<PyFunction> methods,
                                                          boolean implement) {
    final List<PyMethodMember> elements = new ArrayList<>();
    for (PyFunction method : methods) {
      final String name = method.getName();
      if (name == null || PyUtil.isClassPrivateName(name)) {
        continue;
      }
      if (cls.findMethodByName(name, false, null) == null) {
        final PyMethodMember member = new PyMethodMember(method);
        elements.add(member);
      }
    }
    if (elements.isEmpty()) {
      return;
    }

    final MemberChooser<PyMethodMember> chooser = new MemberChooser<>(elements.toArray(new PyMethodMember[0]), false, true, project);
    chooser.setTitle(implement ? "Select Methods to Implement" : "Select Methods to Override");
    chooser.setCopyJavadocVisible(false);
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    overrideMethods(editor, cls, chooser.getSelectedElements(), implement);
  }

  public static void overrideMethods(final Editor editor, final PyClass pyClass, final List<PyMethodMember> membersToOverride,
                                     final boolean implement) {
    if (membersToOverride == null) {
      return;
    }
    WriteCommandAction
      .writeCommandAction(pyClass.getProject(), pyClass.getContainingFile())
      .run(() -> write(pyClass, membersToOverride, editor, implement));
  }

  private static void write(@NotNull PyClass pyClass, @NotNull List<PyMethodMember> newMembers, @NotNull Editor editor, boolean implement) {
    final PyStatementList statementList = pyClass.getStatementList();
    final int offset = editor.getCaretModel().getOffset();
    PsiElement anchor = null;
    for (PyStatement statement : statementList.getStatements()) {
      if (statement.getTextRange().getStartOffset() < offset ||
          (statement instanceof PyExpressionStatement &&
           ((PyExpressionStatement)statement).getExpression() instanceof PyStringLiteralExpression)) {
        anchor = statement;
      }
    }

    PyFunction element = null;
    for (PyMethodMember newMember : Lists.reverse(newMembers)) {
      element = writeMember(pyClass, (PyFunction)newMember.getPsiElement(), anchor, implement);
    }

    PyPsiUtils.removeRedundantPass(statementList);
    if (element != null) {
      final PyStatementList targetStatementList = element.getStatementList();
      final int start = targetStatementList.getTextRange().getStartOffset();
      editor.getCaretModel().moveToOffset(start);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(start, element.getTextRange().getEndOffset());
    }
  }

  @Nullable
  private static PyFunction writeMember(@NotNull PyClass cls,
                                        @NotNull PyFunction baseFunction,
                                        @Nullable PsiElement anchor,
                                        boolean implement) {
    final PyStatementList statementList = cls.getStatementList();
    final TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());

    final PyFunction function = buildOverriddenFunction(cls, baseFunction, implement).addFunctionAfter(statementList, anchor);
    addImports(baseFunction, function, context);

    PyiUtil
      .getOverloads(baseFunction, context)
      .forEach(
        baseOverload -> {
          final PyFunction overload = (PyFunction)statementList.addBefore(baseOverload, function);
          addImports(baseOverload, overload, context);
        }
      );

    return CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
  }

  private static PyFunctionBuilder buildOverriddenFunction(PyClass pyClass,
                                                           PyFunction baseFunction,
                                                           boolean implement) {
    final boolean overridingNew = PyNames.NEW.equals(baseFunction.getName());
    assert baseFunction.getName() != null;
    PyFunctionBuilder pyFunctionBuilder = new PyFunctionBuilder(baseFunction.getName(), baseFunction);
    final PyDecoratorList decorators = baseFunction.getDecoratorList();
    boolean baseMethodIsStatic = false;
    if (decorators != null) {
      if (decorators.findDecorator(PyNames.CLASSMETHOD) != null) {
        pyFunctionBuilder.decorate(PyNames.CLASSMETHOD);
      }
      else if (decorators.findDecorator(PyNames.STATICMETHOD) != null) {
        baseMethodIsStatic = true;
        pyFunctionBuilder.decorate(PyNames.STATICMETHOD);
      }
      else if (decorators.findDecorator(PyNames.PROPERTY) != null ||
        decorators.findDecorator(PyNames.ABSTRACTPROPERTY) != null) {
        pyFunctionBuilder.decorate(PyNames.PROPERTY);
      }
    }
    final LanguageLevel level = LanguageLevel.forElement(pyClass);
    PyAnnotation anno = baseFunction.getAnnotation();
    if (anno != null && !level.isPython2()) {
      pyFunctionBuilder.annotation(anno.getText());
    }
    if (baseFunction.isAsync()) {
      pyFunctionBuilder.makeAsync();
    }
    final TypeEvalContext context = TypeEvalContext.userInitiated(baseFunction.getProject(), baseFunction.getContainingFile());
    final List<PyCallableParameter> baseParams = baseFunction.getParameters(context);
    for (PyCallableParameter parameter : baseParams) {
      final PyParameter psi = parameter.getParameter();
      final PyNamedParameter namedParameter = PyUtil.as(psi, PyNamedParameter.class);

      if (namedParameter != null) {
        final StringBuilder parameterBuilder = new StringBuilder();
        if (namedParameter.isPositionalContainer()) {
          parameterBuilder.append("*");
        }
        else if (namedParameter.isKeywordContainer()) {
          parameterBuilder.append("**");
        }
        parameterBuilder.append(namedParameter.getName());
        final PyAnnotation annotation = namedParameter.getAnnotation();
        if (annotation != null && !level.isPython2()) {
          parameterBuilder.append(annotation.getText());
        }
        final PyExpression defaultValue = namedParameter.getDefaultValue();
        if (defaultValue != null) {
          parameterBuilder.append("=");
          parameterBuilder.append(defaultValue.getText());
        }
        pyFunctionBuilder.parameter(parameterBuilder.toString());
      }
      else if (psi != null) {
        pyFunctionBuilder.parameter(psi.getText());
      }
    }

    PyClass baseClass = baseFunction.getContainingClass();
    assert baseClass != null;
    StringBuilder statementBody = new StringBuilder();

    boolean hadStar = false;
    List<String> parameters = new ArrayList<>();
    for (PyCallableParameter parameter : baseParams) {
      final PyParameter psi = parameter.getParameter();
      final PyNamedParameter namedParameter = PyUtil.as(psi, PyNamedParameter.class);

      if (namedParameter != null) {
        final String repr = namedParameter.getRepr(false);
        parameters.add(hadStar && !namedParameter.isKeywordContainer() ? namedParameter.getName() + "=" + repr : repr);
        if (namedParameter.isPositionalContainer()) {
          hadStar = true;
        }
      }
      else if (psi instanceof PySingleStarParameter) {
        hadStar = true;
      }
      else if (psi != null) {
        parameters.add(psi.getText());
      }
    }

    if (implement ||
        PyNames.TYPES_INSTANCE_TYPE.equals(baseClass.getQualifiedName()) ||
        baseFunction.onlyRaisesNotImplementedError() ||
        PyKnownDecoratorUtil.hasAbstractDecorator(baseFunction, context)) {
      statementBody.append(PyNames.PASS);
    }
    else {
      if (!PyNames.INIT.equals(baseFunction.getName()) && context.getReturnType(baseFunction) != PyNoneType.INSTANCE || overridingNew) {
        statementBody.append("return ");
      }
      if (baseFunction.isAsync()) {
        statementBody.append(PyNames.AWAIT);
        statementBody.append(" ");
      }
      if (baseClass.isNewStyleClass(context)) {
        statementBody.append(PyNames.SUPER);
        statementBody.append("(");
        final LanguageLevel langLevel = ((PyFile)pyClass.getContainingFile()).getLanguageLevel();
        if (langLevel.isPython2()) {
          final String baseFirstName = !baseParams.isEmpty() ? baseParams.get(0).getName() : null;
          final String firstName = baseFirstName != null ? baseFirstName : PyNames.CANONICAL_SELF;
          PyClass outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass.class, true, PyFunction.class);
          String className = pyClass.getName();
          final List<String> nameResult = Lists.newArrayList(className);
          while (outerClass != null) {
            nameResult.add(0, outerClass.getName());
            outerClass = PsiTreeUtil.getParentOfType(outerClass, PyClass.class, true, PyFunction.class);
          }

          StringUtil.join(nameResult, ".", statementBody);
          statementBody.append(", ").append(firstName);
        }
        statementBody.append(").").append(baseFunction.getName()).append("(");
        // type.__new__ is explicitly decorated as @staticmethod in our stubs, but not in real Python code
        if (parameters.size() > 0 && !(baseMethodIsStatic || overridingNew)) {
          parameters.remove(0);
        }
      }
      else {
        statementBody.append(getReferenceText(pyClass, baseClass)).append(".").append(baseFunction.getName()).append("(");
      }
      StringUtil.join(parameters, ", ", statementBody);
      statementBody.append(")");
    }

    pyFunctionBuilder.statement(statementBody.toString());
    return pyFunctionBuilder;
  }

  // TODO find a better place for this logic
  private static String getReferenceText(PyClass fromClass, PyClass toClass) {
    final PyExpression[] superClassExpressions = fromClass.getSuperClassExpressions();
    for (PyExpression expression : superClassExpressions) {
      if (expression instanceof PyReferenceExpression) {
        PsiElement target = ((PyReferenceExpression)expression).getReference().resolve();
        if (target == toClass) {
          return expression.getText();
        }
      }
    }
    return toClass.getName();
  }

  @NotNull
  public static List<PyFunction> getAllSuperAbstractMethods(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return ContainerUtil.filter(getAllSuperMethods(cls, context), method -> isAbstractMethodForClass(method, cls, context));
  }

  private static boolean isAbstractMethodForClass(@NotNull PyFunction method, @NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final String methodName = method.getName();
    if (methodName == null ||
        cls.findMethodByName(methodName, false, context) != null ||
        cls.findClassAttribute(methodName, false, context) != null) {
      return false;
    }
    final PyClass methodClass = method.getContainingClass();
    if (methodClass != null) {
      for (PyClass ancestor : cls.getAncestorClasses(context)) {
        if (ancestor.equals(methodClass)) break;
        if (ancestor.findClassAttribute(methodName, false, context) != null) return false;
      }
    }
    return method.onlyRaisesNotImplementedError() || PyKnownDecoratorUtil.hasAbstractDecorator(method, context);
  }

  /**
   * Returns all super functions available through MRO.
   */
  @NotNull
  public static List<PyFunction> getAllSuperMethods(@NotNull PyClass pyClass, @NotNull TypeEvalContext context) {
    final Map<String, PyFunction> functions = Maps.newLinkedHashMap();
    for (final PyClassLikeType type : pyClass.getAncestorTypes(context)) {
      if (type != null) {
        for (PyFunction function : PyTypeUtil.getMembersOfType(type, PyFunction.class, false, context)) {
          final String name = function.getName();
          if (name != null) {
            if (!functions.containsKey(name) || PyiUtil.isOverload(functions.get(name), context) && !PyiUtil.isOverload(function, context)) {
              functions.put(name, function);
            }
          }
        }
      }
    }
    return Lists.newArrayList(functions.values());
  }

  /**
   * Adds imports for type hints and decorators in overridden function.
   *
   * @param baseFunction base function used to resolve types
   * @param function overridden function
   */
  private static void addImports(@NotNull PyFunction baseFunction, @NotNull PyFunction function, @NotNull TypeEvalContext context) {
    final UnresolvedExpressionVisitor unresolvedExpressionVisitor = new UnresolvedExpressionVisitor();
    getAnnotations(function, context).forEach(annotation -> annotation.accept(unresolvedExpressionVisitor));
    getDecorators(function).forEach(decorator -> decorator.accept(unresolvedExpressionVisitor));

    final ResolveExpressionVisitor resolveExpressionVisitor = new ResolveExpressionVisitor(unresolvedExpressionVisitor.getUnresolved());
    getAnnotations(baseFunction, context).forEach(annotation -> annotation.accept(resolveExpressionVisitor));
    getDecorators(baseFunction).forEach(decorator -> decorator.accept(resolveExpressionVisitor));
  }

  /**
   * Collect annotations from function parameters and return.
   *
   * @param function
   * @param typeEvalContext
   * @return
   */
  @NotNull
  private static List<PyAnnotation> getAnnotations(@NotNull PyFunction function, @NotNull TypeEvalContext typeEvalContext) {
    return StreamEx.of(function.getParameters(typeEvalContext))
        .map(PyCallableParameter::getParameter)
        .select(PyNamedParameter.class)
        .remove(PyParameter::isSelf)
        .map(PyAnnotationOwner::getAnnotation)
        .append(function.getAnnotation())
        .nonNull()
        .toList();
  }

  @NotNull
  private static List<PyDecorator> getDecorators(@NotNull PyFunction function) {
    final PyDecoratorList decoratorList = function.getDecoratorList();
    return decoratorList == null ? Collections.emptyList() : Arrays.asList(decoratorList.getDecorators());
  }

  /**
   * Collects unresolved {@link PyReferenceExpression} objects.
   */
  private static class UnresolvedExpressionVisitor extends PyRecursiveElementVisitor {

    private final List<PyReferenceExpression> myUnresolved = new ArrayList<>();

    @Override
    public void visitPyReferenceExpression(final PyReferenceExpression referenceExpression) {
      super.visitPyReferenceExpression(referenceExpression);
      final PyResolveContext resolveContext = PyResolveContext.noImplicits();
      if (referenceExpression.getReference(resolveContext).multiResolve(false).length == 0) {
        myUnresolved.add(referenceExpression);
      }
    }

    /**
     * Get list of {@link PyReferenceExpression} that left myUnresolved after function override.
     *
     * @return list of {@link PyReferenceExpression} elements.
     */
    @NotNull
    List<PyReferenceExpression> getUnresolved() {
      return Collections.unmodifiableList(myUnresolved);
    }
  }

  /**
   * Resolves reference expressions by name and adds imports for them using references being visited.
   */
  private static class ResolveExpressionVisitor extends PyRecursiveElementVisitor {

    private final Map<String, PyReferenceExpression> myExpressionsToResolve;

    /**
     * {@link PyReferenceExpression} objects to resolve.
     *
     * @param toResolve collection of references to resolve.
     */
    ResolveExpressionVisitor(@NotNull Collection<PyReferenceExpression> toResolve) {
      myExpressionsToResolve = StreamEx.of(toResolve)
              .toMap(PyReferenceExpression::getName, Function.identity(),
                     (expression1, expression2) -> expression2);
    }

    @Override
    public void visitPyReferenceExpression(final PyReferenceExpression referenceExpression) {
      super.visitPyReferenceExpression(referenceExpression);

      if (myExpressionsToResolve.containsKey(referenceExpression.getName())) {
        PyClassRefactoringUtil.rememberNamedReferences(referenceExpression);
        PyClassRefactoringUtil.restoreReference(referenceExpression,
                                                myExpressionsToResolve.get(referenceExpression.getName()),
                                                PsiElement.EMPTY_ARRAY);
      }
    }
  }
}
