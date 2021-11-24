// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public interface SvnClientRunner {
  ProcessOutput runSvn(final VirtualFile file, String... commandLine) throws IOException;

  void checkin(final VirtualFile file) throws IOException;

  void update(final VirtualFile file) throws IOException;

  void checkout(String repoUrl, VirtualFile file) throws IOException;

  void add(VirtualFile root, String path) throws IOException;

  void delete(VirtualFile root, String path) throws IOException;

  void copyOrMove(VirtualFile root, String from, String to, boolean isMove) throws IOException;

  void testSvnVersion(VirtualFile root) throws IOException;
}
