/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.SshSettingsPanel;

import java.awt.*;
import java.io.File;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.configurable.VcsManagerConfigurable.getVcsConfigurableId;

public abstract class SvnConfigurable extends ConfigurableBase<ConfigurableUi<SvnConfiguration>, SvnConfiguration> {

  public static final String DISPLAY_NAME = SvnVcs.VCS_DISPLAY_NAME;
  public static final String ID = getVcsConfigurableId(DISPLAY_NAME);
  @NonNls private static final String HELP_ID = "project.propSubversion";

  @NotNull private final Project myProject;
  @NotNull private final Supplier<? extends ConfigurableUi<SvnConfiguration>> myUiSupplier;

  protected SvnConfigurable(@NotNull Project project,
                            @NotNull String displayName,
                            @NotNull Supplier<? extends ConfigurableUi<SvnConfiguration>> uiSupplier) {
    this(project, ID + "." + displayName, displayName, uiSupplier, HELP_ID + "." + displayName);
  }

  protected SvnConfigurable(@NotNull Project project,
                            @NotNull String id,
                            @NotNull String displayName,
                            @NotNull Supplier<? extends ConfigurableUi<SvnConfiguration>> uiSupplier,
                            @NotNull String helpId) {
    super(id, displayName, helpId);
    myProject = project;
    myUiSupplier = uiSupplier;
  }

  @Override
  protected ConfigurableUi<SvnConfiguration> createUi() {
    return myUiSupplier.get();
  }

  @NotNull
  @Override
  protected SvnConfiguration getSettings() {
    return SvnConfiguration.getInstance(myProject);
  }

  public static class General extends SvnConfigurable {
    public General(@NotNull Project project) {
      super(project, ID, DISPLAY_NAME, () -> new GeneralSettingsPanel(project), HELP_ID);
    }
  }

  public static class Presentation extends SvnConfigurable {
    public Presentation(@NotNull Project project) {
      super(project, "Presentation", () -> new PresentationSettingsPanel(project));
    }
  }

  public static class Network extends SvnConfigurable {
    public Network(@NotNull Project project) {
      super(project, "Network", () -> new NetworkSettingsPanel(project));
    }
  }

  public static class Ssh extends SvnConfigurable {
    public Ssh(@NotNull Project project) {
      super(project, "SSH", () -> new SshSettingsPanel(project));
    }
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

