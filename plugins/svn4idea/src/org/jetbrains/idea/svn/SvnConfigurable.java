/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.jetbrains.idea.svn;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpProxyConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;
import org.jetbrains.idea.svn.config.SvnConfigureProxiesDialog;
import org.jetbrains.idea.svn.dialogs.SshSettingsPanel;
import org.jetbrains.idea.svn.svnkit.SvnKitManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;

public class SvnConfigurable implements Configurable {

  public static final String DISPLAY_NAME = SvnVcs.VCS_DISPLAY_NAME;

  private final Project myProject;
  private JCheckBox myUseDefaultCheckBox;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JCheckBox myUseCommonProxy;
  private JButton myEditProxiesButton;
  private JPanel myComponent;

  private JLabel myConfigurationDirectoryLabel;
  private JCheckBox myLockOnDemand;
  private JCheckBox myCheckNestedInQuickMerge;
  private JCheckBox myDetectNestedWorkingCopiesCheckBox;
  private JCheckBox myIgnoreWhitespaceDifferenciesInCheckBox;
  private JCheckBox myShowMergeSourceInAnnotate;
  private JBCheckBox myWithCommandLineClient;
  private JBCheckBox myRunUnderTerminal;
  private JSpinner myNumRevsInAnnotations;
  private JCheckBox myMaximumNumberOfRevisionsCheckBox;
  private JSpinner mySSHConnectionTimeout;
  private JSpinner mySSHReadTimeout;
  private TextFieldWithBrowseButton myCommandLineClient;
  private JPanel myCommandLineClientOptions;
  private JSpinner myHttpTimeout;
  private JBRadioButton mySSLv3RadioButton;
  private JBRadioButton myTLSv1RadioButton;
  private JBRadioButton myAllRadioButton;
  private JLabel mySSLExplicitly;
  private SshSettingsPanel mySshSettingsPanel;
  private LinkLabel<Object> myNavigateToCommonProxyLink;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;

