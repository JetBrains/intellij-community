package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
  private final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenamePsiPackageProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiPackage;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    final PsiPackage psiPackage = (PsiPackage)element;
    psiPackage.handleQualifiedNameChange(RenameUtil.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName));
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    return getPackageQualifiedNameAfterRename((PsiPackage)element, newName, nonJava);
  }

  public static String getPackageQualifiedNameAfterRename(final PsiPackage element, final String newName, final boolean nonJava) {
    if (nonJava) {
      String qName = element.getQualifiedName();
      int index = qName.lastIndexOf('.');
      return index < 0 ? newName : qName.substring(0, index + 1) + newName;
    }
    else {
      return newName;
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    preparePackageRenaming((PsiPackage)element, newName, allRenames);
  }

  public static void preparePackageRenaming(PsiPackage psiPackage, final String newName, Map<PsiElement, String> allRenames) {
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
        allRenames.put(directory, newName);
      }
    }
  }

  @Nullable
  public Runnable getPostRenameCallback(final PsiElement element, final String newName, final RefactoringElementListener listener) {
    final Project project = element.getProject();
    final PsiPackage psiPackage = (PsiPackage)element;
    final String newQualifiedName = RenameUtil.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
    return new Runnable() {
      public void run() {
        final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(newQualifiedName);
        if (aPackage == null) {
          LOG.error("Package cannot be found: "+newQualifiedName+"; listener="+listener);
        }
        listener.elementRenamed(aPackage);
      }
    };
  }
}
