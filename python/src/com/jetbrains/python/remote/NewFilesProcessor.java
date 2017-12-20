/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.remote;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ArrayUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.jetbrains.extensions.ModuleExtKt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tool to process files, created on remote side
 * {@link #processNewFiles(Module, String)}
 *
 * @author Ilya.Kazakevich
 */
public final class NewFilesProcessor {

  private NewFilesProcessor() {
  }

  /**
   * Accepts list of affected files as comma joined string (that is the way python helper uses now).
   * Pulls them from remote side in case of remote int, or simply adds to VCS (if any).
   *
   * @param files file names, imploded with comma
   * @return Since this method is designed to be console filter, it returns user-readable message about files
   */
  @NotNull
  public static String processNewFiles(@NotNull final Module module, @NotNull final String files) {
    final Sdk sdk = ModuleExtKt.getSdk(module);
    assert sdk != null : String.format("Sdk can't be null on module %s", module);
    final PyProjectSynchronizer synchronizer = PythonRemoteInterpreterManager.getSynchronizerInstance(sdk);

    final String[] fileNames = StringUtil.split(files, ",").toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    if (fileNames.length == 0) {
      return "";
    }
    if (synchronizer != null) { // We are on remote side, lets pull files from python first
      synchronizer.syncProject(module, PySyncDirection.REMOTE_TO_LOCAL, success -> {
        if (!success) {
          return;
        }
        // Convert names to local and add to vcs
        final String[] localFileNames = Arrays.stream(fileNames)
          .map(remoteName -> synchronizer.mapFilePath(module.getProject(), PySyncDirection.REMOTE_TO_LOCAL, remoteName))
          .filter(localFileName -> localFileName != null)
          .toArray(size -> new String[size]);
        addToVcsIfNeeded(module, localFileNames);
      }, fileNames);
    }
    else { // Local, simply add
      addToVcsIfNeeded(module, fileNames);
    }


    return String.format("Following files were affected \n %s", StringUtil.join(fileNames, "\n"));
  }

  /**
   * @param localFileNames names of local files to add to VCS
   */
  private static void addToVcsIfNeeded(@NotNull final Module module, @NotNull final String... localFileNames) {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    fs.refresh(false);
    final Project project = module.getProject();
    Arrays.stream(localFileNames).map(o -> fs.findFileByPath(o)).filter(o -> o != null).forEach(file -> {

      final AbstractVcs<?> vcs = VcsUtil.getVcsFor(project, file);
      if (vcs == null) {
        return;
      }

      final CheckinEnvironment environment = vcs.getCheckinEnvironment();
      if (environment != null) {
        environment.scheduleUnversionedFilesForAddition(Collections.singletonList(file));
      }
    });
  }
}
