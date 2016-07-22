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
package com.jetbrains.python.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.Function;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyInlineLocalHandler extends InlineActionHandler {
  private static final Logger LOG = Logger.getInstance(PyInlineLocalHandler.class.getName());

  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.variable.title");
  private static final Pair<PyStatement, Boolean> EMPTY_DEF_RESULT = Pair.create(null, false);
  private static final String HELP_ID = "python.reference.inline";

  public static PyInlineLocalHandler getInstance() {
    return Extensions.findExtension(EP_NAME, PyInlineLocalHandler.class);
  }

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l instanceof PythonLanguage;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PyTargetExpression;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    if (editor == null) {
      return;
    }
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    PyReferenceExpression refExpr = null;
    if (psiReference != null) {
      final PsiElement refElement = psiReference.getElement();
      if (refElement instanceof PyReferenceExpression) {
        refExpr = (PyReferenceExpression)refElement;
      }
    }
    invoke(project, editor, (PyTargetExpression)element, refExpr);
  }

  private static void invoke(@NotNull final Project project,
                             @NotNull final Editor editor,
                             @NotNull final PyTargetExpression local,
                             @Nullable PyReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    final TextAttributes writeAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    final String localName = local.getName();
    final ScopeOwner containerBlock = getContext(local);
    LOG.assertTrue(containerBlock != null);


    final Pair<PyStatement, Boolean> defPair = getAssignmentToInline(containerBlock, refExpr, local, project);
    final PyStatement def = defPair.first;
    if (def == null || getValue(def) == null) {
      final String key = defPair.second ? "variable.has.no.dominating.definition" : "variable.has.no.initializer";
      final String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    if (def instanceof PyAssignmentStatement && ((PyAssignmentStatement)def).getTargets().length > 1) {
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{def}, writeAttributes, true, null);
      final String message =
        RefactoringBundle.getCannotRefactorMessage(PyBundle.message("refactoring.inline.local.multiassignment", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    final PsiElement[] refsToInline = PyDefUseUtil.getPostRefs(containerBlock, local, getObject(def));
    if (refsToInline.length == 0) {
      final String message = RefactoringBundle.message("variable.is.never.used", localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    final TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      highlightManager.addOccurrenceHighlights(editor, refsToInline, attributes, true, null);
      final int occurrencesCount = refsToInline.length;
      final String occurrencesString = RefactoringBundle.message("occurrences.string", occurrencesCount);
      final String question = RefactoringBundle.message("inline.local.variable.prompt", localName) + " " + occurrencesString;
      final RefactoringMessageDialog dialog =
        new RefactoringMessageDialog(REFACTORING_NAME, question, HELP_ID, "OptionPane.questionIcon", true, project);
      if (!dialog.showAndGet()) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        final String message = RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final List<PsiElement> elems = new ArrayList<>();
      final List<Instruction> latestDefs = PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), ref, false, false);
      for (Instruction i : latestDefs) {
        elems.add(i.getElement());
      }
      final PsiElement[] defs = elems.toArray(new PsiElement[elems.size()]);
      boolean isSameDefinition = true;
      for (PsiElement otherDef : defs) {
        isSameDefinition &= isSameDefinition(def, otherDef);
      }
      if (!isSameDefinition) {
        highlightManager.addOccurrenceHighlights(editor, defs, writeAttributes, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, attributes, true, null);
        final String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }


    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(local);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringStarted(getRefactoringId(), afterData);

        final PsiElement[] exprs = new PsiElement[refsToInline.length];
        final PyExpression value = prepareValue(def, localName, project);
        final PyExpression withParenthesis =
          PyElementGenerator.getInstance(project).createExpressionFromText("(" + value.getText() + ")");
        final PsiElement lastChild = def.getLastChild();
        if (lastChild != null && lastChild.getNode().getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
          final PsiElement parent = def.getParent();
          if (parent != null) parent.addBefore(lastChild, def);
        }

        for (int i = 0, refsToInlineLength = refsToInline.length; i < refsToInlineLength; i++) {
          final PsiElement element = refsToInline[i];
          if (PyReplaceExpressionUtil.isNeedParenthesis((PyExpression)element, value)) {
            exprs[i] = element.replace(withParenthesis);
          }
          else {
            exprs[i] = element.replace(value);
          }
        }
        final PsiElement next = def.getNextSibling();
        if (next instanceof PsiWhiteSpace) {
          PyPsiUtils.removeElements(next);
        }
        PyPsiUtils.removeElements(def);

        final List<TextRange> ranges = ContainerUtil.mapNotNull(exprs, element -> {
          final PyStatement parentalStatement = PsiTreeUtil.getParentOfType(element, PyStatement.class, false);
          return parentalStatement != null ? parentalStatement.getTextRange() : null;
        });
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        CodeStyleManager.getInstance(project).reformatText(workingFile, ranges);

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          highlightManager.addOccurrenceHighlights(editor, exprs, attributes, true, null);
          WindowManager.getInstance().getStatusBar(project)
            .setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        }
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(local);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringDone(getRefactoringId(), afterData);
      }
    }), RefactoringBundle.message("inline.command", localName), null);
  }

  private static boolean isSameDefinition(PyStatement def, PsiElement otherDef) {
    if (otherDef instanceof PyTargetExpression) otherDef = otherDef.getParent();
    return otherDef == def;
  }

  private static ScopeOwner getContext(PyTargetExpression local) {
    ScopeOwner context = PsiTreeUtil.getParentOfType(local, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(local, PyClass.class);
    }
    if (context == null) {
      context = (PyFile)local.getContainingFile();
    }
    return context;
  }

  private static Pair<PyStatement, Boolean> getAssignmentToInline(ScopeOwner containerBlock, PyReferenceExpression expr,
                                                                  PyTargetExpression local, Project project) {
    if (expr != null) {
      try {
        final List<Instruction> candidates = PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), expr, true, true);
        if (candidates.size() == 1) {
          final PyStatement expression = getAssignmentByLeftPart((PyElement)candidates.get(0).getElement());
          return Pair.create(expression, false);
        }
        return Pair.create(null, candidates.size() > 0);
      }
      catch (PyDefUseUtil.InstructionNotFoundException ignored) {
      }
    }
    final Query<PsiReference> query = ReferencesSearch.search(local, GlobalSearchScope.allScope(project), false);
    final PsiReference first = query.findFirst();

    final PyElement lValue = first != null ? (PyElement)first.resolve() : null;
    return lValue != null ? Pair.create(getAssignmentByLeftPart(lValue), false) : EMPTY_DEF_RESULT;
  }

  @Nullable
  private static PyStatement getAssignmentByLeftPart(PyElement candidate) {
    final PsiElement parent = candidate.getParent();
    return parent instanceof PyAssignmentStatement || parent instanceof PyAugAssignmentStatement ? (PyStatement)parent : null;
  }

  @Nullable
  private static PyExpression getValue(@Nullable PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getAssignedValue();
    }
    return ((PyAugAssignmentStatement)def).getValue();
  }

  @Nullable
  private static PyExpression getObject(@Nullable PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getTargets()[0];
    }
    return ((PyAugAssignmentStatement)def).getTarget();
  }

  @NotNull
  private static PyExpression prepareValue(@NotNull PyStatement def, @NotNull String localName, @NotNull Project project) {
    final PyExpression value = getValue(def);
    assert value != null;
    if (def instanceof PyAugAssignmentStatement) {
      final PyAugAssignmentStatement expression = (PyAugAssignmentStatement)def;
      final PsiElement operation = expression.getOperation();
      assert operation != null;
      final String op = operation.getText().replace('=', ' ');
      return PyElementGenerator.getInstance(project).createExpressionFromText(localName + " " + op + value.getText() + ")");
    }
    return value;
  }

  public static String getRefactoringId() {
    return "refactoring.python.inline.local";
  }
}
