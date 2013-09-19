/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.config.SvnConfigureProxiesDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class SvnConfigurable implements Configurable {

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
  private JSpinner myNumRevsInAnnotations;
  private JCheckBox myMaximumNumberOfRevisionsCheckBox;
  private JSpinner mySSHConnectionTimeout;
  private JSpinner mySSHReadTimeout;
  private TextFieldWithBrowseButton myCommandLineClient;
  private JSpinner myHttpTimeout;
  private JBRadioButton mySSLv3RadioButton;
  private JBRadioButton myTLSv1RadioButton;
  private JBRadioButton myAllRadioButton;
  private JLabel mySSLExplicitly;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;

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
        clearAuthenticationCache(myProject, myComponent, myConfigurationDirectoryText.getText());
      }
    });



    myConfigurationDirectoryText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        @NonNls String path = myConfigurationDirectoryText.getText().trim();
        selectConfigirationDirectory(path, new Consumer<String>() {
          @Override
          public void consume(String s) {
            myConfigurationDirectoryText.setText(s);
          }
        }, myProject, myComponent);
      }
    });

    myConfigurationDirectoryLabel.setLabelFor(myConfigurationDirectoryText);

    myUseCommonProxy.setText(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().getProductName()));
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
    if (SvnVcs.isSSLProtocolExplicitlySet()) {
      mySSLv3RadioButton.setEnabled(false);
      myTLSv1RadioButton.setEnabled(false);
      myAllRadioButton.setEnabled(false);
      mySSLExplicitly.setVisible(true);
      mySSLExplicitly.setText("Set explicitly to: " + System.getProperty(SvnVcs.SVNKIT_HTTP_SSL_PROTOCOLS));
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
  }

  public static void selectConfigirationDirectory(@NotNull String path, @NotNull final Consumer<String> dirConsumer,
                                                   final Project project, @Nullable final Component component) {
    final FileChooserDescriptor descriptor = createFileDescriptor();
    path = "file://" + path.replace(File.separatorChar, '/');
    VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);

    String oldValue = PropertiesComponent.getInstance().getValue("FileChooser.showHiddens");
    PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", Boolean.TRUE.toString());
    VirtualFile file = FileChooser.chooseFile(descriptor, component, project, root);
    PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", oldValue);
    if (file == null) {
      return;
    }
    final String resultPath = file.getPath().replace('/', File.separatorChar);
    dirConsumer.consume(resultPath);
  }

  public static void clearAuthenticationCache(@NotNull final Project project, final Component component, final String configDirPath) {
    if (configDirPath != null) {
      int result;
      if (component == null) {
        result = Messages.showYesNoDialog(project, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                          SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                          Messages.getWarningIcon());
      } else {
        result = Messages.showYesNoDialog(component, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                          SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                          Messages.getWarningIcon());
      }
      if (result == 0) {
        SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
        SvnConfiguration.getInstance(project).clearAuthenticationDirectory(project);
      }
    }
  }

  private static FileChooserDescriptor createFileDescriptor() {
    final FileChooserDescriptor descriptor =  FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setShowFileSystemRoots(true);
    descriptor.setTitle(SvnBundle.message("dialog.title.select.configuration.directory"));
    descriptor.setDescription(SvnBundle.message("dialog.description.select.configuration.directory"));
    descriptor.setHideIgnored(false);
    return descriptor;
  }

  public JComponent createComponent() {

    return myComponent;
  }

  public String getDisplayName() {
    return SvnVcs.VCS_DISPLAY_NAME;
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
    if (configuration.UPDATE_LOCK_ON_DEMAND != myLockOnDemand.isSelected()) {
      return true;
    }
    if (configuration.CHECK_NESTED_FOR_QUICK_MERGE != myCheckNestedInQuickMerge.isSelected()) {
      return true;
    }
    if (configuration.IGNORE_SPACES_IN_ANNOTATE != myIgnoreWhitespaceDifferenciesInCheckBox.isSelected()) {
      return true;
    }
    if (configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE != myShowMergeSourceInAnnotate.isSelected()) {
      return true;
    }
    if (! configuration.myUseAcceleration.equals(acceleration())) return true;
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
    if (configuration.mySSHConnectionTimeout/1000 != ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.mySSHReadTimeout/1000 != ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.getHttpTimeout()/1000 != ((SpinnerNumberModel) myHttpTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (! getSelectedSSL().equals(configuration.SSL_PROTOCOLS)) return true;
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    if (! Comparing.equal(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim())) return true;
    return !configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim());
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
    configuration.CHECK_NESTED_FOR_QUICK_MERGE = myCheckNestedInQuickMerge.isSelected();
    configuration.UPDATE_LOCK_ON_DEMAND = myLockOnDemand.isSelected();
    configuration.setIgnoreSpacesInAnnotate(myIgnoreWhitespaceDifferenciesInCheckBox.isSelected());
    configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE = myShowMergeSourceInAnnotate.isSelected();
    if (! myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      configuration.setMaxAnnotateRevisions(-1);
    } else {
      configuration.setMaxAnnotateRevisions(((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue());
    }
    configuration.mySSHConnectionTimeout = ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue() * 1000;
    configuration.mySSHReadTimeout = ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue() * 1000;

    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    boolean reloadWorkingCopies = !acceleration().equals(configuration.myUseAcceleration) ||
                                  !StringUtil.equals(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim());
    configuration.myUseAcceleration = acceleration();
    configuration.SSL_PROTOCOLS = getSelectedSSL();
    SvnVcs.getInstance(myProject).refreshSSLProperty();

    applicationSettings17.setCommandLinePath(myCommandLineClient.getText().trim());
    boolean isClientValid = vcs17.checkCommandLineVersion();
    if (isClientValid && reloadWorkingCopies) {
      vcs17.invokeRefreshSvnRoots();
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
    configuration.setHttpTimeout(((SpinnerNumberModel) myHttpTimeout.getModel()).getNumber().longValue() * 1000);
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
    myCheckNestedInQuickMerge.setSelected(configuration.CHECK_NESTED_FOR_QUICK_MERGE);

    boolean enabled = !myUseDefaultCheckBox.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryText.setEditable(enabled);
    myConfigurationDirectoryLabel.setEnabled(enabled);
    myLockOnDemand.setSelected(configuration.UPDATE_LOCK_ON_DEMAND);
    myIgnoreWhitespaceDifferenciesInCheckBox.setSelected(configuration.IGNORE_SPACES_IN_ANNOTATE);
    myShowMergeSourceInAnnotate.setSelected(configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE);

    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    if (annotateRevisions == -1) {
      myMaximumNumberOfRevisionsCheckBox.setSelected(false);
      myNumRevsInAnnotations.setValue(SvnConfiguration.ourMaxAnnotateRevisionsDefault);
    } else {
      myMaximumNumberOfRevisionsCheckBox.setSelected(true);
      myNumRevsInAnnotations.setValue(annotateRevisions);
    }
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
    mySSHConnectionTimeout.setValue(Long.valueOf(configuration.mySSHConnectionTimeout / 1000));
    mySSHReadTimeout.setValue(Long.valueOf(configuration.mySSHReadTimeout / 1000));
    myHttpTimeout.setValue(Long.valueOf(configuration.getHttpTimeout() / 1000));
    myWithCommandLineClient.setSelected(configuration.isCommandLine());
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLineClient.setText(applicationSettings17.getCommandLinePath());

    if (SvnConfiguration.SSLProtocols.sslv3.equals(configuration.SSL_PROTOCOLS)) {
      mySSLv3RadioButton.setSelected(true);
    } else if (SvnConfiguration.SSLProtocols.tlsv1.equals(configuration.SSL_PROTOCOLS)) {
      myTLSv1RadioButton.setSelected(true);
    } else {
      myAllRadioButton.setSelected(true);
    }
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

    final Long maximum = 30 * 60 * 1000L;
    final long connection = configuration.mySSHConnectionTimeout <= maximum ? configuration.mySSHConnectionTimeout : maximum;
    final long read = configuration.mySSHReadTimeout <= maximum ? configuration.mySSHReadTimeout : maximum;
    mySSHConnectionTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(connection / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    mySSHReadTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    myHttpTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
  }
}

