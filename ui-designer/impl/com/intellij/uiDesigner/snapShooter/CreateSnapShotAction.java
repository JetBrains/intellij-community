/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CreateSnapShotAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    final IdeView view = (IdeView)e.getDataContext().getData(DataConstants.IDE_VIEW);
    if (project == null || view == null) {
      return;
    }

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    final SnapShotClient client = new SnapShotClient();
    List<RunnerAndConfigurationSettingsImpl> appConfigurations = new ArrayList<RunnerAndConfigurationSettingsImpl>();
    RunnerAndConfigurationSettingsImpl snapshotConfiguration = null;
    boolean connected = false;

    ApplicationConfigurationType cfgType = ApplicationConfigurationType.getInstance();
    RunnerAndConfigurationSettingsImpl[] racsi = RunManagerEx.getInstanceEx(project).getConfigurationSettings(cfgType);

    for(RunnerAndConfigurationSettingsImpl config: racsi) {
      if (config.getConfiguration() instanceof ApplicationConfiguration) {
        ApplicationConfiguration appConfig = (ApplicationConfiguration) config.getConfiguration();
        appConfigurations.add(config);
        if (appConfig.ENABLE_SWING_INSPECTOR) {
          snapshotConfiguration = config;
          if (appConfig.getLastSnapShooterPort() > 0) {
            try {
              client.connect(appConfig.getLastSnapShooterPort());
              connected = true;
            }
            catch(IOException ex) {
              connected = false;
            }
          }
        }
        if (connected) break;
      }
    }

    if (snapshotConfiguration == null) {
      snapshotConfiguration = promptForSnapshotConfiguration(project, appConfigurations);
      if (snapshotConfiguration == null) return;
    }

    if (!connected) {
      int rc = Messages.showYesNoDialog(project, "The application from which snapshots will be taken is not currently running. Would you like to run it?",
                                        UIDesignerBundle.message("snapshot.title"), Messages.getQuestionIcon());
      if (rc == 1) return;
      final JavaProgramRunner runner = ExecutionRegistry.getInstance().getDefaultRunner();

      final ApplicationConfiguration appConfig = (ApplicationConfiguration) snapshotConfiguration.getConfiguration();
      appConfig.setSnapShooterNotifyRunnable(new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              Messages.showMessageDialog(project, "Please prepare the running application for taking a form snapshot",
                                         UIDesignerBundle.message("snapshot.title"), Messages.getInformationIcon());
              try {
                client.connect(appConfig.getLastSnapShooterPort());
              }
              catch(IOException ex) {
                Messages.showMessageDialog(project, "Failed to establish form snapshot connection to running application",
                                           UIDesignerBundle.message("snapshot.title"), Messages.getErrorIcon());
                return;
              }
              runSnapShooterSession(client, project, dir, view);
            }
          });
        }
      });

      try {
        RunStrategy.getInstance().execute(snapshotConfiguration, runner, e.getDataContext());
      }
      catch (ExecutionException ex) {
        Messages.showMessageDialog(project, "Failed to prepare run profile: " + ex.getMessage(),
                                   UIDesignerBundle.message("snapshot.title"), Messages.getErrorIcon());
      }
    }
    else {
      runSnapShooterSession(client, project, dir, view);
    }
  }

  private void runSnapShooterSession(final SnapShotClient client, final Project project, final PsiDirectory dir, final IdeView view) {
    try {
      client.suspendSwing();
    }
    catch (IOException e1) {
      Messages.showMessageDialog(project, "Failed to establish form snapshot connection to running application",
                                 UIDesignerBundle.message("snapshot.title"), Messages.getInformationIcon());
      return;
    }

    final MyDialog dlg = new MyDialog(project, client, dir);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      int id = dlg.getSelectedComponentId();
      String snapshot = null;
      try {
        snapshot = client.createSnapshot(id);
      }
      catch (Exception ex) {
        Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.create.error", ex.getMessage()),
                                   UIDesignerBundle.message("snapshot.title"), Messages.getErrorIcon());
      }

      if (snapshot != null) {
        final String snapshot1 = snapshot;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              public void run() {
                try {
                  PsiFile formFile = dir.getManager().getElementFactory().createFileFromText(dlg.getFormName() + ".form", snapshot1);
                  formFile = (PsiFile)dir.add(formFile);
                  view.selectElement(formFile);
                }
                catch (IncorrectOperationException ex) {
                  Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.save.error", ex.getMessage()),
                                             UIDesignerBundle.message("snapshot.title"), Messages.getErrorIcon());
                }
              }
            }, "", null);
          }
        });
      }
    }

    try {
      client.resumeSwing();
    }
    catch (IOException e1) {
      Messages.showMessageDialog(project, "Swing resume failed", "SnapShooter", Messages.getInformationIcon());
    }

    client.dispose();
  }

  private static RunnerAndConfigurationSettingsImpl promptForSnapshotConfiguration(final Project project,
                                                                                   final List<RunnerAndConfigurationSettingsImpl> configurations) {
    final RunnerAndConfigurationSettingsImpl snapshotConfiguration;
    if (configurations.size() == 0) {
      Messages.showMessageDialog(project, "No Application run configurations defined. Please define a run configuration to use for taking snapshots",
                                 UIDesignerBundle.message("snapshot.title"), Messages.getInformationIcon());
      return null;
    }

    if (configurations.size() == 1) {
      final int rc = Messages.showYesNoDialog(
        project,
        "Taking form snapshots is not currently enabled. Enable taking form snapshots in configuration '" +
        configurations.get(0).getConfiguration().getName() + "'?",
        UIDesignerBundle.message("snapshot.title"),
        Messages.getQuestionIcon());
      if (rc == 1) {
        return null;
      }
      snapshotConfiguration = configurations.get(0);
    }
    else {
      String[] names = new String[configurations.size()];
      for(int i=0; i<configurations.size(); i++) {
        names [i] = configurations.get(i).getConfiguration().getName();
      }
      int rc = Messages.showChooseDialog(
        project,
        "Taking form snapshots is not currently enabled. Please choose the run configuration to use for taking form snapshots:",
        UIDesignerBundle.message("snapshot.title"),
        Messages.getQuestionIcon(),
        names,
        names [0]
      );
      if (rc < 0) return null;
      snapshotConfiguration = configurations.get(rc);
    }
    ((ApplicationConfiguration) snapshotConfiguration.getConfiguration()).ENABLE_SWING_INSPECTOR = true;
    return snapshotConfiguration;
  }

  private static class MyDialog extends DialogWrapper {
    private JPanel myRootPanel;
    private JTree myComponentTree;
    private JTextField myFormNameTextField;
    private final Project myProject;
    private final PsiDirectory myDirectory;

    public MyDialog(Project project, final SnapShotClient client, final PsiDirectory dir) {
      super(project, true);
      myProject = project;
      myDirectory = dir;
      init();
      setTitle(UIDesignerBundle.message("snapshot.title"));
      myComponentTree.setModel(new SnapShotTreeModel(client));
      myComponentTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          updateOKAction();
        }
      });
      myFormNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          updateOKAction();
        }
      });
      updateOKAction();
    }

    private void updateOKAction() {
      setOKActionEnabled(isFormNameValid() && isSelectedComponentValid());
    }

    private boolean isSelectedComponentValid() {
      final TreePath selectionPath = myComponentTree.getSelectionPath();
      if (selectionPath == null) return false;
      SnapShotRemoteComponent rc = (SnapShotRemoteComponent) selectionPath.getLastPathComponent();
      PsiClass componentClass = PsiManager.getInstance(myProject).findClass(rc.getClassName(),
                                                                            GlobalSearchScope.allScope(myProject));
      while(componentClass != null) {
        if (JPanel.class.getName().equals(componentClass.getQualifiedName()) ||
            JTabbedPane.class.getName().equals(componentClass.getQualifiedName()) ||
            JScrollPane.class.getName().equals(componentClass.getQualifiedName()) ||
            JSplitPane.class.getName().equals(componentClass.getQualifiedName())) {
          return true;
        }
        componentClass = componentClass.getSuperClass();
      }

      return false;
    }

    private boolean isFormNameValid() {
      return myFormNameTextField.getText().length() > 0;
    }

    @Override @NonNls
    protected String getDimensionServiceKey() {
      return "CreateSnapShotAction.MyDialog";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myFormNameTextField;
    }

    @Override
    protected Action getOKAction() {
      final Action okAction = super.getOKAction();
      okAction.putValue(Action.NAME, UIDesignerBundle.message("create.snapshot.button"));
      return okAction;
    }

    @Override
    protected void doOKAction() {
      if (getOKAction().isEnabled()) {
        try {
          myDirectory.checkCreateFile(getFormName() + ".form");
        }
        catch (IncorrectOperationException e) {
          JOptionPane.showMessageDialog(myRootPanel, UIDesignerBundle.message("error.form.already.exists", getFormName()));
          return;
        }
        close(OK_EXIT_CODE);
      }
    }

    @Nullable
    protected JComponent createCenterPanel() {
      return myRootPanel;
    }

    public int getSelectedComponentId() {
      SnapShotRemoteComponent rc = (SnapShotRemoteComponent) myComponentTree.getSelectionPath().getLastPathComponent();
      return rc.getId();
    }

    public String getFormName() {
      return myFormNameTextField.getText();
    }
  }
}
