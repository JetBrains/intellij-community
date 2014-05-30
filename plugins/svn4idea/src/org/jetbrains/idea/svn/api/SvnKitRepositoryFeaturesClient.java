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
package org.jetbrains.idea.svn.api;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitRepositoryFeaturesClient extends BaseSvnClient implements RepositoryFeaturesClient {

  @Override
  public boolean supportsMergeTracking(@NotNull SVNURL url) throws VcsException {
    SVNRepository repository = null;
    try {
      repository = myVcs.getSvnKitManager().createRepository(url);
      return repository.hasCapability(SVNCapability.MERGE_INFO);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
    finally {
      if (repository != null) {
        repository.closeSession();
      }
    }
  }
}
