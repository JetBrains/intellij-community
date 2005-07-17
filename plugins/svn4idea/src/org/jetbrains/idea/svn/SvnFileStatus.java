/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.peer.PeerFactory;

import java.awt.*;

public class SvnFileStatus {
  public static final FileStatus EXTERNAL =
    PeerFactory.getInstance().getFileStatusFactory()
      .createFileStatus("IDEA_SVN_FILESTATUS_EXTERNAL", "External (svn)", new Color(0x72A038));
  public static final FileStatus OBSTRUCTED =
    PeerFactory.getInstance().getFileStatusFactory()
      .createFileStatus("IDEA_SVN_FILESTATUS_OBSTRUCTED", "Obstructed (svn)", new Color(0x727238));
  public static final FileStatus REPLACED =
    PeerFactory.getInstance().getFileStatusFactory().createFileStatus("IDEA_SVN_REPLACED",
                                                                      "Replaced (svn)",
                                                                      FileStatus.COLOR_ADDED);
  public static final FileStatus SWITCHED =
    PeerFactory.getInstance().getFileStatusFactory().createFileStatus("IDEA_SVN_SWITCHED",
                                                                      "Switched (svn)",
                                                                      new Color(0x72A038));
}
