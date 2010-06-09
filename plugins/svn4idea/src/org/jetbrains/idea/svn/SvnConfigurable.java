/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.MultiLineTooltipUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.config.ConfigureProxiesListener;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.swing.*;
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
  private JLabel myClearCacheLabel;
  private JLabel myUseCommonProxyLabel;
  private JLabel myEditProxyLabel;
  private JCheckBox myLockOnDemand;
  private JCheckBox myCheckNestedInQuickMerge;
  private JCheckBox myDetectNestedWorkingCopiesCheckBox;
  private JCheckBox myIgnoreWhitespaceDifferenciesInCheckBox;
  private JCheckBox myShowMergeSourceInAnnotate;
  private JSpinner myNumRevsInAnnotations;
  private JCheckBox myMaximumNumberOfRevisionsCheckBox;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;

    myUseDefaultCheckBox.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        boolean enabled = !myUseDefaultCheckBox.isSelected();
        myConfigurationDirectoryText.setEnabled(enabled);
        myConfigurationDirectoryLabel.setEnabled(enabled);
        SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
        String path = configuration.getConfigurationDirectory();
        if (!enabled || path == null) {
          myConfigurationDirectoryText.setText(SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath());
        }
        else {
          myConfigurationDirectoryText.setText(path);
        }
      }
    });

    myClearAuthButton.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        String path = myConfigurationDirectoryText.getText();
        if (path != null) {
          int result = Messages.showYesNoDialog(myComponent, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                                SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                                             Messages.getWarningIcon());
          if (result == 0) {
            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            SvnConfiguration.getInstance(myProject).clearAuthenticationDirectory();
          }
        }

      }
    });


    final FileChooserDescriptor descriptor = createFileDescriptor();

    myConfigurationDirectoryText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        @NonNls String path = myConfigurationDirectoryText.getText().trim();
        path = "file://" + path.replace(File.separatorChar, '/');
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);

        String oldValue = PropertiesComponent.getInstance().getValue("FileChooser.showHiddens");
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", Boolean.TRUE.toString());
        VirtualFile[] files = FileChooser.chooseFiles(myComponent, descriptor, root);
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", oldValue);
        if (files.length != 1 || files[0] == null) {
          return;
        }
        myConfigurationDirectoryText.setText(files[0].getPath().replace('/', File.separatorChar));
      }
    });
    myConfigurationDirectoryText.setEditable(false);

    myConfigurationDirectoryLabel.setLabelFor(myConfigurationDirectoryText);

    myUseCommonProxy.setText(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().getProductName()));
    myEditProxiesButton.addActionListener(new ConfigureProxiesListener(myProject));

    myMaximumNumberOfRevisionsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
      }
    });
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
  }

  private FileChooserDescriptor createFileDescriptor() {
    final FileChooserDescriptor descriptor =  new FileChooserDescriptor(false, true, false, false, false, false);
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
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return HELP_ID;
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
    if (configuration.DETECT_NESTED_COPIES != myDetectNestedWorkingCopiesCheckBox.isSelected()) {
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
    return !configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim());
  }

  public void apply() throws ConfigurationException {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    configuration.setConfigurationDirectory(myConfigurationDirectoryText.getText());
    configuration.setUseDefaultConfiguation(myUseDefaultCheckBox.isSelected());
    configuration.setIsUseDefaultProxy(myUseCommonProxy.isSelected());
    if ((! configuration.DETECT_NESTED_COPIES) && (configuration.DETECT_NESTED_COPIES != myDetectNestedWorkingCopiesCheckBox.isSelected())) {
      SvnVcs.getInstance(myProject).invokeRefreshSvnRoots(true);
    }
    configuration.DETECT_NESTED_COPIES = myDetectNestedWorkingCopiesCheckBox.isSelected();
    configuration.CHECK_NESTED_FOR_QUICK_MERGE = myCheckNestedInQuickMerge.isSelected();
    configuration.UPDATE_LOCK_ON_DEMAND = myLockOnDemand.isSelected();
    configuration.setIgnoreSpacesInAnnotate(myIgnoreWhitespaceDifferenciesInCheckBox.isSelected());
    configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE = myShowMergeSourceInAnnotate.isSelected();
    if (! myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      configuration.setMaxAnnotateRevisions(-1);
    } else {
      configuration.setMaxAnnotateRevisions(((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue());
    }
  }

  public void reset() {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    String path = configuration.getConfigurationDirectory();
    if (configuration.isUseDefaultConfiguation() || path == null) {
      path = SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath();
    }
    myConfigurationDirectoryText.setText(path);
    myUseDefaultCheckBox.setSelected(configuration.isUseDefaultConfiguation());
    myUseCommonProxy.setSelected(configuration.isIsUseDefaultProxy());
    myDetectNestedWorkingCopiesCheckBox.setSelected(configuration.DETECT_NESTED_COPIES);
    myCheckNestedInQuickMerge.setSelected(configuration.CHECK_NESTED_FOR_QUICK_MERGE);

    boolean enabled = !myUseDefaultCheckBox.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
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
  }
}

