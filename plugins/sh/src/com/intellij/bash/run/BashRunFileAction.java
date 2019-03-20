package com.intellij.bash.run;

import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.bash.psi.BashFile;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.psi.PsiFile;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class BashRunFileAction extends DumbAwareAction {
  static final String ID = "runShellFileAction";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(PlatformDataKeys.PSI_FILE);
    if (!(file instanceof BashFile)) return;

    Project project = file.getProject();
    TerminalView terminalView = TerminalView.getInstance(project);
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window != null && window.isAvailable()) {
      ((ToolWindowImpl) window).ensureContentInitialized();
      window.activate(null);
    }
    terminalView.createNewSession(new LocalTerminalDirectRunner(project) {
      @Override
      protected PtyProcess createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
        PtyProcess process = super.createProcess(directory, commandHistoryFilePath);
        Pair<String, String> fileCommand = createCommandLine((BashFile) file);
        if (fileCommand.first != null) {
          try {
            process.getOutputStream().write(fileCommand.first.getBytes(CharsetToolkit.UTF8_CHARSET));
          }
          catch (IOException ex) {
            throw new ExecutionException("Fail to start " + fileCommand.first, ex);
          }
        }
        else {
          throw new ExecutionException(fileCommand.second, null);
        }
        return process;
      }
    });
  }

  @NotNull
  private Pair<String, String> createCommandLine(@NotNull BashFile bashFile) {
    VirtualFile virtualFile = bashFile.getVirtualFile();
    if (virtualFile == null) {
      return Pair.create(null, "Cannot run " + bashFile.getName());
    }
    if (!virtualFile.exists()) {
      return Pair.create(null, "File " + virtualFile.getPath() + " doesn't exist");
    }
    String defaultCommand = virtualFile.getPath() + "\n";
    if (VfsUtil.virtualToIoFile(virtualFile).canExecute()) {
      return Pair.create(defaultCommand, null);
    }
    ASTNode shebang = bashFile.getNode().findChildByType(BashTokenTypes.SHEBANG);
    if (shebang != null) {
      String shellPath = StringUtil.trimStart(shebang.getText(), "#!").trim();
      if (!shellPath.isEmpty() && new File(shellPath).canExecute()) {
        return Pair.create(shellPath + " " + defaultCommand, null);
      }
    }
    return Pair.create(defaultCommand, null);
  }

  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;
    if (!(e.getData(PlatformDataKeys.PSI_FILE) instanceof BashFile)) return false;
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    return window != null && window.isAvailable();
  }
}