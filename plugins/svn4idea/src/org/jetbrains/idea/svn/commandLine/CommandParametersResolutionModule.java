/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.InfoCommandRepositoryProvider;
import org.jetbrains.idea.svn.api.Repository;
import org.jetbrains.idea.svn.api.UrlMappingRepositoryProvider;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandParametersResolutionModule extends BaseCommandRuntimeModule {

  public CommandParametersResolutionModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) throws SvnBindException {
    if (command.getRepositoryUrl() == null) {
      command.setRepositoryUrl(resolveRepositoryUrl(command));
    }
    if (command.getWorkingDirectory() == null) {
      command.setWorkingDirectory(resolveWorkingDirectory(command));
    }
    command.setConfigDir(myAuthCallback.getSpecialConfigDir());
    command.saveOriginalParameters();
  }

  @Nullable
  private SVNURL resolveRepositoryUrl(@NotNull Command command) {
    UrlMappingRepositoryProvider urlMappingProvider = new UrlMappingRepositoryProvider(myVcs, command.getTarget());
    InfoCommandRepositoryProvider infoCommandProvider = new InfoCommandRepositoryProvider(myVcs, command.getTarget());

    Repository repository = urlMappingProvider.get();
    if (repository == null && !SvnCommandName.info.equals(command.getName())) {
      repository = infoCommandProvider.get();
    }

    return repository != null ? repository.getUrl() : null;
  }

  @NotNull
  private File resolveWorkingDirectory(@NotNull Command command) {
    SvnTarget target = command.getTarget();
    File workingDirectory = target.isFile() ? target.getFile() : null;
    // TODO: Do we really need search existing parent - or just take parent directory if target is file???
    workingDirectory = CommandUtil.correctUpToExistingParent(workingDirectory);

    if (workingDirectory == null) {
      workingDirectory =
        !myVcs.getProject().isDefault() ? VfsUtilCore.virtualToIoFile(myVcs.getProject().getBaseDir()) : CommandUtil.getHomeDirectory();
    }

    return workingDirectory;
  }
}
