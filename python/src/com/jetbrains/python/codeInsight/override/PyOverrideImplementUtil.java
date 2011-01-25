package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyNoneType;
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
      return (PyClass) element.getPrevSibling();
    }
    return pyClass;
  }

  public static void chooseAndOverrideMethods(final Project project, @NotNull final Editor editor, @NotNull final PyClass pyClass) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.overrideimplement");
    chooseAndOverrideOrImplementMethods(project, editor, pyClass);
  }

  private static void chooseAndOverrideOrImplementMethods(final Project project,
                                                          @NotNull final Editor editor,
                                                          @NotNull final PyClass pyClass) {
    LOG.assertTrue(pyClass.isValid());
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Collection<PyFunction> superFunctions = getAllSuperFunctions(pyClass);
    List<PyMethodMember> elements = new ArrayList<PyMethodMember>();
    for (PyFunction function : superFunctions) {
      final String name = function.getName();
      if (name == null) {
        continue;
      }
      if (pyClass.findMethodByName(name, false) == null) {
        elements.add(new PyMethodMember(function));
      }
    }
    if (elements.size() == 0) {
      return;
    }

    final MemberChooser<PyMethodMember> chooser =
      new MemberChooser<PyMethodMember>(elements.toArray(new PyMethodMember[elements.size()]), false, true, project);
    chooser.setTitle("Select Methods to Override");
    chooser.setCopyJavadocVisible(false);
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    List<PyMethodMember> membersToOverride = chooser.getSelectedElements();
    overrideMethods(editor, pyClass, membersToOverride);
  }

  public static void overrideMethods(final Editor editor, final PyClass pyClass, final List<PyMethodMember> membersToOverride) {
    if (membersToOverride == null) {
      return;
    }
    new WriteCommandAction(pyClass.getProject(), pyClass.getContainingFile()) {
      protected void run(final Result result) throws Throwable {
        write(pyClass, membersToOverride, editor);
      }
    }.execute();
  }

  private static void write(@NotNull final PyClass pyClass,
                            @NotNull final List<PyMethodMember> newMembers,
                            @NotNull final Editor editor) {
    final PyStatementList statementList = pyClass.getStatementList();
    final int offset = editor.getCaretModel().getOffset();
    PsiElement anchor = null;
    for (PyStatement statement: statementList.getStatements()) {
      if (statement.getTextRange().getStartOffset() < offset) {
        anchor = statement;
      }
    }

    PyFunction element = null;
    for (PyMethodMember newMember : newMembers) {
      PyFunction baseFunction = (PyFunction) newMember.getPsiElement();
      final PyFunctionBuilder builder = buildOverriddenFunction(pyClass, baseFunction);
      PyFunction function = builder.addFunctionAfter(statementList, anchor, LanguageLevel.forElement(statementList));
      element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(function);
    }

    PyPsiUtils.removeRedundantPass(statementList);
    if (element != null) {
      final PyStatementList targetStatementList = element.getStatementList();
      final int start = targetStatementList != null
                        ? targetStatementList.getTextRange().getStartOffset()
                        : element.getTextRange().getStartOffset();
      editor.getCaretModel().moveToOffset(start);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(start, element.getTextRange().getEndOffset());
    }
  }

  private static PyFunctionBuilder buildOverriddenFunction(PyClass pyClass, PyFunction baseFunction) {
    PyFunctionBuilder pyFunctionBuilder = new PyFunctionBuilder(baseFunction.getName());
    final PyDecoratorList decorators = baseFunction.getDecoratorList();
    if (decorators != null && decorators.findDecorator(PyNames.CLASSMETHOD) != null) {
      pyFunctionBuilder.decorate(PyNames.CLASSMETHOD);
    }
    PyAnnotation anno = baseFunction.getAnnotation();
    if (anno != null) {
      pyFunctionBuilder.annotation(anno.getText());
    }
    final PyParameter[] baseParams = baseFunction.getParameterList().getParameters();
    for (PyParameter parameter : baseParams) {
      pyFunctionBuilder.parameter(parameter.getText());
    }

    PyClass baseClass = baseFunction.getContainingClass();
    assert baseClass != null;
    StringBuilder statementBody = new StringBuilder();

    String[] paramTexts = ContainerUtil.map(baseParams, new Function<PyParameter, String>() {
      @Override
      public String fun(PyParameter pyParameter) {
        final PyNamedParameter pyNamedParameter = pyParameter.getAsNamed();
        if (pyNamedParameter != null) {
          return pyNamedParameter.getRepr(false);
        }
        return pyParameter.getText();
      }
    }, ArrayUtil.EMPTY_STRING_ARRAY);
    int startIndex = 0;

    if (PyNames.FAKE_OLD_BASE.equals(baseFunction.getContainingClass().getName())) {
      statementBody.append("pass");
    }
    else {
      if (baseFunction.getReturnType(TypeEvalContext.slow(), null) != PyNoneType.INSTANCE) {
        statementBody.append("return ");
      }
      if (baseClass.isNewStyleClass()) {
        statementBody.append(PyNames.SUPER);
        statementBody.append("(");
        final LanguageLevel langLevel = ((PyFile)pyClass.getContainingFile()).getLanguageLevel();
        if (!langLevel.isPy3K()) {
          statementBody.append(pyClass.getName()).append(", ").append(PyUtil.getFirstParameterName(baseFunction));
        }
        statementBody.append(").").append(baseFunction.getName()).append("(");
        startIndex = 1;
      }
      else {
        statementBody.append(getReferenceText(pyClass, baseClass)).append(".").append(baseFunction.getName()).append("(");
      }
      statementBody.append(StringUtil.join(paramTexts, startIndex, paramTexts.length, ", "));
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
        PsiElement target = ((PyReferenceExpression) expression).getReference().resolve();
        if (target == toClass) {
          return expression.getText();
        }
      }
    }
    return toClass.getName();
  }

  @NotNull
  private static Collection<PyFunction> getAllSuperFunctions(@NotNull final PyClass pyClass) {
    final Map<String, PyFunction> superFunctions = new HashMap<String, PyFunction>();
    for (PyClass aClass : pyClass.iterateAncestorClasses()) {
      for (PyFunction function : aClass.getMethods()) {
        if (!superFunctions.containsKey(function.getName())) {
          superFunctions.put(function.getName(), function);
        }
      }
    }
    return superFunctions.values();
  }
}
