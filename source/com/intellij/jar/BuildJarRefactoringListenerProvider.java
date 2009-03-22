package com.intellij.jar;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;

public class BuildJarRefactoringListenerProvider implements RefactoringElementListenerProvider {
  private Project myProject;

  public BuildJarRefactoringListenerProvider(Project project) {
    myProject = project;
  }

  public RefactoringElementListener getListener(PsiElement element) {
    if (element instanceof PsiClass) {
      String className = ((PsiClass)element).getQualifiedName();
      if (className == null) return null;

      final Module[] modules = ModuleManager.getInstance(myProject).getModules();
      RefactoringElementListenerComposite listener = null;
      for (Module module : modules) {
        final BuildJarSettings settings = BuildJarSettings.getInstance(module);
        final String mainClass = settings.getMainClass();
        if (className.equals(mainClass)) {
          if (listener == null) {
            listener = new RefactoringElementListenerComposite();
          }
          listener.addListener(new MainClassRefactoringListener(settings));
        }
      }
      return listener;
    }
    return null;
  }

  private static class MainClassRefactoringListener implements RefactoringElementListener {
    private final BuildJarSettings mySettings;

    public MainClassRefactoringListener(final BuildJarSettings settings) {
      mySettings = settings;
    }

    public void elementMoved(@NotNull PsiElement newElement) {
      mySettings.setMainClass(((PsiClass)newElement).getQualifiedName());
    }

    public void elementRenamed(@NotNull PsiElement newElement) {
      mySettings.setMainClass(((PsiClass)newElement).getQualifiedName());
    }
  }
}
