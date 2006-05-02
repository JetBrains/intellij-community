package com.intellij.refactoring.tempWithQuery;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataConstants;
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
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class TempWithQueryHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.tempWithQuery.TempWithQueryHandler");

  private static final String REFACTORING_NAME = RefactoringBundle.message("replace.temp.with.query.title");

  public void invoke(final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = TargetElementUtil.findTargetElement(editor,
                                                             TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.LOOKUP_ITEM_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
    );
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!(element instanceof PsiLocalVariable)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.name"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }

    invokeOnVariable(file, project, ((PsiLocalVariable)element), editor);
  }

  private void invokeOnVariable(final PsiFile file, final Project project, final PsiLocalVariable local, final Editor editor) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    String localName = local.getName();
    final PsiExpression initializer = local.getInitializer();
    if (initializer == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.has.no.initializer", localName));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }

    PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    final PsiReference[] refs = searchHelper.findReferences(local, GlobalSearchScope.projectScope(project), false);

    if (refs.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.never.used", localName));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
      return;
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    ArrayList<PsiReference> array = new ArrayList<PsiReference>();
    EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        array.add(ref);
      }
      if (array.size() > 0) {
        PsiReference[] refsForWriting = array.toArray(new PsiReference[array.size()]);
        highlightManager.addOccurrenceHighlights(editor, refsForWriting, attributes, true, null);
        String message =  RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", localName));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(
            project, editor,
            new PsiElement[]{initializer}, local.getType(),
            REFACTORING_NAME, localName, HelpID.REPLACE_TEMP_WITH_QUERY
    );

    try {
      if (!processor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, e.getMessage(), HelpID.REPLACE_TEMP_WITH_QUERY, project);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }
    final PsiClass targetClass = processor.getTargetClass();
    if (targetClass != null && targetClass.isInterface()) {
      String message = RefactoringBundle.message("cannot.replace.temp.with.query.in.interface");
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_TEMP_WITH_QUERY, project);
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

                  PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable () {
                    public void run() {
                      ApplicationManager.getApplication().runWriteAction(action);
                      DuplicatesImpl.processDuplicates(processor, project, editor);
                    }
                  });
                }
              },
          REFACTORING_NAME,
          null
      );
    }


    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements != null && elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
      if (dataContext != null) {
        final PsiFile file = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
        final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
        if (file != null && editor != null) {
          invokeOnVariable(file, project, (PsiLocalVariable)elements[0], editor);
        }
      }
    }
  }
}