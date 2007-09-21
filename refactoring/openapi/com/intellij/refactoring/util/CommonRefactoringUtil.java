package com.intellij.refactoring.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ven
 */
public class CommonRefactoringUtil {
  private CommonRefactoringUtil() {}

  public static void showErrorMessage(String title, String message, String helpId, @NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(title, message, helpId, "OptionPane.errorIcon", false, project);
    dialog.show();
  }

  @NonNls
  public static String htmlEmphasize(String text) {
    return "<b><code>" + text + "</code></b>";
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement element) {
    return checkReadOnlyStatus(element, project, RefactoringBundle.message("refactoring.cannot.be.performed"));
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement... elements) {
    return checkReadOnlyStatus(Arrays.asList(elements), project, RefactoringBundle.message("refactoring.cannot.be.performed"), false, true);
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element, @NotNull Project project, String messagePrefix) {
    return element.isWritable() ||
           checkReadOnlyStatus(Collections.singleton(element), project, messagePrefix, false, true);
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements) {
    return checkReadOnlyStatus(elements, project, RefactoringBundle.message("refactoring.cannot.be.performed"), true, false);
  }
  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(elements, project, RefactoringBundle.message("refactoring.cannot.be.performed"), true, notifyOnFail);
  }

  private static boolean checkReadOnlyStatus(@NotNull Collection<? extends PsiElement> elements,
                                             @NotNull Project project,
                                             final String messagePrefix,
                                             boolean recursively,
                                             final boolean notifyOnFail) {
    //Not writable, but could be checked out
    final Collection<VirtualFile> readonly = new THashSet<VirtualFile>();
    //Those located in jars
    final Collection<VirtualFile> failed = new THashSet<VirtualFile>();
    boolean seenNonWritablePsiFilesWithoutVirtualFile = false;

    for (PsiElement element : elements) {
      if (element.isWritable()) continue;

      if (element instanceof PsiDirectory) {
        PsiDirectory dir = (PsiDirectory)element;
        final VirtualFile vFile = dir.getVirtualFile();
        if (vFile.getFileSystem() instanceof JarFileSystem) {
          /*String message1 = messagePrefix + ".\n Directory " + vFile.getPresentableUrl() + " is located in a jar file.";
         showErrorMessage("Cannot Modify Jar", message1, null, project);
         return false;*/
          failed.add(vFile);
        }
        else {
          if (recursively) {
            addVirtualFiles(vFile, readonly);

          }
          else {
            readonly.add(vFile);
          }
        }
      }
      else if (element instanceof PsiPackage) {
        final PsiDirectory[] directories = ((PsiPackage)element).getDirectories();
        for (PsiDirectory directory : directories) {
          VirtualFile virtualFile = directory.getVirtualFile();
          if (recursively) {
            if (virtualFile.getFileSystem() instanceof JarFileSystem) {
              failed.add(virtualFile);
            }
            else {
              addVirtualFiles(virtualFile, readonly);
            }
          }
          else {
            if (!directory.isWritable()) {
              if (virtualFile.getFileSystem() instanceof JarFileSystem) {
                failed.add(virtualFile);
              }
              else {
                readonly.add(virtualFile);
              }
            }
          }
        }
      }
      else if (element instanceof PsiCompiledElement) {
        final PsiFile file = element.getContainingFile();
        if (file != null) {
          failed.add(file.getVirtualFile());
        }
      }
      else {
        PsiFile file = element.getContainingFile();
        if (file == null) {
          seenNonWritablePsiFilesWithoutVirtualFile = true;
        }
        else if (!file.isWritable()) {
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            readonly.add(vFile);
          }
          else {
            seenNonWritablePsiFilesWithoutVirtualFile = true;
          }
        }
      }
    }

    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(readonly.toArray(new VirtualFile[readonly.size()]));
    failed.addAll(Arrays.asList(status.getReadonlyFiles()));
    if (notifyOnFail && (!failed.isEmpty() || seenNonWritablePsiFilesWithoutVirtualFile && readonly.isEmpty())) {
      StringBuilder message = new StringBuilder(messagePrefix);
      message.append('\n');
      int i = 0;
      for (VirtualFile virtualFile : failed) {
        final String presentableUrl = virtualFile.getPresentableUrl();
        final String subj = virtualFile.isDirectory()
                            ? RefactoringBundle.message("directory.description", presentableUrl)
                            : RefactoringBundle.message("file.description", presentableUrl);
        if (virtualFile.getFileSystem() instanceof JarFileSystem) {
          message.append(RefactoringBundle.message("0.is.located.in.a.jar.file", subj));
        }
        else {
          message.append(RefactoringBundle.message("0.is.read.only", subj));
        }
        if (i++ > 20) {
          message.append("...\n");
          break;
        }
      }
      showErrorMessage(RefactoringBundle.message("error.title"), message.toString(), null, project);
      return false;
    }

    return failed.isEmpty();
  }

  private static void addVirtualFiles(final VirtualFile vFile, final Collection<VirtualFile> list) {
    if (!vFile.isWritable()) {
      list.add(vFile);
    }
    final VirtualFile[] children = vFile.getChildren();
    if (children != null) {
      for (VirtualFile virtualFile : children) {
        addVirtualFiles(virtualFile, list);
      }
    }
  }
}
