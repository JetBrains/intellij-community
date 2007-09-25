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
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.peer.PeerFactory;

import java.awt.*;

public class SvnFileStatus {
  private SvnFileStatus() {
  }

  public static final FileStatus EXTERNAL =
    PeerFactory.getInstance().getFileStatusFactory()
      .createFileStatus("IDEA_SVN_FILESTATUS_EXTERNAL", SvnBundle.message("file.status.external"), new Color(0x72A038));

  public static final FileStatus OBSTRUCTED =
    PeerFactory.getInstance().getFileStatusFactory()
      .createFileStatus("IDEA_SVN_FILESTATUS_OBSTRUCTED", SvnBundle.message("file.status.obstructed"), new Color(0x727238));

  public static final FileStatus REPLACED =
    PeerFactory.getInstance().getFileStatusFactory().createFileStatus("IDEA_SVN_REPLACED",
                                                                      SvnBundle.message("file.status.replaced"),
                                                                      FileStatus.COLOR_ADDED);
}
