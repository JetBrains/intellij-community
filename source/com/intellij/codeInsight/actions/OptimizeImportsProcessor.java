package com.intellij.codeInsight.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class OptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.OptimizeImportsProcessor");

  private static final String PROGRESS_TEXT = "Optimizing imports...";
  private static final String COMMAND_NAME = "Optimize Imports";

  public OptimizeImportsProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT);
  }

  public OptimizeImportsProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT);
  }

  public OptimizeImportsProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME);
  }

  public OptimizeImportsProcessor(Project project, PsiFile file) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME);
  }

  public OptimizeImportsProcessor(Project project, PsiFile[] files, Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, COMMAND_NAME, postRunnable);
  }

  protected Runnable preprocessFile(final PsiFile file) throws IncorrectOperationException {
    if (file instanceof PsiJavaFile) {
      final PsiImportList newImportList = CodeStyleManager.getInstance(myProject).prepareOptimizeImportsResult(file);
      return new Runnable() {
        public void run() {
          try {
            if (newImportList != null) {
              PsiImportList oldImportList = ((PsiJavaFile)file).getImportList();
              if (!oldImportList.getText().equals(newImportList.getText())) {
                oldImportList.replace(newImportList);
              }
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      };
    }
    else {
      return EmptyRunnable.getInstance();
    }
  }
}
