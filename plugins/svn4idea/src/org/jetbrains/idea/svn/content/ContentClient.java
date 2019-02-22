// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.content;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Target;

public interface ContentClient extends SvnClient {

  byte[] getContent(@NotNull Target target, @Nullable Revision revision, @Nullable Revision pegRevision) throws VcsException;
}
