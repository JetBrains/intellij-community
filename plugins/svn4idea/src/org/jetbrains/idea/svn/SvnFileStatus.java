// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;

import java.awt.*;

public final class SvnFileStatus {
  private SvnFileStatus() {
  }

  private static final PluginId OUR_PLUGIN_ID;

  static {
    PluginDescriptor plugin = PluginManager.getPluginByClass(SvnFileStatus.class);
    OUR_PLUGIN_ID = plugin == null ? null : plugin.getPluginId();
  }

  public static final FileStatus EXTERNAL = FileStatusFactory.getInstance()
    .createFileStatus("IDEA_SVN_FILESTATUS_EXTERNAL", SvnBundle.messagePointer("file.status.external"), new Color(0x72A038), OUR_PLUGIN_ID);

  public static final FileStatus OBSTRUCTED = FileStatusFactory.getInstance()
    .createFileStatus("IDEA_SVN_FILESTATUS_OBSTRUCTED", SvnBundle.messagePointer("file.status.obstructed"), new Color(0x727238), OUR_PLUGIN_ID);

  public static final FileStatus REPLACED = FileStatusFactory.getInstance()
    .createFileStatus("IDEA_SVN_REPLACED", SvnBundle.messagePointer("file.status.replaced"), FileStatus.ADDED.getColor(), OUR_PLUGIN_ID);
}
