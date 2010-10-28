package com.jetbrains.python.console;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.codeStyle.HelperFactory;
import com.intellij.util.net.NetUtils;
import com.jetbrains.django.run.Runner;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends AbstractConsoleRunnerWithHistory {
  private static final String DOUBLE_QUOTE_MULTILINE = "\"\"\"";
  private static final String SINGLE_QUOTE_MULTILINE = "'''";
  private final int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  public static Key<PydevConsoleCommunication> CONSOLE_KEY = new Key<PydevConsoleCommunication>("PYDEV_CONSOLE_KEY");
  private static final String PYTHON_ENV_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n";
  private Helper myHelper;
  private int currentPythonIndentSize;
  private int myCurrentIndentSize = -1;
  private StringBuilder myInputBuffer;
  private String myInMultilineStringState = null;

  protected PydevConsoleRunner(@NotNull final Project project,
                               @NotNull final String consoleTitle,
                               @NotNull final CommandLineArgumentsProvider provider,
                               @Nullable final String workingDir,
                               int[] ports) {
    super(project, consoleTitle, provider, workingDir);
    myPorts = ports;
    myHelper = HelperFactory.createHelper(PythonFileType.INSTANCE, myProject);
    currentPythonIndentSize = CodeStyleSettingsManager.getSettings(myProject).getIndentSize(PythonFileType.INSTANCE);
  }

  public static void run(@NotNull final Project project,
                         @NotNull final Sdk sdk,
                         final String consoleTitle,
                         final String projectRoot,
                         final String ... statements2execute) {
    final int[] ports;
    try {
      // File "pydev/console/pydevconsole.py", line 223, in <module>
      // port, client_port = sys.argv[1:3]
      ports = NetUtils.findAvailableSocketPorts(2);
    }
    catch (IOException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
      return;
    }
    final ArrayList<String> args = new ArrayList<String>();
    args.add(sdk.getHomePath());
    final String versionString = sdk.getVersionString();
    if (versionString == null || !versionString.toLowerCase().contains("jython")){
      args.add("-u");
    }
    args.add(PythonHelpersLocator.getHelperPath("pydev/console/pydevconsole.py"));
    for (int port : ports) {
      args.add(String.valueOf(port));
    }
    final CommandLineArgumentsProvider provider = new CommandLineArgumentsProvider() {
      public String[] getArguments() {
        return args.toArray(new String[args.size()]);
      }

      public boolean passParentEnvs() {
        return false;
      }

      public Map<String, String> getAdditionalEnvs() {
        return ImmutableMap.of("PYTHONIOENCODING", "utf-8");
      }
    };

    final PydevConsoleRunner consoleRunner = new PydevConsoleRunner(project, consoleTitle, provider, projectRoot, ports);
    try {
      consoleRunner.initAndRun(statements2execute);
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  @Override
  protected LanguageConsoleViewImpl createConsoleView() {
    return new PydevLanguageConsoleView(myProject, myConsoleTitle);
  }

  @Override
  protected Process createProcess() throws ExecutionException {
    final Process server = Runner.createProcess(myWorkingDir, myProvider.getAdditionalEnvs(), myProvider.getArguments());
    try {
      myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], server, myPorts[1]);
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage());
    }
    return server;
  }

  protected PyConsoleProcessHandler createProcessHandler(final Process process) {
    return new PyConsoleProcessHandler(process, myConsoleView.getConsole(), getProviderCommandLine(myProvider), CharsetToolkit.UTF8_CHARSET);
  }

  public void initAndRun(final String ... statements2execute) throws ExecutionException {
    super.initAndRun();

    // Propagate console communication to language console
    ((PydevLanguageConsoleView)myConsoleView).setPydevConsoleCommunication(myPydevConsoleCommunication);

    // Required timeout for establishing socket connection
    try {
      Thread.sleep(3000);
    }
    catch (InterruptedException e) {
      // Ignore
    }

    // Make executed statements visible to developers
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    PyConsoleHighlightingUtil.processOutput(console, PYTHON_ENV_COMMAND, ProcessOutputTypes.SYSTEM);
    processLine(PYTHON_ENV_COMMAND);
    for (String statement : statements2execute) {
      PyConsoleHighlightingUtil.processOutput(console, statement + "\n", ProcessOutputTypes.SYSTEM);
      processLine(statement+"\n");
    }
  }

  @Override
  protected AnAction createStopAction() {
    final AnAction generalStopAction = super.createStopAction();
    final AnAction stopAction = new AnAction() {
      @Override
      public void update(AnActionEvent e) {
        generalStopAction.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myPydevConsoleCommunication != null) {
          final AnActionEvent furtherActionEvent =
            new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(),
                              e.getPresentation(), e.getActionManager(), e.getModifiers());
          try {
            myPydevConsoleCommunication.close();
            // waiting for REPL communication before destroying process handler
            Thread.sleep(300);
          }
          catch (Exception e1) {
            // Ignore
          }
          generalStopAction.actionPerformed(furtherActionEvent);
        }
      }
    };
    stopAction.copyFrom(generalStopAction);
    return stopAction;
  }

  @Override
  public void processLine(final String line) {
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    final Editor currentEditor = console.getCurrentEditor();

    if (myInputBuffer == null){
      myInputBuffer = new StringBuilder();
    }
    myInputBuffer.append(line).append("\n");

    // multiline strings handling
    if (myInMultilineStringState != null){
      if (line.contains(myInMultilineStringState)) {
        myInMultilineStringState = null;
        // restore language
        console.setLanguage(PythonLanguage.getInstance());
        console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
      } else {
        return;
      }
    }
    else {
      if (line.contains(DOUBLE_QUOTE_MULTILINE)) {
        myInMultilineStringState = DOUBLE_QUOTE_MULTILINE;
      }
      else if (line.contains(SINGLE_QUOTE_MULTILINE)){
        myInMultilineStringState = SINGLE_QUOTE_MULTILINE;
      }
      if (myInMultilineStringState != null) {
        // change language
        console.setLanguage(PlainTextLanguage.INSTANCE);
        console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
        return;
      }
    }

    // Process line continuation
    if (line.endsWith("\\")){
      console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
      return;
    }

    if (myCurrentIndentSize != -1) {
      final int indent = myHelper.getIndent(line, false);
      if (indent >= myCurrentIndentSize) {
        indentEditor(currentEditor, indent);
        scrollDown(currentEditor);
        return;
      }
    }

    if (myPydevConsoleCommunication != null){
      final boolean waitedForInputBefore = myPydevConsoleCommunication.waitingForInput;
      myPydevConsoleCommunication.execInterpreter(myInputBuffer.toString(), new ICallback<Object, InterpreterResponse>() {
        public Object call(final InterpreterResponse interpreterResponse) {
          // clear
          myInputBuffer = null;
          // Handle prompt
          if (interpreterResponse.need_input){
            if (!PyConsoleHighlightingUtil.INPUT_PROMPT.equals(console.getPrompt())){
              console.setPrompt(PyConsoleHighlightingUtil.INPUT_PROMPT);
              scrollDown(currentEditor);
            }
            myCurrentIndentSize = -1;
          }
          else if (interpreterResponse.more) {
            if (!PyConsoleHighlightingUtil.INDENT_PROMPT.equals(console.getPrompt())){
              console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
              scrollDown(currentEditor);
            }
            if (myCurrentIndentSize == -1) {
              // compute current indentation
              myCurrentIndentSize = myHelper.getIndent(line, false) + currentPythonIndentSize;
              // In this case we can insert indent automatically
              indentEditor(currentEditor, myCurrentIndentSize);
            }
          } else {
            if (!PyConsoleHighlightingUtil.ORDINARY_PROMPT.equals(console.getPrompt())){
              console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
              scrollDown(currentEditor);
            }
            myCurrentIndentSize = -1;
          }

          // Handle output
          if (!StringUtil.isEmpty(interpreterResponse.err)){
            PyConsoleHighlightingUtil.processOutput(console, interpreterResponse.err, ProcessOutputTypes.STDERR);
          } else {
            PyConsoleHighlightingUtil.processOutput(console, interpreterResponse.out, ProcessOutputTypes.STDOUT);
          }
          return null;
        }
      });
      // After requesting input we got no call back to change prompt, change it manually
      if (waitedForInputBefore && !myPydevConsoleCommunication.waitingForInput){
        console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
        scrollDown(currentEditor);
      }
    }
  }

  private void indentEditor(final Editor editor, final int indentSize) {
    new WriteCommandAction(myProject) {
      @Override
      protected void run(Result result) throws Throwable {
        EditorModificationUtil.insertStringAtCaret(editor, myHelper.fillIndent(indentSize));
      }
    }.execute();
  }

  private void scrollDown(final Editor currentEditor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        currentEditor.getCaretModel().moveToOffset(currentEditor.getDocument().getTextLength());
      }
    });
  }

  public static boolean isInPydevConsole(final PsiElement element){
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  @Nullable
  public static PydevConsoleCommunication getConsoleCommunication(final PsiElement element) {
    return element.getContainingFile().getCopyableUserData(CONSOLE_KEY);
  }
}
