package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.ide.impl.ProjectUtil;
import org.apache.oro.io.GlobFilenameFilter;

import java.io.File;
import java.io.FilenameFilter;


public class CheckoutAction extends AnAction {
  private final CheckoutProvider myProvider;

  public CheckoutAction(final CheckoutProvider provider) {
    myProvider = provider;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    myProvider.doCheckout(new MyListener(project));
  }

  private static boolean processProject(final Project project, final File directory) {
    File[] files = directory.listFiles((FilenameFilter) new GlobFilenameFilter("*.ipr"));
    if (files.length > 0) {
      int rc = Messages.showYesNoDialog(project, "You have checked out an IntelliJ IDEA project file:\n" + files [0].getAbsolutePath() +
                                                 "\nWould you like to open it?", "Checkout from Version Control", Messages.getQuestionIcon());
      if (rc == 0) {
        ProjectUtil.openProject(files [0].getAbsolutePath(), project, false);
      }
      return true;
    }
    return false;
  }

  private static void processNoProject(final Project project, final File directory) {
    int rc = Messages.showYesNoDialog(project, "Would you like to create an IntelliJ IDEA project for the sources you have checked out to " +
      directory.getAbsolutePath() + "?", "Checkout from Version Control", Messages.getQuestionIcon());
    if (rc == 0) {
      ProjectUtil.createNewProject(project, directory.getAbsolutePath());
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(myProvider.getVcsName(), true);
  }

  private static class MyListener implements CheckoutProvider.Listener {
    private final Project myProject;
    private boolean myFoundProject = false;
    private File myFirstDirectory;

    public MyListener(final Project project) {
      myProject = project;
    }

    public void directoryCheckedOut(final File directory) {
      if (myFirstDirectory == null) {
        myFirstDirectory = directory;
      }
      if (!myFoundProject) {
        myFoundProject = processProject(myProject, directory);
      }
    }

    public void checkoutCompleted() {
      if (!myFoundProject && myFirstDirectory != null) {
        processNoProject(myProject, myFirstDirectory);
      }
    }
  }
}
