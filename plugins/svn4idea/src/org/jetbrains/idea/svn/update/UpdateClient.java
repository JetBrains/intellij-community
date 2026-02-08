// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;

public interface UpdateClient extends SvnClient {

  long doUpdate(File path, Revision revision, Depth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SvnBindException;

  long doSwitch(File path,
                Url url,
                Revision pegRevision,
                Revision revision,
                Depth depth,
                boolean allowUnversionedObstructions,
                boolean depthIsSticky) throws SvnBindException;

  void setEventHandler(ProgressTracker dispatcher);
  void setIgnoreExternals(boolean ignoreExternals);
}
