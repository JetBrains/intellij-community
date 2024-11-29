// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.api.InfoCommandRepositoryProvider;
import org.jetbrains.idea.svn.api.Repository;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.api.UrlMappingRepositoryProvider;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;

public class CommandParametersResolutionModule extends BaseCommandRuntimeModule {

  public CommandParametersResolutionModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) {
    if (command.getRepositoryUrl() == null) {
      command.setRepositoryUrl(resolveRepositoryUrl(command));
    }
    if (command.getWorkingDirectory() == null) {
      command.setWorkingDirectory(resolveWorkingDirectory(command));
    }
    command.setConfigDir(myAuthenticationService.getSpecialConfigDir().toFile());
  }

  @Nullable
  private Url resolveRepositoryUrl(@NotNull Command command) {
    UrlMappingRepositoryProvider urlMappingProvider = new UrlMappingRepositoryProvider(myVcs, command.getTarget());
    InfoCommandRepositoryProvider infoCommandProvider = new InfoCommandRepositoryProvider(myVcs, command.getTarget());

    Repository repository = urlMappingProvider.get();
    if (repository == null && !command.isLocalInfo()) {
      repository = infoCommandProvider.get();
    }

    return repository != null ? repository.getUrl() : null;
  }

  @NotNull
  private File resolveWorkingDirectory(@NotNull Command command) {
    File file = command.getTarget().getFile();
    RootUrlInfo root = file != null ? myVcs.getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(file)) : null;

    return root != null ? root.getIoFile() : getDefaultWorkingDirectory(myVcs.getProject());
  }

  @NotNull
  public static File getDefaultWorkingDirectory(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();

    return baseDir != null ? virtualToIoFile(baseDir) : CommandUtil.getHomeDirectory();
  }
}
