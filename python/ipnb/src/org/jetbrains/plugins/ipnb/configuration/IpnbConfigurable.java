package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.UI;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;

public class IpnbConfigurable implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance(IpnbConfigurable.class);
  private static final int DEFAULT_PADDING = 3;
  private JPanel myMainPanel;
  private JBTextField myUrlField;
  private TextFieldWithBrowseButton myNotebookDirectoryField;
  private JBLabel myWarningIcon;
  private JPanel mySpecificNotebookSettingsPanel;
  private final Project myProject;

  public IpnbConfigurable(@NotNull Project project) {
    myProject = project;
    
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myNotebookDirectoryField.addBrowseFolderListener("Select Working Directory", null, myProject, fileChooserDescriptor);

    IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    myUrlField.setText(ipnbSettings.getURL());
    myUrlField.addFocusListener(createUrlProtocolValidationListener());

    if (ipnbSettings.getWorkingDirectory().isEmpty()) {
      ipnbSettings.setWorkingDirectory(myProject.getBasePath());
    }
    myNotebookDirectoryField.setText(ipnbSettings.getWorkingDirectory());
  }

  @NotNull
  private FocusAdapter createUrlProtocolValidationListener() {
    return new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        if (mySpecificNotebookSettingsPanel instanceof SettingsPanelPro) {
          boolean isRemote = ((SettingsPanelPro)mySpecificNotebookSettingsPanel).isRemote();
          setWarningLabelIcon(isRemote);
        }
      }
    };
  }

  private void setWarningLabelIcon(boolean isRemote) {
    if (myUrlField == null) return;
    try {
      URL url = new URL(myUrlField.getText());
      String protocol = url.getProtocol();
      boolean isSupportedProtocol = protocol.equals(isRemote ? "https" : "http");
      if (!isSupportedProtocol) {
        Dimension oldPreferredSize = myWarningIcon.getPreferredSize();
        myWarningIcon.setIcon(AllIcons.General.BalloonWarning);
        myWarningIcon.setPreferredSize(oldPreferredSize);
        myWarningIcon.setMinimumSize(oldPreferredSize);
        myWarningIcon.revalidate();
        String message = isRemote ? "Use HTTPS for remote notebooks" : "Use HTTP for local notebooks";
        IdeTooltipManager.getInstance().setCustomTooltip(myWarningIcon, new TooltipWithClickableLinks.ForBrowser(myWarningIcon, message));

        return;
      }
    }
    catch (MalformedURLException e) {
      LOG.warn(e.getMessage());
    }

    myWarningIcon.setIcon(AllIcons.Nodes.EmptyNode);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Jupyter Notebook";
  }

  @Override
  public String getHelpTopic() {
    return "reference-ipnb";
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    final String oldUrl = IpnbSettings.getInstance(myProject).getURL();
    final String oldWorkingDirectory = IpnbSettings.getInstance(myProject).getWorkingDirectory();

    final String url = StringUtil.trimEnd(StringUtil.notNullize(myUrlField.getText()), "/");
    final String workingDirectory = StringUtil.notNullize(myNotebookDirectoryField.getText());

    final boolean isBaseModified = !url.equals(oldUrl) || !workingDirectory.equals(oldWorkingDirectory);
    
    return isBaseModified || ((SettingsPanel)mySpecificNotebookSettingsPanel).isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    final String newNotebookDir = myNotebookDirectoryField.getText();
    ipnbSettings.setWorkingDirectory(newNotebookDir.isEmpty() ? myProject.getBasePath() : newNotebookDir);
    
    String url = StringUtil.trimEnd(StringUtil.notNullize(myUrlField.getText()), "/");
    if (!url.equals(ipnbSettings.getURL())) {
      IpnbConnectionManager.getInstance(myProject).shutdownKernels();
      ipnbSettings.setURL(url);
    }
    
    ((SettingsPanel)mySpecificNotebookSettingsPanel).apply();
  }

  @Override
  public void reset() {
    myUrlField.setText(IpnbSettings.getInstance(myProject).getURL());
    final String workingDirectory = IpnbSettings.getInstance(myProject).getWorkingDirectory();
    myNotebookDirectoryField.setText(workingDirectory.isEmpty() ? myProject.getBasePath() : workingDirectory);
    
    ((SettingsPanel)mySpecificNotebookSettingsPanel).reset();

    setWarningLabelIcon(IpnbSettings.getInstance(myProject).isRemote());
  }

  @NotNull
  @Override
  public String getId() {
    return "IpnbConfigurable";
  }

  private void createUIComponents() {
    // TODO: remove this hack
    int gap = SystemInfo.isWindows ? 4 : SystemInfo.isMac ? 8 : 16;
    mySpecificNotebookSettingsPanel = PlatformUtils.isPyCharmPro() ? new SettingsPanelPro() : new LocalSettingsPanel(true, 0, gap);
  }
  
  private abstract static class SettingsPanel extends JPanel {
    public abstract boolean isModified();
    
    public abstract void apply();
    
    public abstract void reset();
  }
  
  private class SettingsPanelPro extends SettingsPanel {
    private final JBRadioButton myLocal;
    private final JBRadioButton myRemote;
    private final RemoteSettingsPanel myRemoteSettingsPanel;
    private final LocalSettingsPanel myLocalSettingsPanel;

    public SettingsPanelPro() {
      final boolean isRemote = IpnbSettings.getInstance(myProject).isRemote();
      
      myLocal = createModeRadioButton("Local");
      myLocal.addItemListener(createRadioButtonListener(myLocal));

      myRemote = createModeRadioButton("Remote");

      myRemoteSettingsPanel = new RemoteSettingsPanel(isRemote);
      myLocalSettingsPanel = new LocalSettingsPanel(!isRemote, DEFAULT_PADDING, 2);
      
      final ButtonGroup group = new ButtonGroup();
      myLocal.setSelected(!isRemote);
      myRemote.setSelected(isRemote);
      group.add(myLocal);
      group.add(myRemote);

      final JPanel localPanel = new JPanel(new BorderLayout(10, 1));
      localPanel.add(myLocal, BorderLayout.NORTH);
      localPanel.add(myLocalSettingsPanel, BorderLayout.CENTER);

      final JPanel remotePanel = new JPanel(new BorderLayout(10, 1));
      remotePanel.add(myRemote, BorderLayout.NORTH);
      remotePanel.add(myRemoteSettingsPanel, BorderLayout.CENTER);
      
      setLayout(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, 0, 1, true, false));
      add(localPanel);
      add(remotePanel);
      
      setWarningLabelIcon(isRemote);
    }
    
    public boolean isModified() {
      final boolean wasRemote = IpnbSettings.getInstance(myProject).isRemote();

      if (wasRemote != isRemote()) return true;
      
      if (wasRemote) {
        return myRemoteSettingsPanel.isModified();
      }
      else {
        return myLocalSettingsPanel.isModified();
      }
    }

    public boolean isRemote() {
      return myRemote.isSelected();
    }

    @Override
    public void apply() {
      final boolean isRemote = isRemote();
      final boolean wasRemote = IpnbSettings.getInstance(myProject).isRemote();
      
      if (isRemote != wasRemote) {
        IpnbConnectionManager.getInstance(myProject).shutdownKernels();
        IpnbSettings.getInstance(myProject).setRemote(isRemote);
      }
      
      if (isRemote) {
        myRemoteSettingsPanel.apply();
      }
      else {
        myLocalSettingsPanel.apply();
      }
    }

    @Override
    public void reset() {
      final boolean isRemote = IpnbSettings.getInstance(myProject).isRemote();
      myRemote.setSelected(isRemote);
      myLocal.setSelected(!isRemote);
      
      if (isRemote) {
        myRemoteSettingsPanel.reset();
      }
      else {
        myLocalSettingsPanel.reset();
      }
    }

    @NotNull
    private ItemListener createRadioButtonListener(@NotNull JBRadioButton local) {
      return new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (local.isSelected()) {
            myLocalSettingsPanel.enablePanel();
            myRemoteSettingsPanel.disablePanel();
            setWarningLabelIcon(false);
          }
          else {
            myLocalSettingsPanel.disablePanel();
            myRemoteSettingsPanel.enablePanel();
            setWarningLabelIcon(true);
          }
        }
      };
    }
  }

  @NotNull
  private static JBRadioButton createModeRadioButton(@NotNull String text) {
    final JBRadioButton local = new JBRadioButton(text);
    local.setFocusable(false);
    local.setBorder(null);
    return local;
  }
  
  private class LocalSettingsPanel extends SettingsPanel {
    private static final String PARAMETERS_TEXT = "Additional options:";
    private static final String DEFAULT_PARAMETERS_TEXT = "for example: --profile=nbserver";
    private JBTextField myAdditionalOptions;
    private final JLabel myParametersLabel;

    public LocalSettingsPanel(boolean enabled, int leftPadding, int gap) {
      myParametersLabel = new JLabel(PARAMETERS_TEXT);
      myAdditionalOptions = new JBTextField();
      myAdditionalOptions.addFocusListener(createInitialTextFocusAdapter(myAdditionalOptions, DEFAULT_PARAMETERS_TEXT));
      initFields();
      
      setLayout(new MigLayout("insets 0", leftPadding + "unrel[][grow]", "[][]"));
      add(myParametersLabel, "gapafter " + gap);
      add(myAdditionalOptions, "growx");
      if (enabled) {
        enablePanel();
      }
      else {
        disablePanel();
      }
    }
    
    public void enablePanel() {
      myParametersLabel.setEnabled(true);
      myParametersLabel.setForeground(UIUtil.getActiveTextColor());
      myAdditionalOptions.setEnabled(true);
    }
    
    public void disablePanel() {
      myParametersLabel.setEnabled(false);
      myParametersLabel.setForeground(UIUtil.getInactiveTextColor());
      myAdditionalOptions.setEnabled(false);
    }

    @Override
    public void apply() {
      final String parameters = myAdditionalOptions.getText();
      if (!parameters.equals(DEFAULT_PARAMETERS_TEXT)) {
        IpnbSettings.getInstance(myProject).setArguments(parameters);
      }
      else {
        IpnbSettings.getInstance(myProject).setArguments("");
      }
    }

    @Override
    public void reset() {
      initFields();
    }

    private void initFields() {
      final String arguments = IpnbSettings.getInstance(myProject).getArguments();
      setInitialText(myAdditionalOptions, arguments, DEFAULT_PARAMETERS_TEXT);
    }

    public boolean isModified() {
      final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
      final String arguments = ipnbSettings.getArguments();
      final String text = StringUtil.trim(StringUtil.notNullize(myAdditionalOptions.getText()));
      
      return !text.equals(DEFAULT_PARAMETERS_TEXT) && !text.equals(arguments);
    }
  }
   
  private class RemoteSettingsPanel extends SettingsPanel {
    private static final String DEFAULT_USERNAME_TEXT = "Leave empty for a single-user notebook";

    private JBTextField myUsernameField;
    private JBPasswordField myPasswordField;
    private JLabel myInterpreterSetupLinkLabel;
    private JBLabel myPasswordLabel;
    private JBLabel myUsernameLabel;
    private boolean myIsEnabled;


    public RemoteSettingsPanel(boolean enabled) {
      myIsEnabled = enabled;
      myInterpreterSetupLinkLabel = new JLabel("Configure remote interpreter");
      myInterpreterSetupLinkLabel.setForeground(UI.getColor("link.foreground"));
      myInterpreterSetupLinkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
      createNavigateToInterpreterSettingsListener().installOn(myInterpreterSetupLinkLabel);

      int gap = SystemInfo.isMac ? 6 : 5;
      setLayout(new MigLayout("insets 0", DEFAULT_PADDING + "unrel[]" + gap + "unrel[grow]", "[][][]"));
      myUsernameLabel = new JBLabel("Username:");
      myUsernameField = new JBTextField();
      myUsernameField.addFocusListener(createInitialTextFocusAdapter(myUsernameField, DEFAULT_USERNAME_TEXT));
      setInitialText(myUsernameField, IpnbSettings.getInstance(myProject).getUsername(), DEFAULT_USERNAME_TEXT);
      myPasswordLabel = new JBLabel("Password:");
      myPasswordField = new JBPasswordField();
      
      
      add(myUsernameLabel);
      add(myUsernameField, "cell 1 0, growx, wrap");
      add(myPasswordLabel);
      add(myPasswordField, "cell 1 1, growx, wrap");
      add(myInterpreterSetupLinkLabel, "cell 1 2, align right");
      
      if (myIsEnabled) {
        enablePanel();
      }
      else {
        disablePanel();
      }
    }

    @NotNull
    private ClickListener createNavigateToInterpreterSettingsListener() {
      return new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          if (myIsEnabled) {
            final Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(RemoteSettingsPanel.this));
            if (settings != null) {
              settings.select(settings.find(PyActiveSdkModuleConfigurable.class.getName()));
              return true;
            }
          }
          return false;
        }
      };
    }

    public void enablePanel() {
      myIsEnabled = true;
      myUsernameField.setEnabled(true);
      myUsernameLabel.setEnabled(true);
      myUsernameLabel.setForeground(UIUtil.getActiveTextColor());
      if (DEFAULT_USERNAME_TEXT.equals(myUsernameField.getText())) {
        myUsernameField.setForeground(UIUtil.getInactiveTextColor());
      }
      
      myPasswordField.setEnabled(true);
      myPasswordLabel.setEnabled(true);
      myPasswordLabel.setForeground(UIUtil.getActiveTextColor());
      
      myInterpreterSetupLinkLabel.setEnabled(true);
      myInterpreterSetupLinkLabel.setForeground(UI.getColor("link.foreground"));
      myInterpreterSetupLinkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    
    public void disablePanel() {
      myIsEnabled = false;
      myUsernameField.setEnabled(false);
      myUsernameLabel.setEnabled(false);
      myUsernameLabel.setForeground(UIUtil.getInactiveTextColor());
      
      myPasswordField.setEnabled(false);
      myPasswordLabel.setEnabled(false);
      myPasswordLabel.setForeground(UIUtil.getInactiveTextColor());
      
      myInterpreterSetupLinkLabel.setEnabled(false);
      myInterpreterSetupLinkLabel.setForeground(UIUtil.getInactiveTextColor());
      myInterpreterSetupLinkLabel.setCursor(Cursor.getDefaultCursor());
    }

    @Override
    public void apply() {
      final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
      
      final String oldUsername = ipnbSettings.getUsername();
      final String oldPassword = ipnbSettings.getPassword(myProject.getLocationHash());

      final String newUsername = myUsernameField.getText().equals(DEFAULT_USERNAME_TEXT) ? "" : myUsernameField.getText();
      final String newPassword = String.valueOf(myPasswordField.getPassword());
      
      if (!oldUsername.equals(newUsername) || !oldPassword.equals(newPassword)) {
        IpnbConnectionManager.getInstance(myProject).shutdownKernels();
        ipnbSettings.setUsername(newUsername);
        ipnbSettings.setPassword(newPassword, myProject.getLocationHash());
      }
    }

    @Override
    public void reset() {
      final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
      
      final String savedUsername = ipnbSettings.getUsername();
      setInitialText(myUsernameField, savedUsername, DEFAULT_USERNAME_TEXT);

      final String savedPassword = ipnbSettings.getPassword(myProject.getLocationHash());
      myPasswordField.setText(savedPassword);
    }

    public boolean isModified() {
      final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
      final String oldUsername = ipnbSettings.getUsername();
      final String oldPassword = ipnbSettings.getPassword(myProject.getLocationHash());

      final String newPassword = String.valueOf(myPasswordField.getPassword());
      final String newUsername = myUsernameField.getText();
      
      return !oldPassword.equals(newPassword) || !oldUsername.equals(newUsername);
    }
  }

  @NotNull
  private static FocusAdapter createInitialTextFocusAdapter(@NotNull JBTextField field, @NotNull String initialText) {
    return new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (field.getText().equals(initialText)) {
          field.setForeground(UIUtil.getActiveTextColor());
          field.setText("");
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (field.getText().isEmpty()) {
          field.setForeground(UIUtil.getInactiveTextColor());
          field.setText(initialText);
        }
      }
    };
  }

  private static void setInitialText(@NotNull JBTextField field,
                                     @NotNull String savedValue,
                                     @NotNull String defaultText) {
    if (savedValue.isEmpty()) {
      field.setForeground(UIUtil.getInactiveTextColor());
      field.setText(defaultText);
    }
    else {
      field.setForeground(UIUtil.getActiveTextColor());
      field.setText(savedValue);
    }
  }
}

