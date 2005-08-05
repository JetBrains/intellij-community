package com.intellij.refactoring.extractInterface;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.refactoring.ui.YesNoPreviewUsagesDialog;

/**
 * @author dsl
 */
public class ExtractClassUtil {
  public static void askAndTurnRefsToSuper(final Project project, PsiClass aClass, final PsiClass aSuperClass) {
    final SmartPsiElementPointer classPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(aClass);
    final SmartPsiElementPointer interfacePointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(aSuperClass);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final PsiElement classElement = classPointer.getElement();
        final PsiElement interfaceElement = interfacePointer.getElement();
        if (classElement != null && classElement instanceof PsiClass && interfaceElement != null && interfaceElement instanceof PsiClass) {
          final PsiClass superClass = (PsiClass) interfaceElement;
          String interfaceName = superClass.getName();
          String className = ((PsiClass) classElement).getName();
          String message = (superClass.isInterface() ? "Interface " : "Class ") +
                  interfaceName + " has been successfully created.\n" +
                  "At this stage, " + ApplicationNamesInfo.getInstance().getProductName() +
                  " can analyze usages of " + className + "\nand replace them with usages of the " +
                  (superClass.isInterface() ? "interface " : "superclass ") +
                  "where possible.\n" +
                  "Do you want to proceed?";
          YesNoPreviewUsagesDialog dialog = new YesNoPreviewUsagesDialog(
                  "Analyze and Replace Usages",
                  message,
                  RefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES,
                  /*HelpID.TURN_REFS_TO_SUPER*/null, project);
          dialog.show();
          if (dialog.isOK()) {
            final boolean isPreviewUsages = dialog.isPreviewUsages();
            RefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES = isPreviewUsages;
            TurnRefsToSuperProcessor processor =
                    new TurnRefsToSuperProcessor(project, (PsiClass) classElement, (PsiClass) interfaceElement, true);
            processor.setPreviewUsages(isPreviewUsages);
            processor.run();
          }
        }
      }
    });
  }
}
