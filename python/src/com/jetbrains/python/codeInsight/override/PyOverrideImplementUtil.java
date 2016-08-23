/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.override;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SpeedSearchComparator;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Alexey.Ivanov
 */
public class PyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil");

  private PyOverrideImplementUtil() {
  }

  @Nullable
  public static PyClass getContextClass(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

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

  public static void chooseAndOverrideMethods(final Project project, @NotNull final Editor editor, @NotNull final PyClass pyClass) {


    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);
    chooseAndOverrideOrImplementMethods(project, editor, pyClass);
  }


  private static void chooseAndOverrideOrImplementMethods(final Project project,
                                                          @NotNull final Editor editor,
                                                          @NotNull final PyClass pyClass) {
    PyPsiUtils.assertValid(pyClass);
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Set<PyFunction> result = new HashSet<>();
    TypeEvalContext context = TypeEvalContext.codeCompletion(project, null);
    final Collection<PyFunction> superFunctions = getAllSuperFunctions(pyClass, context);


    result.addAll(superFunctions);
    chooseAndOverrideOrImplementMethods(project, editor, pyClass, result, "Select Methods to Override", false);
  }

  public static void chooseAndOverrideOrImplementMethods(@NotNull final Project project,
                                                         @NotNull final Editor editor,
                                                         @NotNull final PyClass pyClass,
                                                         @NotNull final Collection<PyFunction> superFunctions,
                                                         @NotNull final String title, final boolean implement) {
    List<PyMethodMember> elements = new ArrayList<>();
    for (PyFunction function : superFunctions) {
      final String name = function.getName();
      if (name == null || PyUtil.isClassPrivateName(name)) {
        continue;
      }
      if (pyClass.findMethodByName(name, false, null) == null) {
        final PyMethodMember member = new PyMethodMember(function);
        elements.add(member);
      }
    }
    if (elements.size() == 0) {
      return;
    }

    final MemberChooser<PyMethodMember> chooser =
      new MemberChooser<PyMethodMember>(elements.toArray(new PyMethodMember[elements.size()]), false, true, project) {
        @Override
        protected SpeedSearchComparator getSpeedSearchComparator() {
          return new SpeedSearchComparator(false) {
            @Nullable
            @Override
            public Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
              return super.matchingFragments(PyMethodMember.trimUnderscores(pattern), text);
            }
          };
        }
      };
    chooser.setTitle(title);
    chooser.setCopyJavadocVisible(false);
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    List<PyMethodMember> membersToOverride = chooser.getSelectedElements();
    overrideMethods(editor, pyClass, membersToOverride, implement);
  }

  public static void overrideMethods(final Editor editor, final PyClass pyClass, final List<PyMethodMember> membersToOverride,
                                     final boolean implement) {
    if (membersToOverride == null) {
      return;
    }
    new WriteCommandAction(pyClass.getProject(), pyClass.getContainingFile()) {
      protected void run(@NotNull final Result result) throws Throwable {
        write(pyClass, membersToOverride, editor, implement);
      }
    }.execute();
  }

  private static void write(@NotNull final PyClass pyClass,
                            @NotNull final List<PyMethodMember> newMembers,
                            @NotNull final Editor editor, boolean implement) {
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
    for (PyMethodMember newMember : newMembers) {
      PyFunction baseFunction = (PyFunction)newMember.getPsiElement();
      final PyFunctionBuilder builder = buildOverriddenFunction(pyClass, baseFunction, implement);
      PyFunction function = builder.addFunctionAfter(statementList, anchor, LanguageLevel.forElement(statementList));
      element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
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
    PyAnnotation anno = baseFunction.getAnnotation();
    if (anno != null) {
      pyFunctionBuilder.annotation(anno.getText());
    }
    final TypeEvalContext context = TypeEvalContext.userInitiated(baseFunction.getProject(), baseFunction.getContainingFile());
    final List<PyParameter> baseParams = PyUtil.getParameters(baseFunction, context);
    for (PyParameter parameter : baseParams) {
      pyFunctionBuilder.parameter(parameter.getText());
    }

    PyClass baseClass = baseFunction.getContainingClass();
    assert baseClass != null;
    StringBuilder statementBody = new StringBuilder();

    boolean hadStar = false;
    List<String> parameters = new ArrayList<>();
    for (PyParameter parameter : baseParams) {
      final PyNamedParameter pyNamedParameter = parameter.getAsNamed();
      if (pyNamedParameter != null) {
        String repr = pyNamedParameter.getRepr(false);
        parameters.add(hadStar && !pyNamedParameter.isKeywordContainer() ? pyNamedParameter.getName() + "=" + repr : repr);
        if (pyNamedParameter.isPositionalContainer()) {
          hadStar = true;
        }
      }
      else if (parameter instanceof PySingleStarParameter) {
        hadStar = true;
      }
      else {
        parameters.add(parameter.getText());
      }
    }

    if (PyNames.FAKE_OLD_BASE.equals(baseClass.getName()) || raisesNotImplementedError(baseFunction) || implement) {
      statementBody.append(PyNames.PASS);
    }
    else {
      if (!PyNames.INIT.equals(baseFunction.getName()) && context.getReturnType(baseFunction) != PyNoneType.INSTANCE || overridingNew) {
        statementBody.append("return ");
      }
      if (baseClass.isNewStyleClass(context)) {
        statementBody.append(PyNames.SUPER);
        statementBody.append("(");
        final LanguageLevel langLevel = ((PyFile)pyClass.getContainingFile()).getLanguageLevel();
        if (!langLevel.isPy3K()) {
          final String baseFirstName = !baseParams.isEmpty() ? baseParams.get(0).getName() : null;
          final String firstName = baseFirstName != null ? baseFirstName : PyNames.CANONICAL_SELF;
          PsiElement outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass.class, true, PyFunction.class);
          String className = pyClass.getName();
          final List<String> nameResult = Lists.newArrayList(className);
          while (outerClass != null) {
            nameResult.add(0, ((PyClass)outerClass).getName());
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

  public static boolean raisesNotImplementedError(@NotNull PyFunction function) {
    PyStatementList statementList = function.getStatementList();
    IfVisitor visitor = new IfVisitor();
    statementList.accept(visitor);
    return !visitor.hasReturnInside && visitor.raiseNotImplemented;
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

  /**
   * Returns all super functions available through MRO.
   */
  @NotNull
  public static List<PyFunction> getAllSuperFunctions(@NotNull PyClass pyClass, @NotNull TypeEvalContext context) {
    final Map<String, PyFunction> functions = Maps.newLinkedHashMap();
    for (final PyClassLikeType type : pyClass.getAncestorTypes(context)) {
      if (type != null) {
        for (PyFunction function : PyTypeUtil.getMembersOfType(type, PyFunction.class, false, context)) {
          final String name = function.getName();
          if (name != null && !functions.containsKey(name)) {
            functions.put(name, function);
          }
        }
      }
    }
    return Lists.newArrayList(functions.values());
  }

  private static class IfVisitor extends PyRecursiveElementVisitor {
    private boolean hasReturnInside;
    private boolean raiseNotImplemented;

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      hasReturnInside = true;
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      final PyExpression[] expressions = node.getExpressions();
      if (expressions.length > 0) {
        final PyExpression firstExpression = expressions[0];
        if (firstExpression instanceof PyCallExpression) {
          final PyExpression callee = ((PyCallExpression)firstExpression).getCallee();
          if (callee != null && callee.getText().equals(PyNames.NOT_IMPLEMENTED_ERROR)) {
            raiseNotImplemented = true;
          }
        }
        else if (firstExpression.getText().equals(PyNames.NOT_IMPLEMENTED_ERROR)) {
          raiseNotImplemented = true;
        }
      }
    }
  }
}
