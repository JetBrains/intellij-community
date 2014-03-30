package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import junit.framework.Assert;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.junit.Test;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    List<String> parameters = new ArrayList<String>();
    parameters.add("--xml");

    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File workingDirectory = new File(myWorkingCopyDir.getPath());
    CommandExecutor command =
      CommandUtil.execute(vcs, SvnTarget.fromFile(workingDirectory), workingDirectory, SvnCommandName.info, parameters, null);
    final String result = command.getOutput();
    System.out.println(result);
    Assert.assertNotNull(result);
  }
}
