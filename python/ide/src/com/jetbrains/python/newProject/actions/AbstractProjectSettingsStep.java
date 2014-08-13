package com.jetbrains.python.newProject.actions;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonSdkChooserCombo;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.configuration.VirtualEnvProjectFilter;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PyPySdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

abstract public class AbstractProjectSettingsStep extends AbstractActionWithPanel implements DumbAware {
  protected final DirectoryProjectGenerator myProjectGenerator;
  private final NullableConsumer<AbstractProjectSettingsStep> myCallback;
  private PythonSdkChooserCombo mySdkCombo;
  private boolean myInstallFramework;
  private TextFieldWithBrowseButton myLocationField;
  protected final File myProjectDirectory;
  private Button myCreateButton;
  private JLabel myErrorLabel;
  private AnAction myCreateAction;
  private Sdk mySdk;

  public AbstractProjectSettingsStep(DirectoryProjectGenerator projectGenerator, NullableConsumer<AbstractProjectSettingsStep> callback) {
    super();
    myProjectGenerator = projectGenerator;
    myCallback = callback;
    myProjectDirectory = FileUtil.findSequentNonexistentFile(new File(ProjectUtil.getBaseDir()), "untitled", "");
    if (myProjectGenerator instanceof WebProjectTemplate) {
      ((WebProjectTemplate)myProjectGenerator).getPeer().addSettingsStateListener(new WebProjectGenerator.SettingsStateListener() {
        @Override
        public void stateChanged(boolean validSettings) {
          checkValid();
        }
      });
    }
    else if (myProjectGenerator instanceof PythonProjectGenerator) {
      ((PythonProjectGenerator)myProjectGenerator).addSettingsStateListener(new PythonProjectGenerator.SettingsListener() {
        @Override
        public void stateChanged() {
          checkValid();
        }
      });
    }

    myCreateAction = new AnAction("Create", "Create Project", getIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        boolean isValid = checkValid();
        if (isValid && myCallback != null)
          myCallback.consume(AbstractProjectSettingsStep.this);
      }
    };
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  @Override
  public JPanel createPanel() {
    final JPanel basePanel = createBasePanel();
    final JPanel mainPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        myLocationField.requestFocus();
      }
    };

    final JPanel scrollPanel = new JPanel(new BorderLayout());

    final DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    final int height = generators.length == 0 ? 150 : 400;
    mainPanel.setPreferredSize(new Dimension(mainPanel.getPreferredSize().width, height));
    myErrorLabel = new JLabel("");
    myErrorLabel.setForeground(JBColor.RED);
    myCreateButton = new Button(myCreateAction, myCreateAction.getTemplatePresentation());

    scrollPanel.add(basePanel, BorderLayout.NORTH);
    final JPanel advancedSettings = createAdvancedSettings();
    if (advancedSettings != null) {
      scrollPanel.add(advancedSettings, BorderLayout.CENTER);
    }
    final JBScrollPane scrollPane = new JBScrollPane(scrollPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    mainPanel.add(scrollPane, BorderLayout.CENTER);

    final JPanel bottomPanel = new JPanel(new BorderLayout());

    bottomPanel.add(myErrorLabel, BorderLayout.NORTH);
    bottomPanel.add(myCreateButton, BorderLayout.EAST);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    return mainPanel;
  }

  protected Icon getIcon() {
    return myProjectGenerator.getLogo();
  }

  private JPanel createBasePanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.weightx = 0;
    c.insets = new Insets(2, 2, 2, 2);
    myLocationField = new TextFieldWithBrowseButton();
    myLocationField.setText(myProjectDirectory.toString());

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myLocationField.addBrowseFolderListener("Select base directory", "Select base directory for the Project",
                                            null, descriptor);
    myLocationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myProjectGenerator instanceof PythonProjectGenerator) {
          String path = myLocationField.getText().trim();
          if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - File.separator.length());
          }
          int ind = path.lastIndexOf(File.separator);
          if (ind != -1) {
            String projectName = path.substring(ind + 1, path.length());
            ((PythonProjectGenerator)myProjectGenerator).locationChanged(projectName);
          }
        }
      }
    });
    final JLabel locationLabel = new JLabel("Location:");
    c.gridx = 0;
    c.gridy = 0;
    panel.add(locationLabel, c);

    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 1.;
    panel.add(myLocationField, c);

    final JLabel interpreterLabel = new JLabel("Interpreter:", SwingConstants.LEFT) {
      @Override
      public Dimension getMinimumSize() {
        return new JLabel("Project name:").getPreferredSize();
      }

      @Override
      public Dimension getPreferredSize() {
        return getMinimumSize();
      }
    };
    c.gridx = 0;
    c.gridy = 1;
    c.weightx = 0;
    panel.add(interpreterLabel, c);

    final Project project = ProjectManager.getInstance().getDefaultProject();
    final List<Sdk> sdks = PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks();
    VirtualEnvProjectFilter.removeAllAssociated(sdks);
    Sdk compatibleSdk = sdks.isEmpty() ? null : sdks.iterator().next();
    DirectoryProjectGenerator generator = getProjectGenerator();
    if (generator instanceof PyFrameworkProjectGenerator && !((PyFrameworkProjectGenerator)generator).supportsPython3()) {
      if (compatibleSdk != null && PythonSdkType.getLanguageLevelForSdk(compatibleSdk).isPy3K()) {
        Sdk python2Sdk = PythonSdkType.findPython2Sdk(sdks);
        if (python2Sdk != null) {
          compatibleSdk = python2Sdk;

        }
      }
    }

    final Sdk preferred = compatibleSdk;
    mySdkCombo = new PythonSdkChooserCombo(project, sdks, new Condition<Sdk>() {
      @Override
      public boolean value(Sdk sdk) {
        return sdk == preferred;
      }
    });
    mySdkCombo.setButtonIcon(PythonIcons.Python.InterpreterGear);

    c.gridx = 1;
    c.gridy = 1;
    c.weightx = 1.;
    panel.add(mySdkCombo, c);
    final JPanel basePanelExtension = extendBasePanel();
    if (basePanelExtension != null) {
      c.gridwidth = 2;
      c.gridy = 2;
      c.gridx = 0;
      panel.add(basePanelExtension, c);
    }
    registerValidators();
    return panel;
  }

  @Nullable
  protected JPanel extendBasePanel() {
    if (myProjectGenerator instanceof PythonProjectGenerator)
      return ((PythonProjectGenerator)myProjectGenerator).extendBasePanel();
    return null;
  }

  protected void registerValidators() {
    myLocationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        checkValid();
      }
    });
    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        checkValid();
      }
    };
    mySdkCombo.getComboBox().addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        checkValid();
      }
    });
    myLocationField.getTextField().addActionListener(listener);
    mySdkCombo.getComboBox().addActionListener(listener);
    mySdkCombo.addActionListener(listener);
  }

  public boolean checkValid() {
    final String projectName = myLocationField.getText();
    setErrorText(null);
    myInstallFramework = false;

    if (projectName.trim().isEmpty()) {
      setErrorText("Project name can't be empty");
      return false;
    }
    if (myLocationField.getText().indexOf('$') >= 0) {
      setErrorText("Project directory name must not contain the $ character");
      return false;
    }
    if (myProjectGenerator != null) {
      final String baseDirPath = myLocationField.getTextField().getText();
      ValidationResult validationResult = myProjectGenerator.validate(baseDirPath);
      if (!validationResult.isOk()) {
        setErrorText(validationResult.getErrorMessage());
        return false;
      }
      if (myProjectGenerator instanceof PythonProjectGenerator) {
        final ValidationResult warningResult = ((PythonProjectGenerator)myProjectGenerator).warningValidation(getSdk());
        if (!warningResult.isOk()) {
          setWarningText(warningResult.getErrorMessage());
        }
      }
      if (myProjectGenerator instanceof WebProjectTemplate) {
        final WebProjectGenerator.GeneratorPeer peer = ((WebProjectTemplate)myProjectGenerator).getPeer();
        final ValidationInfo validationInfo = peer.validate();
        if (validationInfo != null && !peer.isBackgroundJobRunning()) {
          setErrorText(validationInfo.message);
          return false;
        }
      }
    }

    final Sdk sdk = getSdk();

    final boolean isPy3k = sdk != null && PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K();
    if (sdk != null && PythonSdkType.isRemote(sdk) && !acceptsRemoteSdk(myProjectGenerator)) {
      setErrorText("Please choose a local interpreter");
      return false;
    }
    else if (myProjectGenerator instanceof PyFrameworkProjectGenerator) {
      PyFrameworkProjectGenerator frameworkProjectGenerator = (PyFrameworkProjectGenerator)myProjectGenerator;
      String frameworkName = frameworkProjectGenerator.getFrameworkTitle();
      if (sdk != null && !isFrameworkInstalled(sdk)) {
        final PyPackageManagerImpl packageManager = (PyPackageManagerImpl)PyPackageManager.getInstance(sdk);
        final boolean onlyWithCache =
          PythonSdkFlavor.getFlavor(sdk) instanceof JythonSdkFlavor || PythonSdkFlavor.getFlavor(sdk) instanceof PyPySdkFlavor;
        String warningText = frameworkName + " will be installed on selected interpreter";
        try {
          if (onlyWithCache && packageManager.cacheIsNotNull() || !onlyWithCache) {
            final PyPackage pip = packageManager.findInstalledPackage("pip");
            myInstallFramework = true;
            if (pip == null) {
              warningText = "pip and " + warningText;
            }
            setWarningText(warningText);
          }
        }
        catch (PyExternalProcessException ignored) {
          myInstallFramework = true;
          warningText = "pip and " + warningText;
          setWarningText(warningText);
        }
        if (!myInstallFramework) {
          setErrorText("No " + frameworkName + " support installed in selected interpreter");
          return false;
        }
      }
      if (isPy3k && !((PyFrameworkProjectGenerator)myProjectGenerator).supportsPython3()) {
        setErrorText(frameworkName + " is not supported for the selected interpreter");
        return false;
      }
    }
    if (sdk == null) {
      setErrorText("No Python interpreter selected");
      return false;
    }
    return true;
  }

  public void setErrorText(@Nullable String text) {
    myErrorLabel.setText(text);
    myErrorLabel.setForeground(MessageType.ERROR.getTitleForeground());
    myErrorLabel.setIcon(text == null ? null : AllIcons.Actions.Lightning);
    myCreateButton.setEnabled(text == null);
  }

  public void setWarningText(@Nullable String text) {
    myErrorLabel.setText("Note: " + text + "  ");
    myErrorLabel.setForeground(MessageType.WARNING.getTitleForeground());
  }

  public void selectCompatiblePython() {
    //DirectoryProjectGenerator generator = getProjectGenerator();
    //if (generator instanceof PyFrameworkProjectGenerator && !((PyFrameworkProjectGenerator)generator).supportsPython3()) {
    //  Sdk sdk = getSdk();
    //  if (sdk != null && PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K()) {
    //    Sdk python2Sdk = PythonSdkType.findPython2Sdk(null);
    //    if (python2Sdk != null) {
    //      mySdkCombo.getComboBox().setSelectedItem(python2Sdk);
    //      mySdkCombo.getComboBox().revalidate();
    //      mySdkCombo.getComboBox().repaint();
    //
    //    }
    //  }
    //}
  }

  private static boolean acceptsRemoteSdk(DirectoryProjectGenerator generator) {
    if (generator instanceof PyFrameworkProjectGenerator) {
      return ((PyFrameworkProjectGenerator)generator).acceptsRemoteSdk();
    }
    return true;
  }

  private boolean isFrameworkInstalled(Sdk sdk) {
    PyFrameworkProjectGenerator projectGenerator = (PyFrameworkProjectGenerator)getProjectGenerator();
    return projectGenerator != null && projectGenerator.isFrameworkInstalled(sdk);
  }

  @Nullable
  protected JPanel createAdvancedSettings() {
    return null;
  }

  public DirectoryProjectGenerator getProjectGenerator() {
    return myProjectGenerator;
  }

  private static class Button extends ActionButtonWithText {
    private final Border myBorder;

    public Button(AnAction action, Presentation presentation) {
      super(action, presentation, "NewProject", new Dimension(70, 50));
      final Border border = new LineBorder(JBColor.border(), 1, true);
      myBorder = UIUtil.isUnderDarcula() ? UIUtil.getButtonBorder() : border;
      setBorder(myBorder);
    }

    @Override
    protected int iconTextSpace() {
      return 8;
    }

    @Override
    public boolean isFocusable() {
      return true;
    }

    @Override
    protected void processFocusEvent(FocusEvent e) {
      super.processFocusEvent(e);
      if (e.getID() == FocusEvent.FOCUS_GAINED) {
        processMouseEvent(new MouseEvent(this, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 0, 0, 0, false));

      }
      else if (e.getID() == FocusEvent.FOCUS_LOST) {
        processMouseEvent(new MouseEvent(this, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, 0, 0, 0, false));
      }
    }

    @Override
    public Insets getInsets() {
      return new Insets(5,10,5,5);
    }

    @Override
    protected int horizontalTextAlignment() {
      return SwingConstants.LEFT;
    }

    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (e.getID() == MouseEvent.MOUSE_ENTERED) {
        setBorder(null);
      }
      else if (e.getID() == MouseEvent.MOUSE_EXITED) {
        setBorder(myBorder);
      }
    }
  }

  public Sdk getSdk() {
    if (mySdk != null) return mySdk;
    return (Sdk)mySdkCombo.getComboBox().getSelectedItem();
  }

  public void setSdk(final Sdk sdk) {
    mySdk = sdk;
  }

  public String getProjectLocation() {
    return myLocationField.getText();
  }

  public boolean installFramework() {
    return myInstallFramework;
  }

}
