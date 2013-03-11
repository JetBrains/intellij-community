package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import junit.framework.Assert;
import org.jetbrains.idea.svn.commandLine.SvnCommandFactory;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.SvnSimpleCommand;
import org.junit.Test;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/19/13
 * Time: 11:45 AM
 */
public class SvnCommandLineStabilityTest extends Svn17TestCase {
  @Test
  public void testCallInfoManyTimes() throws Exception {
    for (int i = 0; i < 200; i++) {
      call();
      try { Thread.sleep(5); } catch (InterruptedException e) {}
    }
  }

  private void call() throws VcsException {
    final SvnSimpleCommand command = SvnCommandFactory.createSimpleCommand(myProject, new File(myWorkingCopyDir.getPath()), SvnCommandName.info);
    command.addParameters("--xml");
    final String result = command.run();
    System.out.println(result);
    Assert.assertNotNull(result);
  }
}
