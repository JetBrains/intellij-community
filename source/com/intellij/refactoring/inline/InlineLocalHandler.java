
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowAnalyzer;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsControlFlowPolicy;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Iterator;

class InlineLocalHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineLocalHandler");

  private static final String REFACTORING_NAME = "Inline Variable";

  /**
   * should be called in AtomicAction
   */
  public void invoke(final Project project, final Editor editor, final PsiLocalVariable local) {
    if (!local.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, local);
      return;
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();
    final PsiExpression initializer = local.getInitializer();
    if (initializer == null){
      String message =
        "Cannot perform the refactoring.\n" +
        "Variable " + localName + " has no initializer.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }

    PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    final PsiReference[] refs = searchHelper.findReferences(local, GlobalSearchScope.projectScope(project), false);

    if (refs.length == 0){
      String message = "Variable " + localName + " is never used";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }

    final ArrayList<PsiReference> toInline = new ArrayList<PsiReference>(refs.length);
    final PsiJavaCodeReferenceElement firstWriteUsage = (PsiJavaCodeReferenceElement)filterUsagesToInline(refs, toInline);
    if (toInline.size() == 0) {
      String message = "Variable " + localName + " is never used before modification";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }
    final PsiElement lastUsage = (toInline.get(toInline.size() - 1)).getElement();
    final PsiElement codeFragment = ControlFlowUtil.findCodeFragment(local);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    ControlFlow controlFlow;
    try {
      controlFlow = new ControlFlowAnalyzer(codeFragment, new LocalsControlFlowPolicy(codeFragment), false).buildControlFlow();
      PsiElement commonParent = PsiTreeUtil.findCommonParent(local, lastUsage);
      PsiElement anchor = lastUsage;
      while (!commonParent.equals(anchor.getParent())) {
        anchor = anchor.getParent();
      }
      while (controlFlow.getEndOffset(anchor) < 0) {
        anchor = anchor.getParent();
      }
      int offset = controlFlow.getEndOffset(anchor);
      if (ControlFlowUtil.needVariableValueAt(local, controlFlow, offset)){
        ArrayList<PsiReference> refsForWriting = new ArrayList<PsiReference>(refs.length);
        for (int idx = 0; idx < refs.length; idx++) {
          PsiReference ref = refs[idx];
          if (PsiUtil.isAccessedForWriting((PsiExpression)ref.getElement())) {
            refsForWriting.add(ref);
          }
        }
        if (refsForWriting.size() > 0) {
          highlightManager.addOccurrenceHighlights(
            editor,
            refsForWriting.toArray(new PsiReference[refsForWriting.size()]),
            attributes, true, null
          );
          String message =
            "Cannot perform the refactoring.\n" +
            "Variable " + localName + " is accessed for writing.";
          RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
          WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
          return;
        }
      }

      if (firstWriteUsage != null) {
        PsiElement tmp = firstWriteUsage;
        int writeInstructionOffset;
        do {
          writeInstructionOffset = controlFlow.getEndOffset(tmp);
          tmp = tmp.getParent();
        } while(writeInstructionOffset < 0);

        for (Iterator<PsiReference> iterator = toInline.iterator(); iterator.hasNext();) {
          PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)iterator.next();
          if (ControlFlowUtil.isInstructionReachable(controlFlow, controlFlow.getStartOffset(ref), writeInstructionOffset)) {
            String message = "Cannot perform the refactoring.\n" +
            "Variable initializer does not dominate its usages.";
            RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
            return;
          }
        }
      }
    }
    catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // TODO : check if initializer uses fieldNames that possibly will be hidden by other
      // locals with the same names after inlining
      highlightManager.addOccurrenceHighlights(
        editor,
        toInline.toArray(new PsiReference[toInline.size()]),
        attributes, true, null
      );
      int occurrencesCount = toInline.size();
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        REFACTORING_NAME,
        "Inline local variable " + localName + "? (" + occurrencesCount + (occurrencesCount == 1? " occurrence)" : " occurrences)"),
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project);
      dialog.show();
      if (!dialog.isOK()){
        WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
        return;
      }
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        try{
          final PsiExpression initializer = local.getInitializer();
          PsiExpression[] exprs = new PsiExpression[toInline.size()];
          for(int idx = 0; idx < toInline.size(); idx++){
            PsiReference ref = toInline.get(idx);
            PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)ref.getElement();
            exprs[idx] = RefactoringUtil.inlineVariable(local, initializer, refElement);
          }
          if (firstWriteUsage != null) {
//            PsiReference firstWriteUsage = refs[toInline.size()];
            ControlFlow controlFlow;
            try {
              controlFlow = new ControlFlowAnalyzer(codeFragment, new LocalsControlFlowPolicy(codeFragment), false).buildControlFlow();
            }
            catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
              controlFlow = ControlFlow.EMPTY;
            }
            PsiElement insertAnchor = firstWriteUsage.getElement();
            PsiElement parent = PsiTreeUtil.findCommonParent(local, insertAnchor);
            while (!parent.equals(insertAnchor.getParent())) {
              insertAnchor = insertAnchor.getParent();
            }
            int startOffset = controlFlow.getStartOffset(insertAnchor);
            if (startOffset != -1) {
              insertAnchor = controlFlow.getElement(startOffset);
            }
            parent = PsiTreeUtil.findCommonParent(local, insertAnchor);
            while (!parent.equals(insertAnchor.getParent())) {
              insertAnchor = insertAnchor.getParent();
            }
            if (initializer != null) {
              initializer.delete();
            }
            PsiAssignmentExpression assignment = getAssignmentExpression(firstWriteUsage);
            PsiDeclarationStatement newDeclaration = createDeclarationStatement(local, assignment);
            parent.addBefore(newDeclaration, insertAnchor);
            if (assignment != null && local.getParent().getParent().equals(assignment.getParent().getParent())) {
              // there is a first assignment and it is on the _same_ level with the local variable declaration
              assignment.getParent().delete();
            }
          }
          local.delete();

          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            highlightManager.addOccurrenceHighlights(editor, exprs, attributes, true, null);
            WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
          }
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, "Inline " + localName, null);
  }

  /**
   *
   * @param refs  occurences
   * @param toInline  where to put results
   * @return  first write usage of a variable
   */
  private static PsiReference filterUsagesToInline(final PsiReference[] refs, ArrayList<PsiReference> toInline) {
    PsiReference firstWriteUsage = null;
    PsiExpression assignmentExpression = null;
    for (int idx = 0; idx < refs.length; idx++) {
      PsiReference ref = refs[idx];
      PsiElement refElement = ref.getElement();
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        if(assignmentExpression != null) break;
        assignmentExpression = (PsiExpression) refElement.getParent();
        firstWriteUsage = ref;
        continue;
      }
      if(assignmentExpression != null) {
        if(!PsiTreeUtil.isAncestor(assignmentExpression, refElement, true)) break;
      }
      toInline.add(ref);
    }
    return firstWriteUsage;
  }

  private PsiDeclarationStatement createDeclarationStatement(PsiLocalVariable local, PsiAssignmentExpression assignment) {
    PsiDeclarationStatement declaration = (PsiDeclarationStatement)local.getParent();
    try {
      if (assignment == null || !declaration.getParent().equals(assignment.getParent().getParent())) {
        declaration = (PsiDeclarationStatement)declaration.copy();
        PsiExpression initializer = ((PsiVariable)declaration.getDeclaredElements()[0]).getInitializer();
        if (initializer != null) {
          initializer.delete();
        }
      }
      else {
        PsiElementFactory factory = local.getManager().getElementFactory();
        declaration = factory.createVariableDeclarationStatement(
          local.getName(),
          local.getType(),
          assignment.getRExpression()
        );
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return declaration;
  }

  private static PsiAssignmentExpression getAssignmentExpression(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref;
    while (parent != null) {
      if (parent instanceof PsiMethod) return null;
      if (parent instanceof PsiAssignmentExpression) {
        return (PsiAssignmentExpression)parent;
      }
      parent = parent.getParent();
    }
    return null;
  }
}