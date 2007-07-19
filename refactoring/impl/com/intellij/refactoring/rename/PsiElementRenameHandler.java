package com.intellij.refactoring.rename;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.ant.PsiAntElement;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * created at Nov 13, 2001
 *
 * @author Jeka, dsl
 */
public class PsiElementRenameHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.PsiElementRenameHandler");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = getElement(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(element, project, nameSuggestionContext, editor);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = getElement(dataContext);
    LOG.assertTrue(element != null);
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    invoke(element, project, element, editor);
  }

  public static void invoke(PsiElement element, Project project, PsiElement nameSuggestionContext, Editor editor) {
    if (element != null && !canRename(element, project)) {
      return;
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.rename");

    if (element instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)element;
      PsiPackage aPackage = psiDirectory.getPackage();
      final String qualifiedName = aPackage != null ? aPackage.getQualifiedName() : "";
      if (aPackage == null || qualifiedName.length() == 0/*default package*/) {
        rename(element, project, nameSuggestionContext, editor);
      }
      else {
        PsiDirectory[] directories = aPackage.getDirectories();
        final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
        if (virtualFiles.length == 0 && directories.length == 1) {
          rename(aPackage, project, nameSuggestionContext, editor);
        }
        else { // the directory corresponds to a package that has multiple associated directories
          StringBuffer message = new StringBuffer();
          RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
          RenameUtil.buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
          message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed", qualifiedName));
          int ret =
            Messages.showYesNoDialog(project, message.toString(), RefactoringBundle.message("warning.title"), Messages.getWarningIcon());
          if (ret != 0) {
            return;
          }
          // if confirmed
          rename(aPackage, project, nameSuggestionContext, editor);
        }
      }
    }
    else {
      rename(element, project, nameSuggestionContext, editor);
    }
  }

  static boolean canRename(PsiElement element, Project project) {
    final String REFACTORING_NAME = RefactoringBundle.message("rename.title");
    if (element instanceof XmlTag && !(((XmlTag)element).getMetaData() instanceof PsiWritableMetaData) ||
        !(element instanceof PsiNamedElement || element instanceof XmlAttributeValue ||
          (element instanceof PsiMetaBaseOwner && ((PsiMetaBaseOwner)element).getMetaData() != null))) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.symbol"));
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      }
      return false;
    }

    if (!PsiManager.getInstance(project).isInProject(element) && element.isPhysical()) {
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("error.out.of.project.element", UsageViewUtil.getType(element)));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      return false;
    }

    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(element)) {
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("error.in.injected.lang.prefix.suffix", UsageViewUtil.getType(element)));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      return false;
    }

    return true;//CommonRefactoringUtil.checkReadOnlyStatus(project, element);
  }


  private static void rename(PsiElement element, final Project project, PsiElement nameSuggestionContext, Editor editor) {
    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (psiMethod.isConstructor()) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) return;
        element = containingClass;
        if (!canRename(element, project)) {
          return;
        }
      }
    }

    PsiElement elementToRename = element;
    if (elementToRename instanceof PsiMethod) {
      elementToRename = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)elementToRename, RefactoringBundle.message("to.rename"));
      if (elementToRename == null) return;
      //if (!CommonRefactoringUtil.checkReadOnlyStatus(project, elementToRename)) return;
    }

    if (editor != null) {
      if (elementToRename instanceof PsiVariable && VariableInplaceRenamer.mayRenameInplace((PsiVariable)elementToRename, editor,
                                                                                            nameSuggestionContext)) {
        new VariableInplaceRenamer((PsiVariable)elementToRename).performInplaceRename(editor);
        return;
      }
    }

    final RenameDialog dialog = new RenameDialog(project, elementToRename, nameSuggestionContext, editor);
    dialog.show();
  }

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiElement element = getElement(dataContext);

    if (element == null || (element instanceof PsiAntElement && !((PsiAntElement)element).canRename())) return false;

    return !(element instanceof JspClass) && !(element instanceof JspHolderMethod) &&
           (!(element instanceof PsiJavaFile) || PsiUtil.isInJspFile(element));
  }

  @Nullable
  protected static PsiElement getElement(final DataContext dataContext) {
    PsiElement[] elementArray = BaseRefactoringAction.getPsiElementArray(dataContext);

    if (elementArray.length != 1) {
      return null;
    }
    return elementArray[0];
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }
}
