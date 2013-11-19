/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.TargetElementUtilBase;
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
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.Query;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
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
    final PsiReference psiReference = TargetElementUtilBase.findReference(editor);
    PyReferenceExpression refExpr = null;
    if (psiReference != null) {
      final PsiElement refElement = psiReference.getElement();
      if (refElement instanceof PyReferenceExpression) {
        refExpr = (PyReferenceExpression) refElement;
      }
    }
    invoke(project, editor, (PyTargetExpression)element, refExpr);
  }

  public static void invoke(final Project project, final Editor editor, PyTargetExpression local, PyReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    final TextAttributes writeAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    final String localName = local.getName();
    final ScopeOwner containerBlock = getContext(local);
    LOG.assertTrue(containerBlock != null);


    final Pair<PyStatement, Boolean> defPair = getAssignmentToInline(containerBlock, refExpr, local, project);
    final PyStatement def = defPair.first;
    if (def == null || getValue(def) == null){
      final String key = defPair.second ? "variable.has.no.dominating.definition" : "variable.has.no.initializer";
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    if (def instanceof PyAssignmentStatement && ((PyAssignmentStatement)def).getTargets().length > 1){
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[] {def}, writeAttributes, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(PyBundle.message("refactoring.inline.local.multiassignment", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    final PsiElement[] refsToInline = PyDefUseUtil.getPostRefs(containerBlock, local, getObject(def));
    if (refsToInline.length == 0) {
      String message = RefactoringBundle.message("variable.is.never.used", localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    final TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      highlightManager.addOccurrenceHighlights(editor, refsToInline, attributes, true, null);
      int occurrencesCount = refsToInline.length;
      String occurencesString = RefactoringBundle.message("occurences.string", occurrencesCount);
      final String promptKey = "inline.local.variable.prompt";
      final String question = RefactoringBundle.message(promptKey, localName) + " " + occurencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(REFACTORING_NAME, question, HELP_ID, "OptionPane.questionIcon", true, project);
      dialog.show();
      if (!dialog.isOK()){
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        String message = RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final List<PsiElement> elems = new ArrayList<PsiElement>();
      final List<ReadWriteInstruction> latestDefs = PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), ref, false);
      for (ReadWriteInstruction i : latestDefs) {
        elems.add(i.getElement());
      }
      final PsiElement[] defs = elems.toArray(new PsiElement[elems.size()]);
      boolean isSameDefinition = true;
      for (PsiElement otherDef : defs) {
        isSameDefinition &= isSameDefinition(def, otherDef);
      }
      if (!isSameDefinition) {
        if (editor != null) {
          highlightManager.addOccurrenceHighlights(editor, defs, writeAttributes, true, null);
          highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, attributes, true, null);
          String message =
            RefactoringBundle
              .getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
        }
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }


    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            PsiElement[] exprs = new PsiElement[refsToInline.length];
            final PyExpression value = prepareValue(def, localName, project);
            final PyExpression withParent = PyElementGenerator.getInstance(project).createExpressionFromText("(" + value.getText() + ")");
            for (int i = 0, refsToInlineLength = refsToInline.length; i < refsToInlineLength; i++) {
              PsiElement element = refsToInline[i];
              if (PyReplaceExpressionUtil.isNeedParenthesis((PyExpression)element, value)) {
                exprs[i] = element.replace(withParent);
              } else {
                exprs[i] = element.replace(value);
              }
            }
            final PsiElement next = def.getNextSibling();
            if (next instanceof PsiWhiteSpace) {
              PyPsiUtils.removeElements(next);
            }
            PyPsiUtils.removeElements(def);
            if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
              highlightManager.addOccurrenceHighlights(editor, exprs, attributes, true, null);
              WindowManager.getInstance().getStatusBar(project)
                .setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
            }
          }
        });
      }
    }, RefactoringBundle.message("inline.command", localName), null);
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
        final List<ReadWriteInstruction> candidates = PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), expr, true);
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
    return parent instanceof PyAssignmentStatement || parent instanceof  PyAugAssignmentStatement ? (PyStatement)parent : null;
  }

  @Nullable
  private static PyExpression getValue(PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getAssignedValue();
    }
    return ((PyAugAssignmentStatement)def).getValue();
  }

  @Nullable
  private static PyExpression getObject(PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getTargets()[0];
    }
    return ((PyAugAssignmentStatement)def).getTarget();
  }

  private static PyExpression prepareValue(PyStatement def, String localName, Project project) {
    final PyExpression value = getValue(def);
    assert value != null;
    if (def instanceof PyAugAssignmentStatementImpl) {
      final PyAugAssignmentStatementImpl expression = (PyAugAssignmentStatementImpl)def;
      String op = expression.getOperation().getText().replace('=', ' ');
      return PyElementGenerator.getInstance(project).createExpressionFromText(localName + " " + op + value.getText() + ")");
    }
    return value;
  }
}
