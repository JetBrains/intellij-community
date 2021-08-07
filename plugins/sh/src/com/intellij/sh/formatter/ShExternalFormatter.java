// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.sh.ShBundle;
import com.intellij.sh.codeStyle.ShCodeStyleSettings;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.settings.ShSettings;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.intellij.sh.ShBundle.message;
import static com.intellij.sh.ShLanguage.NOTIFICATION_GROUP_ID;

public class ShExternalFormatter extends AsyncDocumentFormattingService {
  private static final Logger LOG = Logger.getInstance(ShExternalFormatter.class);
  @NonNls private static final List<String> KNOWN_SHELLS = Arrays.asList("bash", "posix", "mksh");

  private static final Set<Feature> FEATURES = EnumSet.noneOf(Feature.class);

  @Override
  public boolean canFormat(@NotNull PsiFile file) {
    return file instanceof ShFile;
  }

  @Override
  public @NotNull Set<Feature> getFeatures() {
    return FEATURES;
  }

  @Override
  protected @NotNull String getNotificationGroupId() {
    return NOTIFICATION_GROUP_ID;
  }

  @Override
  protected @NotNull String getName() {
    return message("sh.shell.script");
  }

  @Override
  protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
    FormattingContext formattingContext = request.getContext();
    Project project = formattingContext.getProject();
    String shFmtExecutable = ShSettings.getShfmtPath();
    if (!ShShfmtFormatterUtil.isValidPath(shFmtExecutable)) {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        Notification notification = new Notification(NOTIFICATION_GROUP_ID, message("sh.shell.script"), message("sh.fmt.install.question"),
                                                     NotificationType.INFORMATION);
        notification.addAction(
          NotificationAction.createSimple(ShBundle.messagePointer("sh.install"), () -> {
            notification.expire();
            ShShfmtFormatterUtil.download(formattingContext.getProject(),
                                          () -> Notifications.Bus
                                            .notify(new Notification(NOTIFICATION_GROUP_ID, message("sh.shell.script"),
                                                                     message("sh.fmt.success.install"),
                                                                     NotificationType.INFORMATION)),
                                          () -> Notifications.Bus
                                            .notify(new Notification(NOTIFICATION_GROUP_ID, message("sh.shell.script"),
                                                                     message("sh.fmt.cannot.download"),
                                                                     NotificationType.ERROR)));
          }));
        notification.addAction(NotificationAction.createSimple(ShBundle.messagePointer("sh.no.thanks"), () -> {
          notification.expire();
          ShSettings.setShfmtPath(ShSettings.I_DO_MIND_SUPPLIER.get());
        }));
        Notifications.Bus.notify(notification, project);
      }
      return null;
    }
    ShShfmtFormatterUtil.checkShfmtForUpdate(project);
    String interpreter = ShShebangParserUtil.getInterpreter((ShFile)formattingContext.getContainingFile(), KNOWN_SHELLS, "bash");

    VirtualFile file = formattingContext.getVirtualFile();
    if (file == null || !file.exists()) return null;

    CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    if (ShSettings.I_DO_MIND_SUPPLIER.get().equals(shFmtExecutable)) return null;

    String filePath = file.getPath();
    String realPath = FileUtil.toSystemDependentName(filePath);
    if (!new File(realPath).exists()) return null;

    @NonNls
    List<String> params = new SmartList<>();
    params.add("-ln=" + interpreter);
    if (!settings.useTabCharacter(file.getFileType())) {
      int tabSize = settings.getIndentSize(file.getFileType());
      params.add("-i=" + tabSize);
    }
    if (shSettings.BINARY_OPS_START_LINE) {
      params.add("-bn");
    }
    if (shSettings.SWITCH_CASES_INDENTED) {
      params.add("-ci");
    }
    if (shSettings.REDIRECT_FOLLOWED_BY_SPACE) {
      params.add("-sr");
    }
    if (shSettings.KEEP_COLUMN_ALIGNMENT_PADDING) {
      params.add("-kp");
    }
    if (shSettings.MINIFY_PROGRAM) {
      params.add("-mn");
    }
    params.add(realPath);

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine()
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withExePath(shFmtExecutable)
        .withParameters(params);

      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      return new FormattingTask() {
        @Override
        public void run() {
          handler.addProcessListener(new CapturingProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              int exitCode = event.getExitCode();
              if (exitCode == 0) {
                request.onTextReady(getOutput().getStdout());
              }
              else {
                request.onError(message("sh.shell.script"), getOutput().getStderr());
              }
            }
          });
          handler.startNotify();
        }

        @Override
        public boolean cancel() {
          handler.destroyProcess();
          return true;
        }

        @Override
        public boolean isRunUnderProgress() {
          return true;
        }
      };
    }
    catch (ExecutionException e) {
      request.onError(message("sh.shell.script"), e.getMessage());
      return null;
    }
  }

}
