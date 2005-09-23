package com.intellij.refactoring.rename;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.xml.util.XmlUtil;

/**
 * created at Nov 13, 2001
 * @author Jeka, dsl
 */
public class PsiElementRenameHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.PsiElementRenameHandler");
  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(element, project, nameSuggestionContext);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    PsiElement element = elements != null && elements.length == 1 ? elements[0] : null;
    LOG.assertTrue(element != null);
    invoke(element, project, element);
  }

  public static void invoke(PsiElement element, Project project, PsiElement nameSuggestionContext) {
    if (element != null && !canRename(element, project)) {
      return;
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.rename");

    if (element instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)element;
      PsiPackage aPackage = psiDirectory.getPackage();
      final String qualifiedName = aPackage != null ? aPackage.getQualifiedName() : "";
      if (aPackage == null || qualifiedName.length() == 0/*default package*/) {
        rename(element, project, nameSuggestionContext);
      } else {
        PsiDirectory[] directories = aPackage.getDirectories();
        final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
        if (virtualFiles.length == 0 && directories.length == 1) {
          rename(aPackage, project, nameSuggestionContext);
        }
        else { // the directory corresponds to a package that has multiple associated directories
          StringBuffer message = new StringBuffer();
          RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
          RenameUtil.buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
          message.append("\n\nAll these directories and all references to package \n");
          message.append(qualifiedName);
          message.append("\nwill be renamed.");
          message.append("\nDo you wish to continue?");
          int ret = Messages.showYesNoDialog(project, message.toString(), "Warning", Messages.getWarningIcon());
          if (ret != 0) {
            return;
          }
          // if confirmed
          rename(aPackage, project, nameSuggestionContext);
        }
      }
    }
    else {
      rename(element, project, nameSuggestionContext);
    }
  }

  private static boolean canRename(PsiElement element, Project project) {
    if (element instanceof XmlAttributeValue) {
      XmlAttribute value = (XmlAttribute)element.getParent();
      if (XmlUtil.isAntTargetDefinition(value) || XmlUtil.isAntPropertyDefinition(value)) {
        if (!element.isWritable()) {
          if (!RefactoringMessageUtil.checkReadOnlyStatus(project, element)) return false;
        }
        return true;
      }
    }

    final String REFACTORING_NAME = "Rename";
    if (element == null ||
        !(element instanceof PsiFile || element instanceof PsiPointcutDef || element instanceof PsiPackage ||
          element instanceof PsiDirectory || element instanceof PsiClass || element instanceof PsiVariable ||
          element instanceof PsiMethod || element instanceof PsiNamedElement || element instanceof XmlAttributeValue)) {
      String message =
        "Cannot perform the refactoring.\n" +
        "The caret should be positioned at the symbol to be renamed.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      return false;
    }

    if (!PsiManager.getInstance(project).isInProject(element)) {
      String message =
        "Cannot perform the refactoring.\n" +
        "Selected " + UsageViewUtil.getType(element) + " is not located inside the project.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
      return false;
    }

    if (!element.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, element)) return false;
    }
    return true;
  }


  private static void rename(PsiElement element, final Project project, PsiElement nameSuggestionContext) {
    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (psiMethod.isConstructor()) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) return;
        String classType = UsageViewUtil.getType(containingClass);
        String className = UsageViewUtil.getShortName(containingClass);
        int ret = Messages.showYesNoDialog(
          project,
          "Constructor cannot be renamed.\nWould you like to rename " + classType + " " + className + "?",
          "Warning",
          Messages.getQuestionIcon());
        if (ret != 0) {
          return;
        }
        element = containingClass;
        if (!canRename(element, project)) {
          return;
        }
      }
    }

      PsiElement elementToRename = element;
      if (elementToRename instanceof PsiMethod) {
        elementToRename = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)elementToRename, "rename");
        if (elementToRename == null) return;

        elementToRename = EjbUtil.checkDeclMethod((PsiMethod)elementToRename, "rename");
        if (elementToRename == null) return;

        if (!elementToRename.isWritable()) {
          if (!RefactoringMessageUtil.checkReadOnlyStatus(project, elementToRename)) return;
        }
      }
      String helpID = HelpID.getRenameHelpID(elementToRename);
      final RenameDialog dialog =
        new RenameDialog(project, elementToRename, nameSuggestionContext, helpID);

      dialog.show();
  }

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    PsiElement[] elementArray = BaseRefactoringAction.getPsiElementArray(dataContext);
    if (elementArray == null || elementArray.length != 1 || elementArray[0] == null) {
      return false;
    }

    final PsiElement element = elementArray[0];
    if (element instanceof JspClass || element instanceof JspHolderMethod) return false;
    return !(element instanceof PsiJavaFile) || element instanceof JspFile;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }
}
