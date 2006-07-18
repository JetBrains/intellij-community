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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponentFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.nio.charset.Charset;

/**
 * @author yole
 */
public class CreateSnapShotAction extends AnAction {
  @NonNls private static final String FORM_EXTENSION = ".form";

  @Override
  public void update(AnActionEvent e) {
    final Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    final IdeView view = (IdeView)e.getDataContext().getData(DataConstants.IDE_VIEW);
    e.getPresentation().setVisible(project != null && view != null && hasDirectoryInPackage(project, view));
  }

  private static boolean hasDirectoryInPackage(final Project project, final IdeView view) {
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    PsiDirectory[] dirs = view.getDirectories();
    for (PsiDirectory dir : dirs) {
      if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && dir.getPackage() != null) {
        return true;
      }
    }
    return false;
  }

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
      int rc = Messages.showYesNoDialog(project, UIDesignerBundle.message("snapshot.run.prompt"),
                                        UIDesignerBundle.message("snapshot.title"), Messages.getQuestionIcon());
      if (rc == 1) return;
      final JavaProgramRunner runner = ExecutionRegistry.getInstance().getDefaultRunner();

      final ApplicationConfiguration appConfig = (ApplicationConfiguration) snapshotConfiguration.getConfiguration();
      appConfig.setSnapShooterNotifyRunnable(new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.prepare.notice"),
                                         UIDesignerBundle.message("snapshot.title"), Messages.getInformationIcon());
              try {
                client.connect(appConfig.getLastSnapShooterPort());
              }
              catch(IOException ex) {
                Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.connection.error"),
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
        Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.run.error", ex.getMessage()),
                                   UIDesignerBundle.message("snapshot.title"), Messages.getErrorIcon());
      }
    }
    else {
      runSnapShooterSession(client, project, dir, view);
    }
  }

  private static void runSnapShooterSession(final SnapShotClient client, final Project project, final PsiDirectory dir, final IdeView view) {
    try {
      client.suspendSwing();
    }
    catch (IOException e1) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.connection.error"),
                                 UIDesignerBundle.message("snapshot.title"), Messages.getInformationIcon());
      return;
    }

    final MyDialog dlg = new MyDialog(project, client, dir);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final int id = dlg.getSelectedComponentId();
      final Ref<Object> result = new Ref<Object>();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            result.set(client.createSnapshot(id));
          }
          catch (Exception ex) {
            result.set(ex);
          }
        }
      }, UIDesignerBundle.message("progress.creating.snapshot"), false, project);

      String snapshot = null;
      if (result.get() instanceof String) {
        snapshot = (String) result.get();
      }
      else {
        Exception ex = (Exception) result.get();
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
                  PsiFile formFile = dir.getManager().getElementFactory().createFileFromText(dlg.getFormName() + FORM_EXTENSION, snapshot1);
                  formFile = (PsiFile)dir.add(formFile);
                  formFile.getVirtualFile().setCharset(Charset.forName("UTF-8"));
                  formFile.getViewProvider().getDocument().setText(snapshot1);
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
    catch (IOException ex) {
      Messages.showErrorDialog(project, UIDesignerBundle.message("snapshot.connection.broken"),
                               UIDesignerBundle.message("snapshot.title"));
    }

    client.dispose();
  }

  private static RunnerAndConfigurationSettingsImpl promptForSnapshotConfiguration(final Project project,
                                                                                   final List<RunnerAndConfigurationSettingsImpl> configurations) {
    final RunnerAndConfigurationSettingsImpl snapshotConfiguration;
    if (configurations.size() == 0) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("snapshot.no.configuration.error"),
                                 UIDesignerBundle.message("snapshot.title"), Messages.getInformationIcon());
      return null;
    }

    if (configurations.size() == 1) {
      final int rc = Messages.showYesNoDialog(
        project,
        UIDesignerBundle.message("snapshot.confirm.configuration.prompt", configurations.get(0).getConfiguration().getName()),
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
        UIDesignerBundle.message("snapshot.choose.configuration.prompt"),
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
    private JLabel myErrorLabel;
    private final Project myProject;
    private final SnapShotClient myClient;
    private final PsiDirectory myDirectory;
    @NonNls private static final String SWING_PACKAGE = "javax.swing.";

    private Icon myUnknownComponentIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/unknown.png");
    private Icon myFormIcon = IconLoader.getIcon("/fileTypes/uiForm.png");

    public MyDialog(Project project, final SnapShotClient client, final PsiDirectory dir) {
      super(project, true);
      myProject = project;
      myClient = client;
      myDirectory = dir;
      init();
      setTitle(UIDesignerBundle.message("snapshot.title"));
      final SnapShotTreeModel model = new SnapShotTreeModel(client);
      myComponentTree.setModel(model);
      myComponentTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      myComponentTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          updateOKAction();
        }
      });
      for(int i=0; i<2; i++) {
        for(int row=myComponentTree.getRowCount()-1; row >= 0; row--) {
          myComponentTree.expandRow(row);
        }
      }
      myComponentTree.getSelectionModel().setSelectionPath(myComponentTree.getPathForRow(0));
      myFormNameTextField.setText(suggestFormName());

      final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      final TextAttributes attributes = globalScheme.getAttributes(HighlighterColors.JAVA_STRING);
      final SimpleTextAttributes titleAttributes =
        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, attributes.getForegroundColor());

      myComponentTree.setCellRenderer(new ColoredTreeCellRenderer() {
        public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
          SnapShotRemoteComponent rc = (SnapShotRemoteComponent) value;

          String className = rc.getClassName();
          if (className.startsWith(SWING_PACKAGE)) {
            append(className.substring(SWING_PACKAGE.length()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }

          if (rc.getText().length() > 0) {
            append(" \"" + rc.getText() + "\"", titleAttributes);
          }
          if (rc.getLayoutManager().length() > 0) {
            append(" (" + rc.getLayoutManager() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }

          if (rc.isTopLevel()) {
            setIcon(myFormIcon);
          }
          else {
            final Palette palette = Palette.getInstance(myProject);
            final ComponentItem item = palette.getItem(rc.getClassName());
            if (item != null) {
              setIcon(item.getSmallIcon());
            }
            else {
              setIcon(myUnknownComponentIcon);
            }
          }
        }
      });
      myFormNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          updateOKAction();
        }
      });
      updateOKAction();
    }

    @NonNls
    private String suggestFormName() {
      int count = 0;
      do {
        count++;
      } while(myDirectory.findFile("Form" + count + ".form") != null);
      return "Form" + count;
    }

    private void updateOKAction() {
      final boolean selectedComponentValid = isSelectedComponentValid();
      setOKActionEnabled(isFormNameValid() && selectedComponentValid);
      if (myComponentTree.getSelectionPath() != null && !selectedComponentValid) {
        myErrorLabel.setText(UIDesignerBundle.message("snapshooter.invalid.container"));
      }
      else {
        myErrorLabel.setText(" ");
      }
    }

    private boolean isSelectedComponentValid() {
      final TreePath selectionPath = myComponentTree.getSelectionPath();
      if (selectionPath == null) return false;
      SnapShotRemoteComponent rc = (SnapShotRemoteComponent) selectionPath.getLastPathComponent();
      if (isValidComponent(rc)) return true;
      if (selectionPath.getPathCount() == 2) {
        // capture frame/dialog root pane when a frame or dialog itself is selected
        final SnapShotRemoteComponent[] children = rc.getChildren();
        return children != null && children.length > 0 && isValidComponent(children[0]);
      }
      return false;
    }

    private boolean isValidComponent(final SnapShotRemoteComponent rc) {
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
          myDirectory.checkCreateFile(getFormName() + FORM_EXTENSION);
        }
        catch (IncorrectOperationException e) {
          JOptionPane.showMessageDialog(myRootPanel, UIDesignerBundle.message("error.form.already.exists", getFormName()));
          return;
        }
        if (!checkUnknownLayoutManagers(myDirectory.getProject())) return;
        close(OK_EXIT_CODE);
      }
    }

    private boolean checkUnknownLayoutManagers(final Project project) {
      final Set<String> layoutManagerClasses = new TreeSet<String>();
      final SnapShotRemoteComponent rc = (SnapShotRemoteComponent) myComponentTree.getSelectionPath().getLastPathComponent();
      assert rc != null;
      final Ref<Exception> err = new Ref<Exception>();
      Runnable runnable = new Runnable() {
        public void run() {
          try {
            collectUnknownLayoutManagerClasses(project, rc, layoutManagerClasses);
          }
          catch (IOException e) {
            err.set(e);
          }
        }
      };
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable,
                                                                             UIDesignerBundle.message("progress.validating.layout.managers"),
                                                                             false, project)) {
        return false;
      }
      if (!err.isNull()) {
        Messages.showErrorDialog(myRootPanel, UIDesignerBundle.message("snapshot.connection.broken"), UIDesignerBundle.message("snapshot.title"));
        return false;
      }
      if (layoutManagerClasses.size() > 0) {
        StringBuilder builder = new StringBuilder(UIDesignerBundle.message("snapshot.unknown.layout.prefix"));
        for(String layoutManagerClass: layoutManagerClasses) {
          builder.append(layoutManagerClass).append("\n");
        }
        builder.append(UIDesignerBundle.message("snapshot.unknown.layout.prompt"));
        return Messages.showYesNoDialog(myProject, builder.toString(),
                                        UIDesignerBundle.message("snapshot.title"), Messages.getQuestionIcon()) == 0;
      }
      return true;
    }

    private void collectUnknownLayoutManagerClasses(final Project project, final SnapShotRemoteComponent rc,
                                                    final Set<String> layoutManagerClasses) throws IOException {
      RadComponentFactory factory = InsertComponentProcessor.getRadComponentFactory(project, rc.getClassName());
      if (factory instanceof RadContainer.Factory && rc.getLayoutManager().length() > 0 &&
          !LayoutManagerRegistry.isKnownLayoutClass(rc.getLayoutManager())) {
        layoutManagerClasses.add(rc.getLayoutManager());
      }

      SnapShotRemoteComponent[] children = rc.getChildren();
      if (children == null) {
        children = myClient.listChildren(rc.getId());
        rc.setChildren(children);
      }
      for(SnapShotRemoteComponent child: children) {
        collectUnknownLayoutManagerClasses(project, child, layoutManagerClasses);
      }
    }

    @Nullable
    protected JComponent createCenterPanel() {
      return myRootPanel;
    }

    public int getSelectedComponentId() {
      final TreePath selectionPath = myComponentTree.getSelectionPath();
      SnapShotRemoteComponent rc = (SnapShotRemoteComponent) selectionPath.getLastPathComponent();
      if (!isValidComponent(rc) && selectionPath.getPathCount() == 2) {
        // capture frame/dialog root pane when a frame or dialog itself is selected
        final SnapShotRemoteComponent[] children = rc.getChildren();
        if (children != null && children.length > 0 && isValidComponent(children [0])) {
          return children [0].getId();
        }
      }
      return rc.getId();
    }

    public String getFormName() {
      return myFormNameTextField.getText();
    }
  }
}
