package com.jetbrains.python.console;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.*;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairProcessor;
import com.jetbrains.django.run.Runner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author oleg
 */
public class PyConsoleRunner extends AbstractConsoleRunnerWithHistory {
  public PyConsoleRunner(@NotNull final Project project,
                         @NotNull final String consoleTitle,
                         @NotNull final CommandLineArgumentsProvider provider,
                         @Nullable final String workingDir) {
    super(project, consoleTitle, provider, workingDir);
  }

  public static void run(@NotNull final Project project,
                         @NotNull final String consoleTitle,
                         @NotNull final CommandLineArgumentsProvider provider,
                         @Nullable final String workingDir) {

    final PyConsoleRunner consoleRunner = new PyConsoleRunner(project, consoleTitle, provider, workingDir);
    try {
      consoleRunner.initAndRun();
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  protected LanguageConsoleViewImpl createConsoleView() {
    return new PyLanguageConsoleView(myProject, myConsoleTitle);
  }


  @Nullable
  protected Process createProcess() throws ExecutionException {
    return Runner.createProcess(myWorkingDir, true, myProvider.getAdditionalEnvs(), myProvider.getArguments());
  }

  protected PyConsoleProcessHandler createProcessHandler(final Process process) {
    final Charset outputEncoding = EncodingManager.getInstance().getDefaultCharset();
    return new PyConsoleProcessHandler(process, myConsoleView.getConsole(), getProviderCommandLine(myProvider), outputEncoding);
  }

  protected void sendInput(final String input) {
    super.sendInput(input);
    ((PyLanguageConsoleView)myConsoleView).inputSent(input);
  }
}