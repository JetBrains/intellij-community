package org.jetbrains.idea.maven.project.actions;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class MavenShowEffectivePom extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance(MavenShowEffectivePom.class);

  private static void showUnsupportedNotification(@NotNull final Project project) {
    new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                     "Unsupported action",
                     "<html>Maven3 required to use Show Effective POM action. \n" +
                     "Please <a href='#'>select Maven3 home directory</a> or use \"Bundled (Maven 3)\"</html>",
                     NotificationType.ERROR,
                     new NotificationListener.Adapter() {
                       @Override
                       protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                         notification.expire();
                         ShowSettingsUtil.getInstance().showSettingsDialog(project, MavenSettings.DISPLAY_NAME);
                       }
                     }).notify(project);
  }

  public static void actionPerformed(@NotNull final Project project, @NotNull final VirtualFile file) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    final MavenProject mavenProject = manager.findProject(file);
    assert mavenProject != null;

    manager.evaluateEffectivePom(mavenProject, new NullableConsumer<String>() {
      @Override
      public void consume(final String s) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (project.isDisposed()) return;

            if (s == null) { // null means UnsupportedOperationException
              new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                               "Error",
                               "Failed to evaluate effective pom.",
                               NotificationType.ERROR).notify(project);
              return;
            }

            String fileName = mavenProject.getMavenId().getArtifactId() + "-effective-pom.xml";
            PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, XMLLanguage.INSTANCE, s);
            try {
              //noinspection ConstantConditions
              file.getVirtualFile().setWritable(false);
            }
            catch (IOException e) {
              LOG.error(e);
            }

            file.navigate(true);
          }
        });
      }
    });
  }

  @Nullable
  private static VirtualFile findPomXml(@NotNull DataContext dataContext) {
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return null;

    if (file.isDirectory()) {
      file = file.findChild("pom.xml");
      if (file == null) return null;
    }

    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(dataContext);
    if(manager == null) return null;
    MavenProject mavenProject = manager.findProject(file);
    if (mavenProject == null) return null;

    return file;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = MavenActionUtil.getProject(event.getDataContext());
    if(project == null) return;
    final VirtualFile file = findPomXml(event.getDataContext());
    if (file == null) return;

    if (MavenServerManager.getInstance().isUseMaven2()) {
      showUnsupportedNotification(project);
    }
    else {
      actionPerformed(project, file);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();

    boolean visible = findPomXml(e.getDataContext()) != null;

    p.setVisible(visible);
  }

}
