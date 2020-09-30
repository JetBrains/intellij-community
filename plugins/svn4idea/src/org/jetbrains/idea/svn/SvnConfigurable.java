// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
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

import static org.jetbrains.idea.svn.SvnBundle.message;

public abstract class SvnConfigurable extends ConfigurableBase<ConfigurableUi<SvnConfiguration>, SvnConfiguration> {
  private static final @NonNls String ID = "vcs.Subversion";
  private static final @NonNls String HELP_ID = "project.propSubversion";

  @NotNull private final Project myProject;
  @NotNull private final Supplier<? extends ConfigurableUi<SvnConfiguration>> myUiSupplier;

  public static @NlsContexts.ConfigurableName @NotNull String getGroupDisplayName() {
    return SvnVcs.VCS_DISPLAY_NAME;
  }

  protected SvnConfigurable(@NotNull Project project,
                            @NonNls @NotNull String idSuffix,
                            @NlsContexts.ConfigurableName @NotNull String displayName,
                            @NotNull Supplier<? extends ConfigurableUi<SvnConfiguration>> uiSupplier) {
    this(project, ID + "." + idSuffix, displayName, uiSupplier, HELP_ID + "." + idSuffix);
  }

  protected SvnConfigurable(@NotNull Project project,
                            @NonNls @NotNull String id,
                            @NlsContexts.ConfigurableName @NotNull String displayName,
                            @NotNull Supplier<? extends ConfigurableUi<SvnConfiguration>> uiSupplier,
                            @NonNls @NotNull String helpId) {
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
      super(project, ID, getGroupDisplayName(), () -> new GeneralSettingsPanel(project), HELP_ID);
    }
  }

  public static class Presentation extends SvnConfigurable {
    public Presentation(@NotNull Project project) {
      super(project, "Presentation", message("configurable.name.svn.presentation"), () -> new PresentationSettingsPanel(project));
    }
  }

  public static class Network extends SvnConfigurable {
    public Network(@NotNull Project project) {
      super(project, "Network", message("configurable.name.svn.network"), () -> new NetworkSettingsPanel(project));
    }
  }

  public static class Ssh extends SvnConfigurable {
    public Ssh(@NotNull Project project) {
      super(project, "SSH", message("configurable.name.svn.ssh"), () -> new SshSettingsPanel(project));
    }
  }

  public static void selectConfigurationDirectory(@NotNull String path,
                                                  @NotNull final Consumer<? super String> dirConsumer,
                                                  final Project project,
                                                  @Nullable final Component component) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(message("dialog.title.select.configuration.directory"))
      .withDescription(message("dialog.description.select.configuration.directory"))
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