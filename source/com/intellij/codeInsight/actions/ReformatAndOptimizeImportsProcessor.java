package com.intellij.codeInsight.actions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class ReformatAndOptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  private static final String PROGRESS_TEXT = "Reformatting code...";
  private static final String COMMAND_NAME = "Reformat Code";

  private OptimizeImportsProcessor myOptimizeImportsProcessor;
  private ReformatCodeProcessor myReformatCodeProcessor;

  public ReformatAndOptimizeImportsProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project);
    myReformatCodeProcessor = new ReformatCodeProcessor(project);
  }

  public ReformatAndOptimizeImportsProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project, module);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, module);
  }

  public ReformatAndOptimizeImportsProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project, directory, includeSubdirs);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, directory, includeSubdirs);
  }

  public ReformatAndOptimizeImportsProcessor(Project project, PsiFile file) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project, file);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, file, null);
  }

  protected Runnable preprocessFile(PsiFile file) throws IncorrectOperationException {
    final Runnable r1 = myReformatCodeProcessor.preprocessFile(file);
    final Runnable r2 = myOptimizeImportsProcessor.preprocessFile(file);
    return new Runnable() {
      public void run() {
        r1.run();
        r2.run();
      }
    };
  }
}
