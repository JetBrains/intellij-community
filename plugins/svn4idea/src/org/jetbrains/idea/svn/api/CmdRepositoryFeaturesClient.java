// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public class CmdRepositoryFeaturesClient extends BaseSvnClient implements RepositoryFeaturesClient {

  @Override
  public boolean supportsMergeTracking(@NotNull Url url) throws VcsException {
    boolean result;

    try {
      myFactory.createHistoryClient()
        .doLog(Target.on(url), Revision.HEAD, Revision.of(1), false, false, true, 1, null, null);
      result = true;
    }
    catch (SvnBindException e) {
      if (e.contains(ErrorCode.UNSUPPORTED_FEATURE) && e.getMessage().contains("mergeinfo")) {
        result = false;
      }
      else {
        throw e;
      }
    }

    return result;
  }
}
