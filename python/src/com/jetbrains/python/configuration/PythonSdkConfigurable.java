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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
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

    mySdkComboBox.setRenderer(new ColoredListCellRenderer() {

      protected void customizeCellRenderer(final JList list,
                                           final Object value,
                                           final int index,
                                           final boolean selected,
                                           final boolean hasFocus) {
        Sdk sdk = (Sdk) value;
        if (sdk != null) {
          append(sdk.getName() + " (" + FileUtil.toSystemDependentName(sdk.getHomePath()) + ")", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });

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
    List<Sdk> pythonSdks = new ArrayList<Sdk>();
    final Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
    for(Sdk sdk: sdks) {
      if (sdk.getSdkType() instanceof PythonSdkType) {
        pythonSdks.add(sdk);
      }
    }

    mySdkComboBox.setModel(new MyComboBoxModel(pythonSdks, myProjectRootManager.getProjectJdk()));
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

  private static class MyComboBoxModel extends AbstractListModel implements ComboBoxModel {
    private List myItems;
    private Object mySelection;

    private MyComboBoxModel(final List items, final Object selection) {
      myItems = items;
      mySelection = selection;
    }

    public int getSize() {
      return myItems.size();
    }

    public Object getElementAt(final int index) {
      return myItems.get(index);
    }

    public void setSelectedItem(final Object anItem) {
      mySelection = anItem;
    }

    public Object getSelectedItem() {
      return mySelection;
    }
  }
}