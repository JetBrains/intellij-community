package com.intellij.refactoring.rename.inplace;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class VariableInplaceRenameHandler implements RenameHandler {
  private static final @NonNls String INVOKING_DEFAULT = "$$$"+VariableInplaceRenameHandler.class.hashCode() + "_inplace_renaming_failed_due_to_noinlocal_usages";
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler");

  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (editor == null || file == null) return false;
    
    if (dataContext.getData(INVOKING_DEFAULT) != null) {
      return false;
    }
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());

    final RefactoringSupportProvider supportProvider = element != null ? LanguageRefactoringSupport.INSTANCE.forLanguage(element.getLanguage()):null;
    return supportProvider != null &&
           editor.getSettings().isVariableInplaceRenameEnabled() &&
           supportProvider.doInplaceRenameFor(element, nameSuggestionContext);
  }

  public boolean isRenaming(final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    doRename(element, editor, dataContext);
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = PsiElementRenameHandler.getElement(dataContext);
    LOG.assertTrue(element != null);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    doRename(element, editor, dataContext);
  }

  private static void doRename(final PsiElement elementToRename, final Editor editor, final DataContext dataContext) {
    final boolean startedRename = new VariableInplaceRenamer((PsiNameIdentifierOwner)elementToRename, editor).performInplaceRename();

    if (!startedRename) {
      final DataContext ourDataContext = new DataContext() {
        public Object getData(@NonNls final String dataId) {
          if (INVOKING_DEFAULT.equals(dataId)) {
            return Boolean.TRUE;
          }
          return dataContext.getData(dataId);
        }
      };
      RenameHandlerRegistry.getInstance().getRenameHandler(ourDataContext).invoke(
        elementToRename.getProject(),
        editor,
        elementToRename.getContainingFile(), ourDataContext
      );
    }
  }
}
