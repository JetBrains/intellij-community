// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;

import java.awt.*;

public final class SvnFileStatus {
  private SvnFileStatus() {
  }

  public static final FileStatus EXTERNAL = FileStatusFactory.getInstance()
    .createFileStatus("IDEA_SVN_FILESTATUS_EXTERNAL", SvnBundle.message("file.status.external"), new Color(0x72A038));

  public static final FileStatus OBSTRUCTED = FileStatusFactory.getInstance()
    .createFileStatus("IDEA_SVN_FILESTATUS_OBSTRUCTED", SvnBundle.message("file.status.obstructed"), new Color(0x727238));

  public static final FileStatus REPLACED = FileStatusFactory.getInstance()
    .createFileStatus("IDEA_SVN_REPLACED", SvnBundle.message("file.status.replaced"), FileStatus.ADDED.getColor());
}
