// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.commandLine.*;
import org.junit.Test;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertThat;

public class SvnCommandLineStabilityTest extends SvnTestCase {
  @Test
  public void no_error_on_many_info_calls() throws Exception {
    for (int i = 0; i < 200; i++) {
      assertThat(runInfo().getOutput(), not(isEmptyString()));
    }
  }

  @NotNull
  private CommandExecutor runInfo() throws SvnBindException {
    File workingDirectory = virtualToIoFile(myWorkingCopyDir);
    Command command = new Command(SvnCommandName.info);

    command.setTarget(Target.on(workingDirectory));
    command.setWorkingDirectory(workingDirectory);
    command.put("--xml");

    CommandRuntime runtime = new CommandRuntime(vcs, new AuthenticationService(vcs, true));
    return runtime.runWithAuthenticationAttempt(command);
  }
}
