
package com.intellij.refactoring.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NonNls;

public class RefactoringMessageUtil {

  public static String getIncorrectIdentifierMessage(String identifierName) {
    return RefactoringBundle.message("0.is.not.a.legal.java.identifier", identifierName);
  }

  /**
   * @return null, if can create a class
   *         an error message, if cannot create a class
   *
   */
  public static String checkCanCreateClass(PsiDirectory destinationDirectory, String className) {
    PsiClass[] classes = destinationDirectory.getClasses();
    VirtualFile file = destinationDirectory.getVirtualFile();
    for (PsiClass aClass : classes) {
      if (className.equals(aClass.getName())) {
        return RefactoringBundle.message("directory.0.already.contains.1.named.2",
                                         file.getPresentableUrl(), UsageViewUtil.getType(aClass), className);
      }
    }
    @NonNls String fileName = className+".java";
    return checkCanCreateFile(destinationDirectory, fileName);
  }
  public static String checkCanCreateFile(PsiDirectory destinationDirectory, String fileName) {
    VirtualFile file = destinationDirectory.getVirtualFile();
    VirtualFile child = file.findChild(fileName);
    if (child != null) {
      return RefactoringBundle.message("directory.0.already.contains.a.file.named.1",
                                       file.getPresentableUrl(), fileName);
    }
    return null;
  }

  public static String getGetterSetterMessage(String newName, String action, PsiMethod getter, PsiMethod setter) {
    String text;
    if (getter != null && setter != null) {
      text = RefactoringBundle.message("getter.and.setter.methods.found.for.the.field.0", newName, action);
    } else if (getter != null) {
      text = RefactoringBundle.message("getter.method.found.for.the.field.0", newName, action);
    } else {
      text = RefactoringBundle.message("setter.method.found.for.the.field.0", newName, action);
    }
    return text;
  }

  public static void showNotSupportedForJspClassesError(final Project project, final String refactoringName, final String helpId) {
    String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
    CommonRefactoringUtil.showErrorMessage(refactoringName, message, helpId, project);
  }
}