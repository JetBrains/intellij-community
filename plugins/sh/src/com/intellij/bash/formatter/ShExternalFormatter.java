package com.intellij.bash.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.bash.codeStyle.ShCodeStyleSettings;
import com.intellij.bash.lexer.ShTokenTypes;
import com.intellij.bash.parser.ShShebangParserUtil;
import com.intellij.bash.psi.ShFile;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.lang.ASTNode;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

// todo: rewrite with the future API, see IDEA-203568
public class ShExternalFormatter implements ExternalFormatProcessor {
  private final static Logger LOG = Logger.getInstance(ShExternalFormatter.class);
  private static final List<String> KNOWN_SHELLS = ContainerUtil.list("bash", "posix", "mksh");

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

  private void doFormat(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null || !file.exists()) return;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    CodeStyleSettings settings = psiFile == null ? null : CodeStyle.getSettings(psiFile);
    if (settings == null) return;

    ShCodeStyleSettings bashSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    String shFmtExecutable = bashSettings.SHFMT_PATH;
    if (!ShShfmtFormatterUtil.isValidPath(shFmtExecutable)) {
      Notification notification = new Notification("Shell Script", "", "Shell script formatter not installed or incorrect path", NotificationType.WARNING);
      notification.addAction(NotificationAction.createSimple("Download", () -> ShShfmtFormatterUtil.download(project, settings, null)));
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

    String interpreter = "bash";
    ASTNode shebang = psiFile.getNode().findChildByType(ShTokenTypes.SHEBANG);
    if (shebang != null) {
      String detectedInterpreter = ShShebangParserUtil.getInterpreter(shebang.getText());
      if (KNOWN_SHELLS.contains(detectedInterpreter)) {
        interpreter = detectedInterpreter;
      }
    }

    List<String> params = ContainerUtil.newSmartList();
    params.add("-ln=" + interpreter);
    if (!settings.useTabCharacter(file.getFileType())) {
      int tabSize = settings.getIndentSize(file.getFileType());
      params.add("-i=" + tabSize);
    }
    if (bashSettings.BINARY_OPS_START_LINE) {
      params.add("-bn");
    }
    if (bashSettings.SWITCH_CASES_INDENTED) {
      params.add("-ci");
    }
    if (bashSettings.REDIRECT_FOLLOWED_BY_SPACE) {
      params.add("-sr");
    }
    if (bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING) {
      params.add("-kp");
    }
    if (bashSettings.MINIFY_PROGRAM) {
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
      handler.startNotify();
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
