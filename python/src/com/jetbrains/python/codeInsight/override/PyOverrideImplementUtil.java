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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
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
      PyFunction function = builder.addFunctionAfter(statementList, anchor);
      element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(function);
    }
    
    PyPsiUtils.removeRedundantPass(statementList);
    final int start = element.getStatementList().getTextRange().getStartOffset();
    editor.getCaretModel().moveToOffset(start);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(start, element.getTextRange().getEndOffset());
  }

  private static PyFunctionBuilder buildOverriddenFunction(PyClass pyClass, PyFunction baseFunction) {
    PyFunctionBuilder pyFunctionBuilder = new PyFunctionBuilder(baseFunction.getName());
    final PyDecoratorList decorators = baseFunction.getDecoratorList();
    if (decorators != null && decorators.findDecorator(PyNames.CLASSMETHOD) != null) {
      pyFunctionBuilder.decorate(PyNames.CLASSMETHOD);
    }
    final PyParameter[] baseParams = baseFunction.getParameterList().getParameters();
    for (PyParameter parameter : baseParams) {
      pyFunctionBuilder.parameter(parameter.getText());
    }

    PyClass baseClass = baseFunction.getContainingClass();
    assert baseClass != null;
    StringBuilder statementBody = new StringBuilder();
    if (baseClass.isNewStyleClass()) {
      statementBody.append(PyNames.SUPER);
      statementBody.append("(");
      final LanguageLevel langLevel = ((PyFile)pyClass.getContainingFile()).getLanguageLevel();
      if (!langLevel.isPy3K()) {
        statementBody.append(pyClass.getName()).append(", self");
      }
      statementBody.append(").").append(baseFunction.getName()).append("(");
      for (int i = 1; i < baseParams.length; i++) {
        statementBody.append(baseParams [i].getText());
      }
      statementBody.append(")");
    }
    else {
      statementBody.append(baseClass.getName()).append(".").append(baseFunction.getName()).append("(");
      for (PyParameter param : baseParams) {
        statementBody.append(param.getText());
      }
      statementBody.append(")");
    }

    pyFunctionBuilder.statement(statementBody.toString());
    return pyFunctionBuilder;
  }

  @NotNull
  private static Collection<PyFunction> getAllSuperFunctions(@NotNull final PyClass pyClass) {
    final Map<String, PyFunction> superFunctions = new HashMap<String, PyFunction>();
    final PyClass[] superClasses = PyUtil.getAllSuperClasses(pyClass);
    for (PyClass aClass : superClasses) {
      for (PyFunction function : aClass.getMethods()) {
        superFunctions.put(function.getName(), function);
      }
    }
    return superFunctions.values();
  }
}
