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

import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.IOException;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProxyModule extends BaseCommandRuntimeModule {

  public ProxyModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) throws SvnBindException {
    // for IDEA proxy case
    writeIdeaConfig2SubversionConfig(command.getRepositoryUrl());
  }

  private void writeIdeaConfig2SubversionConfig(@Nullable SVNURL repositoryUrl) throws SvnBindException {
    if (myAuthCallback.haveDataForTmpConfig()) {
      try {
        if (!myAuthCallback.persistDataToTmpConfig(repositoryUrl)) {
          throw new SvnBindException("Can not persist " + ApplicationNamesInfo.getInstance().getProductName() +
                                     " HTTP proxy information into tmp config directory");
        }
      }
      catch (IOException e) {
        throw new SvnBindException(e);
      }
      assert myAuthCallback.getSpecialConfigDir() != null;
    }
  }
}
