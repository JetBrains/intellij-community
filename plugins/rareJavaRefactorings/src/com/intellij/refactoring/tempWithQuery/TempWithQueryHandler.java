// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.tempWithQuery;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class TempWithQueryHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(TempWithQueryHandler.class);

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = TargetElementUtil.findTargetElement(editor, TargetElementUtil
                                                                       .ELEMENT_NAME_ACCEPTED |
                                                                     TargetElementUtil
                                                                       .LOOKUP_ITEM_ACCEPTED |
                                                                     TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!(element instanceof PsiLocalVariable)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRareRefactoringsBundle.message("error.wrong.caret.position.local.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    invokeOnVariable(file, project, (PsiLocalVariable)element, editor);
  }

  private static void invokeOnVariable(final PsiFile file, final Project project, final PsiLocalVariable local, final Editor editor) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    String localName = local.getName();
    final PsiExpression initializer = local.getInitializer();
    if (initializer == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.has.no.initializer", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    final PsiReference[] refs = ReferencesSearch.search(local, GlobalSearchScope.projectScope(project), false).toArray(
      PsiReference.EMPTY_ARRAY);

    if (refs.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.never.used", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    ArrayList<PsiReference> array = new ArrayList<>();
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        array.add(ref);
      }
      if (!array.isEmpty()) {
        PsiReference[] refsForWriting = array.toArray(PsiReference.EMPTY_ARRAY);
        highlightManager.addOccurrenceHighlights(editor, refsForWriting, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        String message =  RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("variable.is.accessed.for.writing", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_TEMP_WITH_QUERY);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(
      project, editor,
      new PsiElement[]{initializer}, local.getType(),
      getRefactoringName(), localName, HelpID.REPLACE_TEMP_WITH_QUERY
    );

    try {
      if (!processor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), getRefactoringName(), HelpID.REPLACE_TEMP_WITH_QUERY);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }
    final PsiClass targetClass = processor.getTargetClass();
    if (targetClass != null && targetClass.isInterface()) {
      String message = JavaRareRefactoringsBundle.message("cannot.replace.temp.with.query.in.interface");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }


    if (processor.showDialog()) {
      CommandProcessor.getInstance().executeCommand(
        project, () -> {
          final Runnable action = () -> {
            try {
              processor.doRefactoring();

              local.normalizeDeclaration();

              PsiExpression initializer1 = local.getInitializer();

              PsiExpression[] exprs = new PsiExpression[refs.length];
              for (int idx = 0; idx < refs.length; idx++) {
                PsiElement ref = refs[idx].getElement();
                exprs[idx] = (PsiExpression) ref.replace(initializer1);
              }
              PsiDeclarationStatement declaration = (PsiDeclarationStatement) local.getParent();
              declaration.delete();

              highlightManager.addOccurrenceHighlights(editor, exprs, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          };

          PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
            ApplicationManager.getApplication().runWriteAction(action);
            DuplicatesImpl.processDuplicates(processor, project, editor);
          });
        },
        getRefactoringName(),
        null
      );
    }


    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
      if (dataContext != null) {
        final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (file != null && editor != null) {
          invokeOnVariable(file, project, (PsiLocalVariable)elements[0], editor);
        }
      }
    }
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRareRefactoringsBundle.message("replace.temp.with.query.title");
  }
}