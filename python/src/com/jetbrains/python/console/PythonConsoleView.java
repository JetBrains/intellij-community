// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.JBSplitter;
import com.intellij.util.TimeoutUtil;
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugValueDescriptor;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.testing.PyTestsSharedKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PythonConsoleView extends LanguageConsoleImpl implements ObservableConsoleView, PyCodeExecutor {
  static Key<Boolean> CONSOLE_KEY = new Key<>("PYDEV_CONSOLE_KEY");
  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);
  private final boolean myTestMode;

  private PythonConsoleExecuteActionHandler myExecuteActionHandler;
  private PyConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput;
  private final PyHighlighter myPyHighlighter;
  private final EditorColorsScheme myScheme;
  private boolean myHyperlink;

  private XStandaloneVariablesView mySplitView;
  private final ActionCallback myInitialized = new ActionCallback();
  private boolean isShowVars;
  @Nullable private String mySdkHomePath;

  private final Map<String, Map<String, PyDebugValueDescriptor>> myDescriptorsCache = Maps.newConcurrentMap();

  /**
   * @param testMode this console will be used to display test output and should support TC messages
   */
  public PythonConsoleView(final Project project, final String title, @Nullable final Sdk sdk, final boolean testMode) {
    super(project, title, PythonLanguage.getInstance());
    myTestMode = testMode;
    isShowVars = PyConsoleOptions.getInstance(project).isShowVariableByDefault();
    VirtualFile virtualFile = getVirtualFile();
    PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, PythonSdkType.getLanguageLevelForSdk(sdk));
    virtualFile.putUserData(CONSOLE_KEY, true);
    // Mark editor as console one, to prevent autopopup completion
    getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());
    getHistoryViewer().putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true);
    super.setPrompt(null);
    setUpdateFoldingsEnabled(false);
    LanguageLevel languageLevel = LanguageLevel.getDefault();
    if (sdk != null) {
      final PythonSdkFlavor sdkFlavor = PythonSdkFlavor.getFlavor(sdk);
      if (sdkFlavor != null) {
        languageLevel = sdkFlavor.getLanguageLevel(sdk);
      }
      mySdkHomePath = sdk.getHomePath();
    }
    myPyHighlighter = new PyHighlighter(languageLevel);
    myScheme = getConsoleEditor().getColorsScheme();
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY, communication);

    if (isShowVars && communication instanceof PydevConsoleCommunication) {
      showVariables((PydevConsoleCommunication)communication);
    }
  }

  /**
   * Add folding to Console view
   *
   * @param addOnce If true, folding will be added once when an appropriate area is found.
   *                Otherwise folding can be expanded by newly added text.
   */
  @Nullable
  private PyConsoleStartFolding createConsoleFolding(boolean addOnce) {
    PyConsoleStartFolding startFolding = new PyConsoleStartFolding(this, addOnce);
    myExecuteActionHandler.getConsoleCommunication().addCommunicationListener(startFolding);
    Editor editor = getEditor();
    if (editor == null) {
      return null;
    }
    editor.getDocument().addDocumentListener(startFolding);
    ((FoldingModelEx)editor.getFoldingModel()).addListener(startFolding, this);
    return startFolding;
  }

  public void addConsoleFolding(boolean isDebugConsole, boolean addOnce) {
    try {
      if (isDebugConsole && myExecuteActionHandler != null && getEditor() != null) {
        PyConsoleStartFolding folding = createConsoleFolding(addOnce);
        if (folding != null) {
          // in debug console we should add folding from the place where the folding was turned on
          folding.setStartLineOffset(getEditor().getDocument().getTextLength());
          folding.setNumberOfCommandToStop(2);
        }
      }
      else {
        myInitialized.doWhenDone(() -> createConsoleFolding(addOnce));
      }
    }
    catch (Exception e) {
      LOG.error(e.getMessage());
    }
  }

  public void setExecutionHandler(@NotNull PythonConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public PythonConsoleExecuteActionHandler getExecuteActionHandler() {
    return myExecuteActionHandler;
  }

  public void setConsoleEnabled(boolean flag) {
    if (myExecuteActionHandler != null) {
      myExecuteActionHandler.setEnabled(flag);
    }
    else {
      myInitialized.doWhenDone(() -> myExecuteActionHandler.setEnabled(flag));
    }
  }

  public void inputRequested() {
    if (myExecuteActionHandler != null) {
      final ConsoleCommunication consoleCommunication = myExecuteActionHandler.getConsoleCommunication();
      if (consoleCommunication instanceof PythonDebugConsoleCommunication) {
        consoleCommunication.notifyInputRequested();
      }
    }
  }

  public void inputReceived() {
    // If user's input was entered while debug console was turned off, we shouldn't wait for it anymore
    if (myExecuteActionHandler != null) {
      myExecuteActionHandler.getConsoleCommunication().notifyInputReceived();
    }
  }

  @Override
  public void requestFocus() {
    myInitialized.doWhenDone(() ->
                               IdeFocusManager.getGlobalInstance().requestFocus(getConsoleEditor().getContentComponent(), true));
  }

  @Override
  public void executeCode(final @Nullable String code, @Nullable final Editor editor) {
    myInitialized.doWhenDone(
      () -> {
        if (code != null) {
          ProgressManager.getInstance().run(new Task.Backgroundable(null, PyBundle.message("console.executing.code.in.console"), true) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
              while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
                if (indicator.isCanceled()) {
                  break;
                }
                TimeoutUtil.sleep(300);
              }
              if (!indicator.isCanceled()) {
                executeInConsole(code);
              }
            }
          });
        }
        else {
          requestFocus();
        }
      }
    );
  }


  public void executeInConsole(@NotNull final String code) {
    CountDownLatch latch = new CountDownLatch(1);

    TransactionGuard.submitTransaction(this, () -> {
      final String codeToExecute = code.endsWith("\n") || myExecuteActionHandler.checkSingleLine(code) ? code : code + "\n";
      DocumentEx document = getConsoleEditor().getDocument();
      String oldText = document.getText();
      ApplicationManager.getApplication().runWriteAction(() -> {
        setInputText(codeToExecute);
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
        if (psiFile != null) {
          CommandProcessor.getInstance().runUndoTransparentAction(() ->
                                                                    CodeStyleManager.getInstance(getProject())
                                                                                    .adjustLineIndent(psiFile,
                                                                                                      new TextRange(0, psiFile
                                                                                                        .getTextLength())));
        }
        int oldOffset = getConsoleEditor().getCaretModel().getOffset();
        getConsoleEditor().getCaretModel().moveToOffset(document.getTextLength());
        myExecuteActionHandler.runExecuteAction(this);

        if (!StringUtil.isEmpty(oldText)) {
          ApplicationManager.getApplication().runWriteAction(() -> setInputText(oldText));
          getConsoleEditor().getCaretModel().moveToOffset(oldOffset);
        }
      });

      latch.countDown();
    });

    try {
      latch.await(1, TimeUnit.MINUTES);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void executeStatement(@NotNull String statement, @NotNull final Key attributes) {
    print(statement, outputTypeForAttributes(attributes));
    myExecuteActionHandler.processLine(statement);
  }

  public void printText(String text, final ConsoleViewContentType outputType) {
    super.print(text, outputType);
  }

  public void print(String text, @NotNull final Key attributes) {
    print(text, outputTypeForAttributes(attributes));
  }

  @Override
  public void print(@NotNull String text, @NotNull final ConsoleViewContentType outputType) {
    if (myTestMode) {
      text = PyTestsSharedKt.processTCMessage(text);
    }
    detectIPython(text, outputType);
    if (PyConsoleUtil.detectIPythonEnd(text)) {
      myIsIPythonOutput = false;
      mySourceHighlighter = null;
    }
    else if (PyConsoleUtil.detectIPythonStart(text)) {
      myIsIPythonOutput = true;
    }
    else {
      if (mySourceHighlighter == null || outputType == ConsoleViewContentType.ERROR_OUTPUT) {
        if (myHyperlink) {
          printHyperlink(text, outputType);
        }
        else {
          //Print text normally with converted attributes
          super.print(text, outputType);
        }
        myHyperlink = detectHyperlink(text);
        if (mySourceHighlighter == null && myIsIPythonOutput && PyConsoleUtil.detectSourcePrinting(text)) {
          mySourceHighlighter = new PyConsoleSourceHighlighter(this, myScheme, myPyHighlighter);
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

  public void detectIPython(String text, final ConsoleViewContentType outputType) {
    VirtualFile file = getVirtualFile();
    if (PyConsoleUtil.detectIPythonImported(text, outputType)) {
      PyConsoleUtil.markIPython(file);
      PythonConsoleExecuteActionHandler handler = getExecuteActionHandler();
      if (handler != null) {
        handler.updateConsoleState();
      }
    }
    if (PyConsoleUtil.detectIPythonAutomagicOn(text)) {
      PyConsoleUtil.setIPythonAutomagic(file, true);
    }
    if (PyConsoleUtil.detectIPythonAutomagicOff(text)) {
      PyConsoleUtil.setIPythonAutomagic(file, false);
    }
  }

  private boolean detectHyperlink(@NotNull String text) {
    return myIsIPythonOutput && text.startsWith("File:");
  }

  private void printHyperlink(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (!StringUtil.isEmpty(text)) {
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(text.trim());

      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(getProject(), vFile, -1);

        super.printHyperlink(text, hyperlink);
      }
      else {
        super.print(text, contentType);
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

  public void setSdk(Sdk sdk) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_SDK, sdk);
  }

  public void showVariables(PydevConsoleCommunication consoleCommunication) {
    PyStackFrame stackFrame = new PyStackFrame(getProject(), consoleCommunication, new PyStackFrameInfo("", "", "", null), null);
    stackFrame.restoreChildrenDescriptors(myDescriptorsCache);
    final XStandaloneVariablesView view = new XStandaloneVariablesView(getProject(), new PyDebuggerEditorsProvider(), stackFrame);
    consoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        view.rebuildView();
      }

      @Override
      public void inputRequested() {
      }
    });
    mySplitView = view;
    Disposer.register(this, view);
    splitWindow();
  }

  @NotNull
  @Override
  protected JComponent createCenterComponent() {
    //workaround for extra lines appearing in the console
    JComponent centerComponent = super.createCenterComponent();
    getHistoryViewer().getSettings().setAdditionalLinesCount(0);
    getHistoryViewer().getSettings().setUseSoftWraps(false);
    getConsoleEditor().getGutterComponentEx().setBackground(getConsoleEditor().getBackgroundColor());
    getConsoleEditor().getGutterComponentEx().revalidate();
    getConsoleEditor().getColorsScheme().setColor(EditorColors.GUTTER_BACKGROUND, getConsoleEditor().getBackgroundColor());

    // settings.set
    return centerComponent;
  }


  private void splitWindow() {
    Component console = getComponent(0);
    removeAll();
    JBSplitter p = new JBSplitter(false, 2f / 3);
    p.setFirstComponent((JComponent)console);
    p.setSecondComponent(mySplitView.getPanel());
    p.setShowDividerControls(true);
    p.setHonorComponentsMinimumSize(true);

    add(p, BorderLayout.CENTER);
    validate();
    repaint();
  }

  public void restoreWindow() {
    Component component = getComponent(0);
    if (mySplitView != null && component instanceof JBSplitter) {
      JBSplitter pane = (JBSplitter)component;
      removeAll();
      Disposer.dispose(mySplitView);
      mySplitView = null;
      add(pane.getFirstComponent(), BorderLayout.CENTER);
      validate();
      repaint();
    }
  }

  @Nullable
  public String getSdkHomePath() {
    return mySdkHomePath;
  }

  public boolean isInitialized() {
    return myInitialized.isDone();
  }

  public void initialized() {
    myInitialized.setDone();
  }

  public void setShowVars(boolean showVars) {
    isShowVars = showVars;
  }

  public boolean isShowVars() {
    return isShowVars;
  }

  public void whenInitialized(Runnable runnable) {
    myInitialized.doWhenDone(runnable);
  }

  @Nullable
  @TestOnly
  public XDebuggerTreeNode getDebuggerTreeRootNode() {
    return mySplitView.getTree().getRoot();
  }
}
