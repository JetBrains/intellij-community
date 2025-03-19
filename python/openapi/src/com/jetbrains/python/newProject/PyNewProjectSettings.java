// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject;

import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nullable;

/**
 * Project generation settings selected on the first page of the new project dialog.
 *
 * @author catherine
 * @deprecated Use {@link com.jetbrains.python.newProjectWizard}
 */
@Deprecated(forRemoval = true)
public class PyNewProjectSettings {
  private Sdk mySdk;
  /**
   * Path on remote server for remote project
   */
  private @Nullable String myRemotePath;

  private @Nullable Object myInterpreterInfoForStatistics;

  public final @Nullable Sdk getSdk() {
    return mySdk;
  }

  public final void setSdk(final @Nullable Sdk sdk) {
    mySdk = sdk;
  }

  public final void setRemotePath(final @Nullable String remotePath) {
      myRemotePath = remotePath;
  }

  public final void setInterpreterInfoForStatistics(@Nullable Object interpreterInfoForStatistics) {
    myInterpreterInfoForStatistics = interpreterInfoForStatistics;
  }

  public final @Nullable Object getInterpreterInfoForStatistics() {
    return myInterpreterInfoForStatistics;
  }

  public final @Nullable String getRemotePath() {
    return myRemotePath;
  }
}
