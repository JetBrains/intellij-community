/*
 * User: anna
 * Date: 18-Feb-2008
 */
package com.jetbrains.python.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class PythonSdkConfigurable implements Configurable {
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");
  private ProjectRootManager myProjectRootManager;
  private Project myProject;
  private JPanel myPanel;
  private JComboBox mySdkComboBox;
  private JButton myAddButton;

  public PythonSdkConfigurable(final ProjectRootManager projectRootManager, final Project project) {
    myProjectRootManager = projectRootManager;
    myProject = project;

    mySdkComboBox.setRenderer(new PythonSdkCellRenderer());

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        addSdk();
      }
    });
  }

  public String getDisplayName() {
    return "Project Structure";
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !Comparing.equal(myProjectRootManager.getProjectJdk(), mySdkComboBox.getSelectedItem());
  }

  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
          myProjectRootManager.setProjectJdk((Sdk) mySdkComboBox.getSelectedItem());
          final ModifiableRootModel model =
              ModuleRootManager.getInstance(ModuleManager.getInstance(myProject).getModules()[0]).getModifiableModel();
          model.inheritSdk();
          model.commit();
      }
    });
  }

  public void reset() {
    refreshSdkList();
  }

  private void refreshSdkList() {
    List<Sdk> pythonSdks = PythonSdkType.getAllSdks();
    mySdkComboBox.setModel(new CollectionComboBoxModel(pythonSdks, myProjectRootManager.getProjectJdk()));
  }

  private void addSdk() {
    final PythonSdkType sdkType = PythonSdkType.getInstance();
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public void validateSelectedFiles(final VirtualFile[] files) throws Exception {
        final String path = files[0].getPath();
        if (files.length > 0 && !sdkType.isValidSdkHome(path)) {
          throw new Exception(FileUtil.toSystemDependentName(path) + " is not a valid Python SDK home");
        }
      }
    };
    final FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, myProject);
    String suggestedPath = sdkType.suggestHomePath();
    VirtualFile suggestedDir = suggestedPath == null
                               ? null
                               :  LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
    final VirtualFile[] selection = dialog.choose(suggestedDir, myProject);
    if (selection.length > 0) {
      setupSdk(selection [0]);
    }
  }

  private void setupSdk(final VirtualFile homeDir) {
    final PythonSdkType pythonSdkType = PythonSdkType.getInstance();
    Sdk sdk = ApplicationManager.getApplication().runWriteAction(new Computable<Sdk>() {
        public Sdk compute(){
          final ProjectJdkImpl projectJdk = new ProjectJdkImpl(pythonSdkType.suggestSdkName(null, homeDir.getPath()), pythonSdkType);
          projectJdk.setHomePath(homeDir.getPath());
          pythonSdkType.setupSdkPaths(projectJdk);
          ProjectJdkTable.getInstance().addJdk(projectJdk);
          return projectJdk;
        }
    });
    refreshSdkList();
    mySdkComboBox.setSelectedItem(sdk);
  }

  public void disposeUIResources() {

  }

}