package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class StandardBeforeCheckinHandler extends BeforeCheckinHandler {

  protected final Project myProject;


  public StandardBeforeCheckinHandler(final Project project) {
    myProject = project;
  }


  public ReturnResult perform(VirtualFile[] filesToBeCommited) {

    if (getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT) {
      new OptimizeImportsProcessor(myProject, getPsiFiles(filesToBeCommited),
                                   null).run();
    }

    if (getSettings().REFORMAT_BEFORE_PROJECT_COMMIT) {
      new ReformatCodeProcessor(myProject, getPsiFiles(filesToBeCommited),
                                null).run();
    }


    return ReturnResult.COMMIT;
  }


  @Nullable
  public RefreshableOnComponent getConfigurationPanel() {
    final JCheckBox optimizeBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.optimize.imports"));
    final JCheckBox reformatBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.reformat.code"));

    return new RefreshableOnComponent() {
      public JComponent getComponent() {
        final JPanel panel = new JPanel(new GridLayout(2, 0));
        panel.add(optimizeBox);
        panel.add(reformatBox);
        return panel;
      }

      public void refresh() {
      }

      public void saveState() {
        getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = optimizeBox.isSelected();
        getSettings().REFORMAT_BEFORE_PROJECT_COMMIT = reformatBox.isSelected();
      }

      public void restoreState() {
        optimizeBox.setSelected(getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT);
        reformatBox.setSelected(getSettings().REFORMAT_BEFORE_PROJECT_COMMIT);
      }
    };
  }

  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  protected PsiFile[] getPsiFiles(VirtualFile[] selectedFiles) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (VirtualFile file : selectedFiles) {
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return result.toArray(new PsiFile[result.size()]);
  }

}
