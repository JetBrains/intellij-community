/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.FileStatusFactory;

import java.awt.*;

public class SvnFileStatus {
  private SvnFileStatus() {
  }

  public static final FileStatus EXTERNAL =
    FileStatusFactory.getInstance()
      .createFileStatus("IDEA_SVN_FILESTATUS_EXTERNAL", SvnBundle.message("file.status.external"), new Color(0x72A038));

  public static final FileStatus OBSTRUCTED =
    FileStatusFactory.getInstance()
      .createFileStatus("IDEA_SVN_FILESTATUS_OBSTRUCTED", SvnBundle.message("file.status.obstructed"), new Color(0x727238));

  public static final FileStatus REPLACED =
    FileStatusFactory.getInstance().createFileStatus("IDEA_SVN_REPLACED",
                                                                      SvnBundle.message("file.status.replaced"),
                                                                      FileStatus.COLOR_ADDED);
}
