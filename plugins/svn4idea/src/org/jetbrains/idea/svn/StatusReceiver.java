// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;

import java.util.EventListener;

public interface StatusReceiver extends EventListener {
  void process(final FilePath path, final Status status) throws SvnBindException;
  void processIgnored(final VirtualFile vFile);
  void processUnversioned(final VirtualFile vFile);
  void processCopyRoot(VirtualFile file, Url url, WorkingCopyFormat format, Url rootURL);
  void bewareRoot(VirtualFile vf, Url url);
  void finish();
}
