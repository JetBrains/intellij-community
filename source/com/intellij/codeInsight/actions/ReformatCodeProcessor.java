package com.intellij.codeInsight.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class ReformatCodeProcessor extends AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.ReformatCodeProcessor");

  private TextRange myRange;
  private static final String PROGRESS_TEXT = "Reformatting code...";
  private static final String COMMAND_NAME = "Reformat Code";

  public ReformatCodeProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT);
  }

  public ReformatCodeProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT);
  }

  public ReformatCodeProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME);
  }

  public ReformatCodeProcessor(Project project, PsiFile file, TextRange range) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME);
    myRange = range;
  }

  public ReformatCodeProcessor(Project project, PsiFile[] files, Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, COMMAND_NAME, postRunnable);
  }

  protected Runnable preprocessFile(final PsiFile file) throws IncorrectOperationException {
    return new Runnable() {
      public void run() {
        try {
          if (myRange == null) {
            CodeStyleManager.getInstance(myProject).reformat(file);
          }
          else {
            CodeStyleManager.getInstance(myProject).reformatRange(file, myRange.getStartOffset(),
                                                                  myRange.getEndOffset());
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }
}
