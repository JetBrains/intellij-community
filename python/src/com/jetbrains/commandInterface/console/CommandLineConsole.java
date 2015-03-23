/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.commandInterface.console;

import com.intellij.execution.console.LanguageConsoleBuilder;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.Consumer;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Displays command-line console for user.
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineConsole {

  private CommandLineConsole() {
  }

  /**
   * Creates and displays command-line console for user.
   *
   * @param module      module to display console for.
   * @param consoleName Console name (would be used in prompt, history etc)
   * @param commandList list of commands available for this console. You may pass null here, but in this case no validation nor suggestion
   *                    would work. Additionaly, no executor would be registered, so you will need to use
   *                    {@link LanguageConsoleBuilder#registerExecuteAction(LanguageConsoleView, Consumer, String, String, Condition)}
   *                    by yourself passing this method result as arg to enable execution, history etc.
   * @return newly created console. You do not need to do anything with this value to display console: it will be displayed automatically
   */
  @NotNull
  public static LanguageConsoleView createConsole(
    @NotNull final Module module,
    @NotNull final String consoleName,
    @Nullable final List<Command> commandList) {
    final Project project = module.getProject();
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow window = toolWindowManager.getToolWindow(consoleName);
    if (window == null) {
      window = toolWindowManager.registerToolWindow(consoleName, true, ToolWindowAnchor.BOTTOM);
    }
    window.activate(null);

    final ContentManager contentManager = window.getContentManager();
    contentManager.removeAllContents(true);
    final LanguageConsoleImpl console = new LanguageConsoleImpl(project, "", CommandLineLanguage.INSTANCE);
    console.setPrompt(consoleName + " > ");
    console.setEditable(true);

    final CommandLineFile file = PyUtil.as(console.getFile(), CommandLineFile.class);
    if (file != null && commandList != null) {
      file.setCommands(commandList);
      LanguageConsoleBuilder.registerExecuteAction(console, new CommandExecutor(commandList, module), consoleName, consoleName, null);
    }


    final Content content = new ContentImpl(console.getComponent(), "", true);
    contentManager.addContent(content);

    showHiddenCommandWorkAround(console);
    ArgumentHintLayer.attach(console); // Display [arguments]
    return console;
  }

  /**
   * TODO: Investigate why do we need this hack
   * For some reason console is not displayed correctly unless we type something to it (i.e. space)
   *
   * @param console console to type space
   */
  private static void showHiddenCommandWorkAround(@NotNull final LanguageConsoleView console) {
    console.getComponent().setVisible(true);
    CommandProcessor.getInstance().executeCommand(null, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            console.getEditorDocument().insertString(0, " ");
            console.getComponent().grabFocus();
          }
        });
      }
    }, null, null);
  }
}

