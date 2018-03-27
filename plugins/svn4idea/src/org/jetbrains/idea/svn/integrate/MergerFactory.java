// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

import java.io.File;

public interface MergerFactory {
  IMerger createMerger(final SvnVcs vcs,
                       final File target,
                       final UpdateEventHandler handler,
                       final Url currentBranchUrl,
                       String branchName);
}
