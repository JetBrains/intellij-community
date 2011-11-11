package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PythonConsoleView extends LanguageConsoleViewImpl implements PyCodeExecutor {
  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);

  private Project myProject;
  private PydevConsoleExecuteActionHandler myExecuteActionHandler;
  private ConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput = false;
  private PyHighlighter myPyHighlighter;
  private EditorColorsScheme myScheme;
  private boolean myHyperlink;

  public PythonConsoleView(final Project project, final String title, Sdk sdk) {
    super(project, new PythonLanguageConsole(project, title, sdk));
    getPythonLanguageConsole().setPythonConsoleView(this);
    getPythonLanguageConsole().setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
    setUpdateFoldingsEnabled(false);
    myProject = project;
    //noinspection ConstantConditions
    myPyHighlighter = new PyHighlighter(sdk != null && sdk.getVersionString() != null ? LanguageLevel.fromPythonVersion(sdk.getVersionString()) : LanguageLevel.getDefault());
    myScheme = getPythonLanguageConsole().getConsoleEditor().getColorsScheme();
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getPythonLanguageConsole().setConsoleCommunication(communication);
  }

  public void setExecutionHandler(@NotNull PydevConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public void requestFocus() {
    IdeFocusManager.findInstance().requestFocus(getPythonLanguageConsole().getConsoleEditor().getContentComponent(), true);
  }

  private PythonLanguageConsole getPythonLanguageConsole() {
    return ((PythonLanguageConsole)myConsole);
  }

  @Override
  public void executeCode(final @NotNull String code) {
    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Executing code in console...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
          if (indicator.isCanceled()) {
            break;
          }
          try {
            Thread.sleep(300);
          }
          catch (InterruptedException e) {
          }
        }
        if (!indicator.isCanceled()) {
          doExecute(code);
        }
      }
    });
  }


  private void doExecute(String code) {
    executeInConsole(PyConsoleIndentUtil.normalize(code));
  }

  public void executeInConsole(final String code) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        getPythonLanguageConsole().addTextToCurrentEditor(code);
        myExecuteActionHandler.runExecuteAction(getPythonLanguageConsole());
        myExecuteActionHandler.finishExecution();
      }
    });
  }

  public void executeStatement(@NotNull String statement, @NotNull final Key attributes) {
    printText(statement, attributes);
    myExecuteActionHandler.processLine(statement, true);
  }

  public void printText(String text, final Key attributes) {
    if (PyConsoleUtil.detectIPythonEnd(text)) {
      myIsIPythonOutput = false;
      mySourceHighlighter = null;
    }
    else if (PyConsoleUtil.detectIPythonStart(text)) {
      myIsIPythonOutput = true;
    }
    else {
      if (mySourceHighlighter == null || attributes == ProcessOutputTypes.STDERR) {
        if (myHyperlink) {
          printHyperlink(text, attributes);
        }
        else {
          //Print text normally with converted attributes
          print(text, outputTypeForAttributes(attributes));
        }
        myHyperlink = detectHyperlink(text, attributes);
        if (mySourceHighlighter == null && myIsIPythonOutput && PyConsoleUtil.detectSourcePrinting(text)) {
          mySourceHighlighter = new ConsoleSourceHighlighter(this, myScheme, myPyHighlighter);
        }
      }
      else {
        try {
          mySourceHighlighter.printHighlightedSource(text);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  private boolean detectHyperlink(@NotNull String text, @NotNull Key attributes) {
    return myIsIPythonOutput && text.startsWith("File:");
  }

  private void printHyperlink(@NotNull String text, @NotNull Key attributes) {
    if (!StringUtil.isEmpty(text)) {
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(text.trim());

      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(myProject, vFile, -1);

        printHyperlink(text, hyperlink);
      }
      else {
        print(text, outputTypeForAttributes(attributes));
      }
    }
  }

  public ConsoleViewContentType outputTypeForAttributes(Key attributes) {
    final ConsoleViewContentType outputType;
    if (attributes == ProcessOutputTypes.STDERR) {
      outputType = ConsoleViewContentType.ERROR_OUTPUT;
    }
    else if (attributes == ProcessOutputTypes.SYSTEM) {
      outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
    }
    else {
      outputType = ConsoleViewContentType.getConsoleViewType(attributes);
    }

    return outputType;
  }

  private static class PythonLanguageConsole extends LanguageConsoleImpl {
    private PythonConsoleView myPythonConsoleView;

    public PythonLanguageConsole(final Project project, final String title, final Sdk sdk) {
      super(project, title, PythonLanguage.getInstance(), false);
      initLanguageLevel(sdk);
      // Mark editor as console one, to prevent autopopup completion
      getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());

      setShowSeparatorLine(PyConsoleOptionsProvider.getInstance(project).isShowSeparatorLine());

      initComponents();
    }

    private void initLanguageLevel(@Nullable Sdk sdk) {
      if (myFile.getVirtualFile() != null) {
        //noinspection ConstantConditions
        myFile.getVirtualFile().putUserData(LanguageLevel.KEY, PythonSdkType.getLanguageLevelForSdk(sdk));
      }
    }

    public void setPythonConsoleView(PythonConsoleView pythonConsoleView) {
      myPythonConsoleView = pythonConsoleView;
    }

    public void setConsoleCommunication(final ConsoleCommunication communication) {
      myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
    }

    @Override
    protected void appendToHistoryDocument(@NotNull Document history, @NotNull String text) {
      myPythonConsoleView.beforeExternalAddContentToDocument(text.length(), ConsoleViewContentType.NORMAL_OUTPUT);
      super.appendToHistoryDocument(history, text);
    }
  }

  @Override
  protected EditorEx createRealEditor() {
    EditorEx editor = myConsole.getHistoryViewer();
    editor.setHighlighter(createHighlighter());
    return editor;
  }
}