    myWithCommandLineClient.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        enableCommandLineClientOptions();
      }
    });
    enableCommandLineClientOptions();
    myUseDefaultCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        boolean enabled = !myUseDefaultCheckBox.isSelected();
        myConfigurationDirectoryText.setEnabled(enabled);
        myConfigurationDirectoryText.setEditable(enabled);
        myConfigurationDirectoryLabel.setEnabled(enabled);
        SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
        String path = configuration.getConfigurationDirectory();
        if (!enabled || path == null) {
          myConfigurationDirectoryText.setText(IdeaSubversionConfigurationDirectory.getPath());
        }
        else {
          myConfigurationDirectoryText.setText(path);
        }
      }
    });
    myCommandLineClient.addBrowseFolderListener("Subversion", "Select path to Subversion executable (1.7+)", project,
                                                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());

    myClearAuthButton.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        SvnAuthenticationNotifier.clearAuthenticationCache(myProject, myComponent, myConfigurationDirectoryText.getText());
      }
    });



    myConfigurationDirectoryText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        @NonNls String path = myConfigurationDirectoryText.getText().trim();
        selectConfigurationDirectory(path, new Consumer<String>() {
          @Override
          public void consume(String s) {
            myConfigurationDirectoryText.setText(s);
          }
        }, myProject, myComponent);
      }
    });

    myConfigurationDirectoryLabel.setLabelFor(myConfigurationDirectoryText);

    myUseCommonProxy.setText(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().getProductName()));
    myNavigateToCommonProxyLink.setListener(new LinkListener<Object>() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myComponent));

        if (settings != null) {
          settings.select(settings.find(HttpProxyConfigurable.class));
        }
      }
    }, null);
    myEditProxiesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final SvnConfigureProxiesDialog dialog = new SvnConfigureProxiesDialog(myProject);
        dialog.show();
        myHttpTimeout.setValue(Long.valueOf(SvnConfiguration.getInstance(myProject).getHttpTimeout() / 1000));
      }
    });

    myMaximumNumberOfRevisionsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
      }
    });
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());

    final ButtonGroup bg = new ButtonGroup();
    bg.add(mySSLv3RadioButton);
    bg.add(myTLSv1RadioButton);
    bg.add(myAllRadioButton);
    if (SvnKitManager.isSSLProtocolExplicitlySet()) {
      mySSLv3RadioButton.setEnabled(false);
      myTLSv1RadioButton.setEnabled(false);
      myAllRadioButton.setEnabled(false);
      mySSLExplicitly.setVisible(true);
      mySSLExplicitly.setText("Set explicitly to: " + SvnKitManager.getExplicitlySetSslProtocols());
    } else {
      mySSLv3RadioButton.setEnabled(true);
      myTLSv1RadioButton.setEnabled(true);
      myAllRadioButton.setEnabled(true);
      mySSLExplicitly.setVisible(false);
      final String version = SystemInfo.JAVA_RUNTIME_VERSION;
      final boolean jdkBugFixed = version.startsWith("1.7") || version.startsWith("1.8");
      if (! jdkBugFixed) {
        mySSLExplicitly.setVisible(true);
        mySSLExplicitly.setText("Setting 'All' value in this JDK version (" + version + ") is not recommended.");
      }
    }

    mySshSettingsPanel.load(SvnConfiguration.getInstance(myProject));
  }

  public void enableCommandLineClientOptions() {
    UIUtil.setEnabled(myCommandLineClientOptions, myWithCommandLineClient.isSelected(), true);
  }

  public static void selectConfigurationDirectory(@NotNull String path,
                                                  @NotNull final Consumer<String> dirConsumer,
                                                  final Project project,
                                                  @Nullable final Component component) {
    FileChooserDescriptor descriptor =  FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(SvnBundle.message("dialog.title.select.configuration.directory"))
      .withDescription(SvnBundle.message("dialog.description.select.configuration.directory"))
      .withShowFileSystemRoots(true)
      .withHideIgnored(false)
      .withShowHiddenFiles(true);

    path = "file://" + path.replace(File.separatorChar, '/');
    VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);

    VirtualFile file = FileChooser.chooseFile(descriptor, component, project, root);
    if (file == null) {
      return;
    }
    final String resultPath = file.getPath().replace('/', File.separatorChar);
    dirConsumer.consume(resultPath);
  }

  public JComponent createComponent() {
    return myComponent;
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  private SvnConfiguration.SSLProtocols getSelectedSSL() {
    if (myAllRadioButton.isSelected()) return SvnConfiguration.SSLProtocols.all;
    if (mySSLv3RadioButton.isSelected()) return SvnConfiguration.SSLProtocols.sslv3;
    if (myTLSv1RadioButton.isSelected()) return SvnConfiguration.SSLProtocols.tlsv1;
    throw new IllegalStateException();
  }

  public boolean isModified() {
    if (myComponent == null) {
      return false;
    }
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    if (configuration.isUseDefaultConfiguation() != myUseDefaultCheckBox.isSelected()) {
      return true;
    }
    if (configuration.isIsUseDefaultProxy() != myUseCommonProxy.isSelected()) {
      return true;
    }
    if (configuration.isUpdateLockOnDemand() != myLockOnDemand.isSelected()) {
      return true;
    }
    if (configuration.isCheckNestedForQuickMerge() != myCheckNestedInQuickMerge.isSelected()) {
      return true;
    }
    if (configuration.isIgnoreSpacesInAnnotate() != myIgnoreWhitespaceDifferenciesInCheckBox.isSelected()) {
      return true;
    }
    if (configuration.isShowMergeSourcesInAnnotate() != myShowMergeSourceInAnnotate.isSelected()) {
      return true;
    }
    if (! configuration.getUseAcceleration().equals(acceleration())) return true;
    if (configuration.isRunUnderTerminal() != myRunUnderTerminal.isSelected()) return true;
    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    final boolean useMaxInAnnot = annotateRevisions != -1;
    if (useMaxInAnnot != myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      return true;
    }
    if (myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      if (annotateRevisions != ((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue()) {
        return true;
      }
    }
    if (configuration.getSshConnectionTimeout() /1000 != ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.getSshReadTimeout() /1000 != ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.getHttpTimeout()/1000 != ((SpinnerNumberModel) myHttpTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (! getSelectedSSL().equals(configuration.getSslProtocols())) return true;
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    if (! Comparing.equal(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim())) return true;
    if (!configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim())) return true;
    return mySshSettingsPanel.isModified(configuration);
  }

  private SvnConfiguration.UseAcceleration acceleration() {
    if (myWithCommandLineClient.isSelected()) return SvnConfiguration.UseAcceleration.commandLine;
    return SvnConfiguration.UseAcceleration.nothing;
  }

  public void apply() throws ConfigurationException {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    configuration.setConfigurationDirParameters(myUseDefaultCheckBox.isSelected(), myConfigurationDirectoryText.getText());

    configuration.setIsUseDefaultProxy(myUseCommonProxy.isSelected());
    final SvnVcs vcs17 = SvnVcs.getInstance(myProject);
    configuration.setCheckNestedForQuickMerge(myCheckNestedInQuickMerge.isSelected());
    configuration.setUpdateLockOnDemand(myLockOnDemand.isSelected());
    configuration.setIgnoreSpacesInAnnotate(myIgnoreWhitespaceDifferenciesInCheckBox.isSelected());
    configuration.setShowMergeSourcesInAnnotate(myShowMergeSourceInAnnotate.isSelected());
    if (! myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      configuration.setMaxAnnotateRevisions(-1);
    } else {
      configuration.setMaxAnnotateRevisions(((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue());
    }
    configuration.setSshConnectionTimeout(((SpinnerNumberModel)mySSHConnectionTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setSshReadTimeout(((SpinnerNumberModel)mySSHReadTimeout.getModel()).getNumber().longValue() * 1000);

    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    boolean reloadWorkingCopies = !acceleration().equals(configuration.getUseAcceleration()) ||
                                  !StringUtil.equals(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim());
    configuration.setUseAcceleration(acceleration());
    configuration.setRunUnderTerminal(myRunUnderTerminal.isSelected());
    configuration.setSslProtocols(getSelectedSSL());
    SvnVcs.getInstance(myProject).getSvnKitManager().refreshSSLProperty();

    applicationSettings17.setCommandLinePath(myCommandLineClient.getText().trim());
    boolean isClientValid = vcs17.checkCommandLineVersion();
    if (isClientValid && reloadWorkingCopies) {
      vcs17.invokeRefreshSvnRoots();
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
    configuration.setHttpTimeout(((SpinnerNumberModel) myHttpTimeout.getModel()).getNumber().longValue() * 1000);

    mySshSettingsPanel.apply(configuration);
  }

  public void reset() {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    String path = configuration.getConfigurationDirectory();
    if (configuration.isUseDefaultConfiguation() || path == null) {
      path = IdeaSubversionConfigurationDirectory.getPath();
    }
    myConfigurationDirectoryText.setText(path);
    myUseDefaultCheckBox.setSelected(configuration.isUseDefaultConfiguation());
    myUseCommonProxy.setSelected(configuration.isIsUseDefaultProxy());
    myCheckNestedInQuickMerge.setSelected(configuration.isCheckNestedForQuickMerge());

    boolean enabled = !myUseDefaultCheckBox.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryText.setEditable(enabled);
    myConfigurationDirectoryLabel.setEnabled(enabled);
    myLockOnDemand.setSelected(configuration.isUpdateLockOnDemand());
    myIgnoreWhitespaceDifferenciesInCheckBox.setSelected(configuration.isIgnoreSpacesInAnnotate());
    myShowMergeSourceInAnnotate.setSelected(configuration.isShowMergeSourcesInAnnotate());

    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    if (annotateRevisions == -1) {
      myMaximumNumberOfRevisionsCheckBox.setSelected(false);
      myNumRevsInAnnotations.setValue(SvnConfiguration.ourMaxAnnotateRevisionsDefault);
    } else {
      myMaximumNumberOfRevisionsCheckBox.setSelected(true);
      myNumRevsInAnnotations.setValue(annotateRevisions);
    }
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
    mySSHConnectionTimeout.setValue(Long.valueOf(configuration.getSshConnectionTimeout() / 1000));
    mySSHReadTimeout.setValue(Long.valueOf(configuration.getSshReadTimeout() / 1000));
    myHttpTimeout.setValue(Long.valueOf(configuration.getHttpTimeout() / 1000));
    myWithCommandLineClient.setSelected(configuration.isCommandLine());
    myRunUnderTerminal.setSelected(configuration.isRunUnderTerminal());
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLineClient.setText(applicationSettings17.getCommandLinePath());

    if (SvnConfiguration.SSLProtocols.sslv3.equals(configuration.getSslProtocols())) {
      mySSLv3RadioButton.setSelected(true);
    } else if (SvnConfiguration.SSLProtocols.tlsv1.equals(configuration.getSslProtocols())) {
      myTLSv1RadioButton.setSelected(true);
    } else {
      myAllRadioButton.setSelected(true);
    }

    mySshSettingsPanel.reset(configuration);
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myLockOnDemand = new JCheckBox() {
      @Override
      public JToolTip createToolTip() {
        JToolTip toolTip = new JToolTip(){{
          setUI(new MultiLineTooltipUI());
        }};
        toolTip.setComponent(this);
        return toolTip;
      }
    };

    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    int value = configuration.getMaxAnnotateRevisions();
    value = (value == -1) ? SvnConfiguration.ourMaxAnnotateRevisionsDefault : value;
    myNumRevsInAnnotations = new JSpinner(new SpinnerNumberModel(value, 10, 100000, 100));

    myNavigateToCommonProxyLink = new LinkLabel<>(SvnBundle.message("navigate.to.idea.proxy.settings"), null);

    final Long maximum = 30 * 60 * 1000L;
    final long connection = configuration.getSshConnectionTimeout() <= maximum ? configuration.getSshConnectionTimeout() : maximum;
    final long read = configuration.getSshReadTimeout() <= maximum ? configuration.getSshReadTimeout() : maximum;
    mySSHConnectionTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(connection / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    mySSHReadTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    myHttpTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
  }
}

