// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Kolosovsky.
 */
public interface RepositoryFeaturesClient extends SvnClient {

  boolean supportsMergeTracking(@NotNull Url url) throws VcsException;
}
