package org.jetbrains.builtInWebServer;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class ConsoleManager {
  private ConsoleView console;

  @NotNull
  public ConsoleView getConsole(@NotNull NetService netService) {
    if (console == null) {
      createConsole(netService);
    }
    return console;
  }

  private void createConsole(@NotNull final NetService netService) {
    TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(netService.project);
    netService.configureConsole(consoleBuilder);
    console = consoleBuilder.getConsole();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(netService.project).registerToolWindow(netService.getConsoleToolWindowId(), false, ToolWindowAnchor.BOTTOM, netService.project, true);
        toolWindow.setIcon(netService.getConsoleToolWindowIcon());
        toolWindow.getContentManager().addContent(ContentFactory.SERVICE.getInstance().createContent(console.getComponent(), "", false));
      }
    }, netService.project.getDisposed());
  }
}