package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
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
import java.util.Collection;

public class StandardBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
  protected final Project myProject;
  private CheckinProjectPanel myPanel;

  public StandardBeforeCheckinHandler(final Project project, final CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
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

  public void runCheckinHandlers(final Runnable finishAction) {
    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    final Collection<VirtualFile> files = myPanel.getVirtualFiles();

    final Runnable performCheckoutAction = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        finishAction.run();
      }
    };

    final Runnable reformatCodeAndPerformCheckout = new Runnable() {
      public void run() {
        if (reformat(configuration, true)) {
          new ReformatCodeProcessor(myProject, getPsiFiles(files), performCheckoutAction).run();
        }
        else {
          performCheckoutAction.run();
        }
      }
    };

    if (configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT) {
      new OptimizeImportsProcessor(myProject, getPsiFiles(files), reformatCodeAndPerformCheckout).run();
    }
    else {
      reformatCodeAndPerformCheckout.run();
    }

  }

  private static boolean reformat(final VcsConfiguration configuration, boolean checkinProject) {
    return checkinProject ? configuration.REFORMAT_BEFORE_PROJECT_COMMIT : configuration.REFORMAT_BEFORE_FILE_COMMIT;
  }

  private PsiFile[] getPsiFiles(Collection<VirtualFile> selectedFiles) {
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
