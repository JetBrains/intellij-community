/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SvnEditFileProvider implements EditFileProvider {
  private final SvnVcs myVCS;

  public SvnEditFileProvider(SvnVcs vcs) {
    myVCS = vcs;
  }

  public void editFiles(VirtualFile[] files) throws VcsException {
    File[] ioFiles = new File[files.length];

    for (int i = 0; i < files.length; i++) {
      ioFiles[i] = virtualToIoFile(files[i]);

      PropertyClient client = myVCS.getFactory(ioFiles[i]).createPropertyClient();
      PropertyValue property = client.getProperty(Target.on(ioFiles[i], Revision.WORKING), SvnPropertyKeys.SVN_NEEDS_LOCK,
                                                  false, Revision.WORKING);

      if (property == null) {
        throw new VcsException(SvnBundle.message("exception.text.file.miss.svn", ioFiles[i].getName()));
      }
    }
    SvnUtil.doLockFiles(myVCS.getProject(), myVCS, ioFiles);
  }

  public String getRequestText() {
    return SvnBundle.message("confirmation.text.edit.file");
  }
}
