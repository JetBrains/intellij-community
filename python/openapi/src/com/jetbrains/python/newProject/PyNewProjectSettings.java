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
package com.jetbrains.python.newProject;

import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nullable;

/**
 * Project generation settings selected on the first page of the new project dialog.
 *
 * @author catherine
 */
public class PyNewProjectSettings {
  private Sdk mySdk;
  private boolean myInstallFramework;
  /**
   * Path on remote server for remote project
   */
  @Nullable
  private String myRemotePath;

  @Nullable
  public final Sdk getSdk() {
    return mySdk;
  }

  public final void setSdk(@Nullable final Sdk sdk) {
    mySdk = sdk;
  }

  public void setInstallFramework(final boolean installFramework) {
    myInstallFramework = installFramework;
  }

  public boolean installFramework() {
    return myInstallFramework;
  }

  public final void setRemotePath(@Nullable final String remotePath) {
      myRemotePath = remotePath;
  }

  @Nullable
  public final String getRemotePath() {
    return myRemotePath;
  }
}
