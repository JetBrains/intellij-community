package com.jetbrains.python.sdk;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.LocationNameFieldsBinding;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.PathUtil;
import com.jetbrains.python.packaging.PyPackageService;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.ui.IdeaDialog;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CreateVirtualEnvDialog extends IdeaDialog {
  private JPanel myMainPanel;
  private JComboBox mySdkCombo;
  private TextFieldWithBrowseButton myDestination;
  private JTextField myName;
  private JBCheckBox mySitePackagesCheckBox;
  private JBCheckBox myAssociateCheckbox;
  private Project myProject;
  private String myInitialPath;

  public CreateVirtualEnvDialog(Sdk sdk, Project project, boolean isNewProject, final List<Sdk> allSdks) {
    super(project);
    myProject = project;
    init();
    setTitle("Create Virtual Environment");
    updateSdkList(sdk, allSdks);

    myAssociateCheckbox.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    if (project.isDefault()) {
      myAssociateCheckbox.setSelected(false);
      myAssociateCheckbox.setVisible(false);
    }
    else if (isNewProject) {
      myAssociateCheckbox.setText("Associate this virtual environment with the project being created");
    }

    setOKActionEnabled(false);

    myInitialPath = "";
    
    final VirtualFile file = VirtualEnvSdkFlavor.getDefaultLocation();

    if (file != null)
      myInitialPath = file.getPath();
    else {
      final String savedPath = PyPackageService.getInstance().getVirtualEnvBasePath();
      if (!StringUtil.isEmptyOrSpaces(savedPath))
        myInitialPath = savedPath;
      else {
        final VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null)
          myInitialPath = baseDir.getPath();
      }
    }

    addUpdater(myName);
    new LocationNameFieldsBinding(project, myDestination, myName, myInitialPath, "Select Location for Virtual Environment");

    registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
      }

      public void validate() {
        checkValid();
      }
    });
    myMainPanel.setPreferredSize(new Dimension(300, 50));
    checkValid();
  }

  private void checkValid() {
    final String projectName = myName.getText();
    if (new File(getDestination()).exists()) {
      setOKActionEnabled(false);
      setErrorText("Directory already exists");
      return;
    }
    if (StringUtil.isEmptyOrSpaces(projectName)) {
      setOKActionEnabled(false);
      setErrorText("VirtualEnv name can't be empty");
      return;
    }
    if (!PathUtil.isValidFileName(projectName)) {
      setOKActionEnabled(false);
      setErrorText("Invalid directory name");
      return;
    }
    if (mySdkCombo.getSelectedItem() == null) {
      setOKActionEnabled(false);
      setErrorText("Select base interpreter");
      return;
    }
    if (StringUtil.isEmptyOrSpaces(myDestination.getText())) {
      setOKActionEnabled(false);
      setErrorText("Destination directory can't be empty");
      return;
    }

    setOKActionEnabled(true);
    setErrorText(null);
  }

  private void registerValidators(final FacetValidatorsManager validatorsManager) {
    myDestination.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validatorsManager.validate();
      }
    });

    mySdkCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        validatorsManager.validate();
      }
    });

    myDestination.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        validatorsManager.validate();
      }
    });
    myName.addCaretListener(new CaretListener() {
      @Override
      public void caretUpdate(CaretEvent event) {
        validatorsManager.validate();
      }
    });

    myDestination.getTextField().addCaretListener(new CaretListener() {
      @Override
      public void caretUpdate(CaretEvent event) {
        validatorsManager.validate();
      }
    });
  }

  private void updateSdkList(Sdk sdk, final List<Sdk> allSdks) {
    final HtmlListCellRenderer<Sdk> sdkListCellRenderer = new PySdkListCellRenderer(mySdkCombo.getRenderer(), null);
    mySdkCombo.setRenderer(sdkListCellRenderer);
    List<Sdk> baseSdks = new ArrayList<Sdk>();
    for (Sdk s : allSdks) {
      if (!PythonSdkType.isInvalid(s) && !PythonSdkType.isVirtualEnv(s) && !PythonRemoteSdkAdditionalData.isRemoteSdk(s.getHomePath())) {
        baseSdks.add(s);
      }
      else if (s.equals(sdk)){
        sdk = null;
      }
    }

    mySdkCombo.setModel(new CollectionComboBoxModel(baseSdks, sdk));
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getDestination() {
    return myDestination.getText();
  }

  public String getName() {
    return myName.getText();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    VirtualFile baseDir = myProject.getBaseDir();
    if (!myDestination.getText().startsWith(myInitialPath) &&
        (baseDir == null || !myDestination.getText().startsWith(baseDir.getPath()))) {
      String path = myDestination.getText();
      PyPackageService.getInstance().setVirtualEnvBasePath(!path.contains(File.separator) ?
                                                                    path : path.substring(0, path.lastIndexOf(File.separator)));
    }
  }

  public Sdk getSdk() {
    return (Sdk)mySdkCombo.getSelectedItem();
  }

  public boolean useGlobalSitePackages() {
    return mySitePackagesCheckBox.isSelected();
  }

  public boolean associateWithProject() {
    return myAssociateCheckbox.isSelected();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myName;
  }
}
