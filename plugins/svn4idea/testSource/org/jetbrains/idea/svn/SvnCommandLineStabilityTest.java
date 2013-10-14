package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.LineCommandAdapter;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.junit.Test;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/19/13
 * Time: 11:45 AM
 */
// TODO: Rather strange test - probably it should be removed
public class SvnCommandLineStabilityTest extends Svn17TestCase {

  private static SvnSimpleCommand createSimpleCommand(final Project project, File workingDirectory, @NotNull SvnCommandName commandName) {
    final SvnSimpleCommand command =
      new SvnSimpleCommand(workingDirectory, commandName, SvnApplicationSettings.getInstance().getCommandLinePath());
    addStartFailedListener(project, command);
    return command;
  }

  private static void addStartFailedListener(final Project project, SvnCommand command) {
    command.addListener(new LineCommandAdapter() {
      @Override
      public void processTerminated(int exitCode) {
      }

      @Override
      public void startFailed(Throwable exception) {
        SvnVcs.getInstance(project).checkCommandLineVersion();
      }
    });
  }

  @Test
  public void testCallInfoManyTimes() throws Exception {
    for (int i = 0; i < 200; i++) {
      call();
      try { Thread.sleep(5); } catch (InterruptedException e) {}
    }
  }

  private void call() throws VcsException {
    final SvnSimpleCommand command = createSimpleCommand(myProject, new File(myWorkingCopyDir.getPath()), SvnCommandName.info);
    command.addParameters("--xml");
    final String result = command.run();
    System.out.println(result);
    Assert.assertNotNull(result);
  }
}
