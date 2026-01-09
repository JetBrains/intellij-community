// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ArrayUtilRt;
import com.intellij.vcsUtil.VcsUtil;
import com.jetbrains.python.extensions.ModuleExtKt;
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
  public static @NotNull String processNewFiles(final @NotNull Module module, final @NotNull String files) {
    final Sdk sdk = ModuleExtKt.getSdk(module);
    assert sdk != null : String.format("Sdk can't be null on module %s", module);

    final String[] fileNames = ArrayUtilRt.toStringArray(StringUtil.split(files, ","));
    if (fileNames.length == 0) {
      return "";
    }
    // Local, simply add
    addToVcsIfNeeded(module, fileNames);


    return String.format("Following files were affected \n %s", StringUtil.join(fileNames, "\n"));
  }

  /**
   * @param localFileNames names of local files to add to VCS
   */
  private static void addToVcsIfNeeded(final @NotNull Module module, final String @NotNull ... localFileNames) {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    fs.refresh(false);
    final Project project = module.getProject();
    Arrays.stream(localFileNames).map(o -> fs.findFileByPath(o)).filter(o -> o != null).forEach(file -> {

      final AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
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
