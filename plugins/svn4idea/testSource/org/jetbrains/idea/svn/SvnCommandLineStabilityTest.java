// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.commandLine.*;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;

// TODO: Rather strange test - probably it should be removed
public class SvnCommandLineStabilityTest extends SvnTestCase {

  @Test
  public void testCallInfoManyTimes() throws Exception {
    for (int i = 0; i < 200; i++) {
      call();
      TimeoutUtil.sleep(5);
    }
  }

  private void call() throws VcsException {
    String result = runInfo().getOutput();
    System.out.println(result);
    assertNotNull(result);
  }

  @NotNull
  private CommandExecutor runInfo() throws SvnBindException {
    File workingDirectory = VfsUtilCore.virtualToIoFile(myWorkingCopyDir);
    Command command = new Command(SvnCommandName.info);

    command.setTarget(Target.on(workingDirectory));
    command.setWorkingDirectory(workingDirectory);
    command.put("--xml");

    CommandRuntime runtime = new CommandRuntime(vcs, new AuthenticationService(vcs, true));
    return runtime.runWithAuthenticationAttempt(command);
  }
}
