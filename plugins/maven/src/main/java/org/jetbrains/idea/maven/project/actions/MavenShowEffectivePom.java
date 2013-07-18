package org.jetbrains.idea.maven.project.actions;

import com.intellij.CommonBundle;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Sergey Evdokimov
 */
public class MavenShowEffectivePom extends AnAction implements DumbAware {

  //private static final Logger LOG = Logger.getInstance(MavenShowEffectivePom.class);

  private static void showUnsupportedNotification(@NotNull final Project project) {
    new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                     "Unsupported action",
                     "<html>You have to <a href='#'>enable</a> <b>" + CommonBundle.settingsActionPath() + " | Maven | Importing | \"Use Maven3 to import project\"</b> option to use Show Effective POM action</html>",
                     NotificationType.ERROR,
                     new NotificationListener.Adapter() {
                       @Override
                       protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                         MavenServerManager.getInstance().setUseMaven2(false);
                         notification.expire();

                         new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "Option enabled", "Option \"Use Maven3 to import project\" has been enabled", NotificationType.INFORMATION)
                           .notify(project);
                       }
                     }).notify(project);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = MavenActionUtil.getProject(event.getDataContext());

    if (MavenServerManager.getInstance().isUseMaven2()) {
      showUnsupportedNotification(project);
      return;
    }


    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(event.getDataContext());
    assert file != null;

    final MavenProject mavenProject = manager.findProject(file);
    assert mavenProject != null;

    manager.evaluateEffectivePom(mavenProject, new Consumer<String>() {
      @Override
      public void consume(final String s) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            String fileName = mavenProject.getMavenId().getArtifactId() + "-pom.xml";
            PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, XMLLanguage.INSTANCE, s);
            file.navigate(true);
          }
        });
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation p = e.getPresentation();

    boolean visible = false;

    final MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e.getDataContext());

    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file != null) {
      MavenProject mavenProject = manager.findProject(file);
      visible = mavenProject != null;
    }

    p.setVisible(visible);
  }

}
