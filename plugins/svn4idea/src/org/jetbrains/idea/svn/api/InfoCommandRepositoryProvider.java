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
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
* @author Konstantin Kolosovsky.
*/
public class InfoCommandRepositoryProvider extends BaseRepositoryProvider {

  public InfoCommandRepositoryProvider(@NotNull SvnVcs vcs, @NotNull SvnTarget target) {
    super(vcs, target);
  }

  @Nullable
  @Override
  public Repository get() {
    Repository result;

    if (myTarget.isURL()) {
      // TODO: Also could still execute info when target is url - either to use info for authentication or to just get correct repository
      // TODO: url in case of "read" operations are allowed anonymously.
      result = new Repository(myTarget.getURL());
    }
    else {
      SVNInfo info = myVcs.getInfo(myTarget.getFile());
      result = info != null ? new Repository(info.getRepositoryRootURL()) : null;
    }

    return result;
  }
}
