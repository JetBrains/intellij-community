// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.sh.codeStyle.ShCodeStyleSettings;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.settings.ShSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

// todo: rewrite with the future API, see IDEA-203568
public class ShExternalFormatter implements ExternalFormatProcessor {
  private static final Logger LOG = Logger.getInstance(ShExternalFormatter.class);
  private static final List<String> KNOWN_SHELLS = Arrays.asList("bash", "posix", "mksh");

  @Override
  public boolean activeForFile(@NotNull PsiFile file) {
    return file instanceof ShFile;
  }

  @Nullable
  @Override
  public TextRange format(@NotNull PsiFile source, @NotNull TextRange range, boolean canChangeWhiteSpacesOnly) {
    doFormat(source.getProject(), source.getVirtualFile());
    return range;
  }

  @Nullable
  @Override
  public String indent(@NotNull PsiFile source, int lineStartOffset) {
    return null;
  }

  private void doFormat(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null || !file.exists()) return;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    if (!(psiFile instanceof ShFile)) return;

    CodeStyleSettings settings = CodeStyle.getSettings(psiFile);

    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    String shFmtExecutable = ShSettings.getShfmtPath();
    if (ShSettings.I_DO_MIND.equals(shFmtExecutable)) return;

    if (!ShShfmtFormatterUtil.isValidPath(shFmtExecutable)) {
      Notification notification =
        new Notification("Shell Script", "", "Would you like to install a shell script formatter?", NotificationType.INFORMATION);
      notification.addAction(
        NotificationAction.createSimple("Install", () -> {
          notification.expire();
          ShShfmtFormatterUtil.download(project, settings, () -> Notifications.Bus
            .notify(new Notification("Shell Script", "", "Shell script formatter was successfully installed",
                                     NotificationType.INFORMATION)), () -> Notifications.Bus
            .notify(new Notification("Shell Script", "", "Can't download sh shfmt formatter. Please install it manually",
                                     NotificationType.ERROR)));
        }));
      notification.addAction(NotificationAction.createSimple("No, thanks", () -> {
        notification.expire();
        ShSettings.setShfmtPath(ShSettings.I_DO_MIND);
      }));
      Notifications.Bus.notify(notification);
      return;
    }

    String filePath = file.getPath();
    String realPath = FileUtil.toSystemDependentName(filePath);
    if (!new File(realPath).exists()) return;

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return;

    long before = document.getModificationStamp();
    documentManager.saveDocument(document);

    List<String> params = ContainerUtil.newSmartList();
    params.add("-ln=" + ShShebangParserUtil.getInterpreter((ShFile)psiFile, KNOWN_SHELLS, "bash"));
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

      OSProcessHandler handler = new OSProcessHandler(commandLine);
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          int exitCode = event.getExitCode();
          if (exitCode == 0) {
            String text = getOutput().getStdout();
            ApplicationManager.getApplication().invokeLater(() -> {
              long after = document.getModificationStamp();
              if (after > before) {
                // todo: document has already been changed
                return;
              }
              CommandProcessor.getInstance().executeCommand(project, () -> {
                WriteAction.run(() -> {
                  document.setText(text);
                  FileDocumentManager.getInstance().saveDocument(document);
                });
                file.putUserData(UndoConstants.FORCE_RECORD_UNDO, null);
              }, "Reformat Code with " + getId(), null, document);
            });
          }
          else {
            showFailedNotification(getOutput().getStderr());
          }
        }
      });
      ApplicationManager.getApplication().executeOnPooledThread(handler::startNotify);
    }
    catch (ExecutionException e) {
      showFailedNotification(e.getMessage());
    }
  }

  private static void showFailedNotification(String stderr) {
    // todo: add notification
    LOG.debug(stderr);
  }

  @NotNull
  @Override
  public String getId() {
    return "shfmt";
  }
}
