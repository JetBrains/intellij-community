/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/23/12
 * Time: 2:29 PM
 */
// TODO: Such locking functionality is not required anymore. Likely to be removed.
public class SvnWriteOperationLocks extends SvnAbstractWriteOperationLocks {
  private final RootsToWorkingCopies myRootsToWorkingCopies;

  public SvnWriteOperationLocks(RootsToWorkingCopies rootsToWorkingCopies) {
    super(1000);
    myRootsToWorkingCopies = rootsToWorkingCopies;
  }

  protected WorkingCopy getCopy(File file, boolean directory) throws SVNException {
    final VirtualFile parentOrSelf = ChangesUtil.findValidParentAccurately(VcsUtil.getFilePath(file, directory));
    final WorkingCopy wcRoot = myRootsToWorkingCopies.getWcRoot(parentOrSelf);
    if (wcRoot == null) {
      throw new SVNException(SVNErrorMessage.create(directory ? SVNErrorCode.WC_NOT_WORKING_COPY : SVNErrorCode.WC_NOT_FILE));
    }
    // todo check about externals!
    return wcRoot;
  }
}
