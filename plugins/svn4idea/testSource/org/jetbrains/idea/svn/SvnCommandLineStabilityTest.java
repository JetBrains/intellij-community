package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.commandLine.*;
import org.junit.Test;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/19/13
 * Time: 11:45 AM
 */
// TODO: Rather strange test - probably it should be removed
public class SvnCommandLineStabilityTest extends Svn17TestCase {

  @Test
  public void testCallInfoManyTimes() throws Exception {
    for (int i = 0; i < 200; i++) {
      call();
      try { Thread.sleep(5); } catch (InterruptedException e) {}
    }
  }

  private void call() throws VcsException {
    String result = runInfo().getOutput();
    System.out.println(result);
    Assert.assertNotNull(result);
  }

  @NotNull
  private CommandExecutor runInfo() throws SvnBindException {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File workingDirectory = VfsUtilCore.virtualToIoFile(myWorkingCopyDir);
    Command command = new Command(SvnCommandName.info);

    command.setTarget(SvnTarget.fromFile(workingDirectory));
    command.setWorkingDirectory(workingDirectory);
    command.put("--xml");

    CommandRuntime runtime = new CommandRuntime(vcs, new AuthenticationService(vcs, true));
    return runtime.runWithAuthenticationAttempt(command);
  }
}
