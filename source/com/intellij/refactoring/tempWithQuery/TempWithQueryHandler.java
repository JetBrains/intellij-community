package com.intellij.refactoring.tempWithQuery;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class TempWithQueryHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.tempWithQuery.TempWithQueryHandler");

  private static final String REFACTORING_NAME = "Replace Temp with Query";

  public void invoke(final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = TargetElementUtil.findTargetElement(editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.LOOKUP_ITEM_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
    );
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!(element instanceof PsiLocalVariable)) {
      String message =
              "Cannot perform the refactoring.\n" +
              "The caret should be positioned at the name of the local variable to be refactored.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }

    if (!file.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, file);
      return;
    }

    final PsiLocalVariable local = (PsiLocalVariable) element;

    String localName = local.getName();
    final PsiExpression initializer = local.getInitializer();
    if (initializer == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Variable " + localName + " has no initializer.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }

    PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    final PsiReference[] refs = searchHelper.findReferences(local, GlobalSearchScope.projectScope(project), false);

    if (refs.length == 0) {
      String message = "Variable " + localName + " is never used";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    ArrayList<PsiReference> array = new ArrayList<PsiReference>();
    EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    for (int idx = 0; idx < refs.length; idx++) {
      PsiReference ref = refs[idx];
      PsiElement refElement = ref.getElement();
      if (PsiUtil.isAccessedForWriting((PsiExpression) refElement)) {
        array.add(ref);
      }
      if (array.size() > 0) {
        PsiReference[] refsForWriting = array.toArray(new PsiReference[array.size()]);
        highlightManager.addOccurrenceHighlights(editor, refsForWriting, attributes, true, null);
        String message =
                "Cannot perform the refactoring.\n" +
                "Variable " + localName + " is accessed for writing.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
        WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
        return;
      }
    }

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(
            project, editor, file,
            new PsiElement[]{initializer}, local.getType(),
            REFACTORING_NAME, localName, HelpID.REPLACE_TEMP_WITH_QUERY
    );

    try {
      if (!processor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, e.getMessage(), HelpID.REPLACE_TEMP_WITH_QUERY, project);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }
    final PsiClass targetClass = processor.getTargetClass();
    if (targetClass != null && targetClass.isInterface()) {
      String message = "Cannot replace temp with query in interface.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }


    if (processor.showDialog()) {
      CommandProcessor.getInstance().executeCommand(
          project, new Runnable() {
                public void run() {
                  final Runnable action = new Runnable() {
                    public void run() {
                      try {
                        processor.doRefactoring();

                        local.normalizeDeclaration();

                        PsiExpression initializer = local.getInitializer();

                        PsiExpression[] exprs = new PsiExpression[refs.length];
                        for (int idx = 0; idx < refs.length; idx++) {
                          PsiElement ref = refs[idx].getElement();
                          exprs[idx] = (PsiExpression) ref.replace(initializer);
                        }
                        PsiDeclarationStatement declaration = (PsiDeclarationStatement) local.getParent();
                        declaration.delete();

                        highlightManager.addOccurrenceHighlights(editor, exprs, attributes, true, null);
                      } catch (IncorrectOperationException e) {
                        LOG.error(e);
                      }
                    }
                  };
                  ApplicationManager.getApplication().runWriteAction(action);
                }
              },
              REFACTORING_NAME,
              null
      );
      DuplicatesImpl.processDuplicates(processor, project, editor, REFACTORING_NAME);
    }


    WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
  }
}