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
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Alexey.Ivanov
 */
public class PyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil");

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

  public static void overrideMethods(final Editor editor, final PyClass pyClass, List<PyMethodMember> membersToOverride) {
    final List<String> newMembers = generateCode(membersToOverride);
    if (newMembers.isEmpty()) {
      return;
    }

    new WriteCommandAction(pyClass.getProject(), pyClass.getContainingFile()) {
      protected void run(final Result result) throws Throwable {
        write(pyClass, newMembers, pyClass.getProject(), editor);
      }
    }.execute();
  }

  private static void write(@NotNull final PyClass pyClass,
                            @NotNull final List<String> newMembers,
                            @NotNull final Project project,
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
    for (String newMember : newMembers) {
      element = PyElementGenerator.getInstance(project).createFromText(PyFunction.class, newMember + "\n    pass");
      try {
        element = (PyFunction)statementList.addAfter(element, anchor);
        element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    
    PyPsiUtils.removeRedundantPass(statementList);
    final int start = element.getStatementList().getTextRange().getStartOffset();
    editor.getCaretModel().moveToOffset(start);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(start, element.getTextRange().getEndOffset());
  }

  private static List<String> generateCode(final List<PyMethodMember> members) {
    if (members == null) {
      return Collections.emptyList();
    }
    List<String> newMembers = new ArrayList<String>();
    for (PyMethodMember member : members) {
      newMembers.add(generateNewMethod(member.getPsiElement()));
    }
    return newMembers;
  }

  @NotNull
  private static String generateNewMethod(@NotNull final PsiElement element) {
    assert (element instanceof PyFunction);
    final PyFunction function = (PyFunction)element;
    final StringBuilder newMethodText = new StringBuilder();
    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) {
      for (PyDecorator decorator: decoratorList.getDecorators()) {
        if ("classmethod".equals(decorator.getCallee().getText())) {
          newMethodText.append("@classmethod\n");
        }
      }
    }
    return newMethodText.append("def ").append(function.getName()).append(function.getParameterList().getText()).append(":").toString();
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

  private PyOverrideImplementUtil() {
  }
}
