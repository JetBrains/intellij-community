package de.plushnikov.intellij.plugin.activity;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.OuterModelsModificationTrackerManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.SingletonNotificationManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.CommonProcessors;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.util.UUID;

import static com.intellij.psi.search.GlobalSearchScope.getScopeRestrictedByFileTypes;
import static com.intellij.psi.search.GlobalSearchScope.projectScope;
import static com.intellij.util.concurrency.AppJavaExecutorUtil.executeOnPooledIoThread;

final class LombokBuildManagerListener implements BuildManagerListener {
  private final SingletonNotificationManager myNotificationManager =
    new SingletonNotificationManager(Version.PLUGIN_NAME, NotificationType.ERROR);

  @Override
  public void beforeBuildProcessStarted(@NotNull Project project, @NotNull UUID sessionId) {
    executeOnPooledIoThread(() -> {
      if (ReadAction.nonBlocking(() -> requiresAnnotationProcessing(project)).executeSynchronously()) {
        suggestEnableAnnotations(project);
      }
    });
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  private static boolean requiresAnnotationProcessing(@NotNull Project project) {
    if (project.isDisposed()) return false;
    if (hasAnnotationProcessorsEnabled(project)) return false;
    if (!LombokLibraryUtil.hasLombokLibrary(project)) return false;

    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      var processor = new CommonProcessors.FindFirstProcessor<PsiFile>();
      var scope = getScopeRestrictedByFileTypes(projectScope(project), JavaFileType.INSTANCE);

      CacheManager.getInstance(project).processFilesWithWord(processor, "lombok", UsageSearchContext.IN_CODE, scope, false);
      return new Result<>(processor.isFound(), OuterModelsModificationTrackerManager.getTracker(project));
    });
  }

  private static CompilerConfigurationImpl getCompilerConfiguration(@NotNull Project project) {
    return (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
  }

  private static boolean hasAnnotationProcessorsEnabled(@NotNull Project project) {
    final CompilerConfigurationImpl compilerConfiguration = getCompilerConfiguration(project);
    return compilerConfiguration.getDefaultProcessorProfile().isEnabled() &&
           ContainerUtil.and(compilerConfiguration.getModuleProcessorProfiles(), AnnotationProcessingConfiguration::isEnabled);
  }

  private static void enableAnnotationProcessors(@NotNull Project project) {
    CompilerConfigurationImpl compilerConfiguration = getCompilerConfiguration(project);
    compilerConfiguration.getDefaultProcessorProfile().setEnabled(true);
    compilerConfiguration.getModuleProcessorProfiles().forEach(pp -> pp.setEnabled(true));

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(
        LombokBundle.message("popup.content.java.annotation.processing.has.been.enabled"),
        MessageType.INFO,
        null
      )
      .setFadeoutTime(3000)
      .createBalloon()
      .show(RelativePoint.getNorthEastOf(statusBar.getComponent()), Balloon.Position.atRight);
  }

  private void suggestEnableAnnotations(Project project) {
    myNotificationManager.notify("", LombokBundle.message("config.warn.annotation-processing.disabled.title"), project, (notification) -> {
      notification.setSuggestionType(true);
      notification.addAction(new NotificationAction(LombokBundle.message("notification.enable.annotation.processing")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          enableAnnotationProcessors(project);
          notification.expire();
        }
      });
    });
  }
}
