/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationGroup;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

import java.util.Collection;

public class SvnRepositoryLocationGroup extends RepositoryLocationGroup {
  private final SVNURL myUrl;

  public SvnRepositoryLocationGroup(@NotNull final SVNURL url, final Collection<RepositoryLocation> locations) {
    super(url.toString());
    myUrl = url;
    for (RepositoryLocation location : locations) {
      add(location);
    }
  }

  public SVNURL getUrl() {
    return myUrl;
  }
}
