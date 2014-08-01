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

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.io.File;

public class SvnFormatSelector {

  @NotNull
  public static WorkingCopyFormat findRootAndGetFormat(final File path) {
    File root = SvnUtil.getWorkingCopyRootNew(path);

    return root != null ? getWorkingCopyFormat(root) : WorkingCopyFormat.UNKNOWN;
  }

  @NotNull
  public static WorkingCopyFormat getWorkingCopyFormat(final File path) {
    WorkingCopyFormat format = SvnUtil.getFormat(path);

    return WorkingCopyFormat.UNKNOWN.equals(format) ? detectWithSvnKit(path) : format;
  }

  @NotNull
  private static WorkingCopyFormat detectWithSvnKit(File path) {
    try {
      final SvnWcGeneration svnWcGeneration = SvnOperationFactory.detectWcGeneration(path, true);
      if (SvnWcGeneration.V17.equals(svnWcGeneration)) return WorkingCopyFormat.ONE_DOT_SEVEN;
    }
    catch (SVNException e) {
      //
    }
    int format  = 0;
    // it is enough to check parent and this.
    try {
      format = SVNAdminAreaFactory.checkWC(path, false);
    } catch (SVNException e) {
      //
    }
    try {
      if (format == 0 && path.getParentFile() != null) {
        format = SVNAdminAreaFactory.checkWC(path.getParentFile(), false);
      }
    } catch (SVNException e) {
      //
    }

    return WorkingCopyFormat.getInstance(format);
  }
}
