// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.TestClientRunner;

import java.io.IOException;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SvnClientRunnerImpl implements SvnClientRunner {
  private final TestClientRunner myTestClientRunner;

  public SvnClientRunnerImpl(final TestClientRunner testClientRunner) {
    myTestClientRunner = testClientRunner;
  }

  @Override
  public ProcessOutput runSvn(final VirtualFile file, String... commandLine) throws IOException {
    return myTestClientRunner.runClient("svn", null, virtualToIoFile(file), commandLine);
    }

  @Override
  public void checkin(final VirtualFile file) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(file), myTestClientRunner, new String[]{"ci", "-m", "test", file.getPath()});
  }

  @Override
  public void update(final VirtualFile file) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(file), myTestClientRunner,
                                           new String[]{"up", "--accept", "postpone", file.getPath()});
  }

  @Override
  public void checkout(final String repoUrl, final VirtualFile file) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(file), myTestClientRunner, new String[]{"co", repoUrl, file.getPath()});
  }

  @Override
  public void add(VirtualFile root, String path) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(root), myTestClientRunner, new String[]{"add", path});
  }

  @Override
  public void delete(VirtualFile root, String path) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(root), myTestClientRunner, new String[]{"delete", path});
  }

  @Override
  public void copy(VirtualFile root, String path, String from) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(root), myTestClientRunner, new String[]{"copy", path, from});
  }

  @Override
  public void testSvnVersion(VirtualFile root) throws IOException {
    SvnTestCase.runInAndVerifyIgnoreOutput(virtualToIoFile(root), myTestClientRunner, new String[]{"--version"});
  }
}
