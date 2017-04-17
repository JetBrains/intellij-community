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

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.SshSettingsPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SvnConfigurable implements Configurable {

  public static final String DISPLAY_NAME = SvnVcs.VCS_DISPLAY_NAME;

  private final Project myProject;

  private JPanel myMainPanel;
  private GeneralSettingsPanel myGeneralSettingsPanel;
  private PresentationSettingsPanel myPresentationSettingsPanel;
  private NetworkSettingsPanel myNetworkSettingsPanel;
  private SshSettingsPanel mySshSettingsPanel;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public boolean isModified() {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);

    return myGeneralSettingsPanel.isModified(configuration) ||
           myPresentationSettingsPanel.isModified(configuration) ||
           myNetworkSettingsPanel.isModified(configuration) ||
           mySshSettingsPanel.isModified(configuration);
  }

  public void apply() throws ConfigurationException {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);

    myGeneralSettingsPanel.apply(configuration);
    myPresentationSettingsPanel.apply(configuration);
    myNetworkSettingsPanel.apply(configuration);
    mySshSettingsPanel.apply(configuration);
  }

  public void reset() {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);

    myGeneralSettingsPanel.reset(configuration);
    myPresentationSettingsPanel.reset(configuration);
    myNetworkSettingsPanel.reset(configuration);
    mySshSettingsPanel.reset(configuration);
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myGeneralSettingsPanel = new GeneralSettingsPanel(myProject);
    myPresentationSettingsPanel = new PresentationSettingsPanel(myProject);
    myNetworkSettingsPanel = new NetworkSettingsPanel(myProject);
    mySshSettingsPanel = new SshSettingsPanel(myProject);
  }

  public static void selectConfigurationDirectory(@NotNull String path,
                                                  @NotNull final Consumer<String> dirConsumer,
                                                  final Project project,
                                                  @Nullable final Component component) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
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
}

