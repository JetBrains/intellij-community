// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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

  @Override
  public String getRequestText() {
    return SvnBundle.message("confirmation.text.edit.file");
  }
}
