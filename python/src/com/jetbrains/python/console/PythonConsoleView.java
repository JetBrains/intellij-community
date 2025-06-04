// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.console.actions.CommandQueueListener;
import com.jetbrains.python.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.console.pythonCommandQueue.PythonCommandQueuePanel;
import com.jetbrains.python.debugger.PyDebugValueDescriptor;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.testing.PyTestsSharedKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.jetbrains.python.console.PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY;

public class PythonConsoleView extends LanguageConsoleImpl implements ObservableConsoleView, PyCodeExecutor, PyTargetedCodeExecutor {
  public static final Key<Boolean> CONSOLE_KEY = new Key<>("PYDEV_CONSOLE_KEY");
  private static final Key<Map<Integer, Integer>> COUNTER_LINE_NUMBER = new Key<>("PYDEV_COUNTER_LINE_NUMBER");
  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);
  private final boolean myTestMode;

  private PythonConsoleExecuteActionHandler myExecuteActionHandler;
  private PyConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput;
  private final PyHighlighter myPyHighlighter;
  private boolean myHyperlink;

  private XStandaloneVariablesView mySplitView;
  private final ActionCallback myInitialized = new ActionCallback();
  private boolean isShowVars;
  private @Nullable String mySdkHomePath;
  private PydevConsoleRunner myRunner;

  private final Map<String, Map<String, PyDebugValueDescriptor>> myDescriptorsCache = Maps.newConcurrentMap();

  private final PythonCommandQueuePanel myCommandQueuePanel = new PythonCommandQueuePanel(this);
  private JBPopup myCommandQueue;
  private Dimension commandQueueDimension;
  private boolean isShowQueue;

  private @Nullable OnClearCallback myOnClearCallback;

  private ActionToolbar myToolbar;
  private boolean myIsToolwindowHorizontal = true;

  /**
   * The execution environment of the process associated with this {@link PythonConsoleView}.
   */
  private @Nullable TargetEnvironment myTargetEnvironment;

  /**
   * Context psi file for Python Console Editor. Needs for static code completion.
   */
  private final PsiFile myHistoryPsiFile = new PyExpressionCodeFragmentImpl(getProject(), "dummy.py", "", true);

  public PsiFile getHistoryPsiFile() {
    return myHistoryPsiFile;
  }

  private static @NotNull Helper createHelper(@NotNull Project project, String title) {
    return new Helper(project, Objects.requireNonNull(PsiUtilCore.getVirtualFile(new PyExpressionCodeFragmentImpl(project, title + ".py", "", true))));
  }


  /**
   * @param testMode this console will be used to display test output and should support TC messages
   */
  public PythonConsoleView(final Project project, final String title, final @Nullable Sdk sdk, final boolean testMode) {
    super(createHelper(project, title));
    if (PsiUtilCore.getPsiFile(project, getVirtualFile()) instanceof PyExpressionCodeFragmentImpl codeFragment) {
      codeFragment.setContext(myHistoryPsiFile);
    }

    getConsoleEditor().setFile(getVirtualFile());
    myTestMode = testMode;
    isShowVars = PyConsoleOptions.getInstance(project).isShowVariableByDefault();
    VirtualFile virtualFile = getVirtualFile();
    PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, PySdkUtil.getLanguageLevelForSdk(sdk));
    virtualFile.putUserData(CONSOLE_KEY, true);
    // Mark editor as console one, to prevent autopopup completion if runtime completion is enabled
    if (PyConsoleOptions.getInstance(getProject()).isRuntimeCodeCompletion()) {
      getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());
    }
    getHistoryViewer().putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true);
    getHistoryViewer().putUserData(COUNTER_LINE_NUMBER, new HashMap<>());
    super.setPrompt(null);
    setUpdateFoldingsEnabled(false);
    LanguageLevel languageLevel = LanguageLevel.getDefault();
    if (sdk != null) {
      final PythonSdkFlavor<?> sdkFlavor = PythonSdkFlavor.getFlavor(sdk);
      if (sdkFlavor != null) {
        languageLevel = sdkFlavor.getLanguageLevel(sdk);
      }
      mySdkHomePath = sdk.getHomePath();
    }
    myPyHighlighter = new PyHighlighter(languageLevel);
    addToolwindowPositionListener(project);
  }

  public void setCommandQueueTitle(String title) {
    if (myCommandQueue != null) {
      myCommandQueue.setCaption(PyBundle.message(
        "python.console.command.queue.add.title", title));
    }
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getFile().putCopyableUserData(CONSOLE_COMMUNICATION_KEY, communication);

    if (isShowVars && communication instanceof PydevConsoleCommunication) {
      myIsToolwindowHorizontal = isToolwindowHorizontal(PythonConsoleToolWindow.getInstance(getProject()).getToolWindow());
      showVariables((PydevConsoleCommunication)communication);
    }

    if (communication instanceof PydevConsoleCommunication || communication instanceof PythonDebugConsoleCommunication) {
      myCommandQueuePanel.setCommunication(communication);
    }

    addCommandQueuePanelListener(communication);
  }

  public void addCommandQueuePanelListener(final ConsoleCommunication communication) {
    ApplicationManager.getApplication().getService(CommandQueueForPythonConsoleService.class)
      .addListener(communication, new CommandQueueListener() {
        @Override
        public void removeCommand(ConsoleCommunication.@NotNull ConsoleCodeFragment command) {
          ApplicationManager.getApplication().invokeLater(() -> {
            myCommandQueuePanel.removeCommand(command);
          });
        }

        @Override
        public void addCommand(ConsoleCommunication.@NotNull ConsoleCodeFragment command) {
          myCommandQueuePanel.addCommand(command);
        }

        @Override
        public void removeAll() {
          myCommandQueuePanel.removeAllCommands();
        }

        @Override
        public void disableConsole() {
          myCommandQueuePanel.removeAllCommands();
          isShowQueue = false;
          restoreQueueWindow(false);
        }
      });
  }

  /**
   * Add folding to Console view
   *
   * @param addOnce If true, folding will be added once when an appropriate area is found.
   *                Otherwise, folding can be expanded by newly added text.
   */
  private @Nullable PyConsoleStartFolding createConsoleFolding(boolean addOnce) {
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
  public void executeCode(final @Nullable String code, final @Nullable Editor editor) {
    myInitialized.doWhenDone(() -> executeCodeImpl(code));
  }

  /**
   * Must be called when {@link #myInitialized} is resolved.
   *
   * @param code the code to be executed
   */
  private void executeCodeImpl(@Nullable String code) {
    if (code != null) {
      if (PyConsoleUtil.isCommandQueueEnabled(getProject())) {
        executeInConsole(code);
      }
      else {
        ProgressManager.getInstance().run(new Task.Backgroundable(null, PyBundle.message("console.executing.code.in.console"), true) {
          @Override
          public void run(final @NotNull ProgressIndicator indicator) {
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
    }
    else {
      requestFocus();
    }
  }


  public void executeInConsole(final @NotNull String code) {
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
          CommandProcessor.getInstance().runUndoTransparentAction(
            () -> CodeStyleManager.getInstance(getProject()).adjustLineIndent(psiFile, new TextRange(0, psiFile.getTextLength()))
          );
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

  public void executeStatement(@NotNull String statement, @NotNull Key<?> attributes) {
    print(statement, outputTypeForAttributes(attributes));
    myExecuteActionHandler.processLine(statement);
  }

  public void printText(String text, final ConsoleViewContentType outputType) {
    super.print(text, outputType);
  }

  public void print(@NotNull String text, @NotNull Key<?> attributes) {
    print(text, outputTypeForAttributes(attributes));
  }

  @Override
  public void print(@NotNull String text, final @NotNull ConsoleViewContentType outputType) {
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
          mySourceHighlighter = new PyConsoleSourceHighlighter(this, myPyHighlighter);
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

  public @NotNull ConsoleViewContentType outputTypeForAttributes(@NotNull Key<?> attributes) {
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
    Project project = getProject();
    PyStackFrame stackFrame = new PyStackFrame(project, consoleCommunication, new PyStackFrameInfo("", "", "", null), null);
    stackFrame.restoreChildrenDescriptors(myDescriptorsCache);
    final XStandaloneVariablesView view = new XStandaloneVariablesView(project, new PyDebuggerEditorsProvider(), stackFrame);
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


    JBScrollPane scrollPane = (JBScrollPane)SwingUtilities.getAncestorOfClass(JBScrollPane.class, mySplitView.getMainComponent());
    if (scrollPane != null) {
      scrollPane.setBorder(JBUI.Borders.empty());
    }

    //((JBScrollPane)mySplitView.getTree().getParent().getParent()).setBorder(JBUI.Borders.empty());
    //mySplitView.getMainComponent().setBorder(JBUI.Borders.empty());

    Disposer.register(this, view);
    splitWindow();
    consoleCommunication.notifyViewCreated(view);
  }

  /**
   * Assigns {@link TargetEnvironment} to this view. The target environment could be later used for code execution using
   * {@link #executeCode(Function)}.
   * <p>
   * Works in pair with {@link #attachToProcess(ProcessHandler)}.
   *
   * @param targetEnvironment {@link TargetEnvironment} related to the process
   */
  public void setTargetEnvironment(@NotNull TargetEnvironment targetEnvironment) {
    myTargetEnvironment = targetEnvironment;
  }

  @Override
  public void executeCode(@NotNull Function<TargetEnvironment, String> code) {
    myInitialized.doWhenDone(
      () -> {
        TargetEnvironment targetEnvironment = myTargetEnvironment;
        if (targetEnvironment == null) {
          throw new IllegalStateException("Execution environment must be initialized");
        }
        else {
          String codeValue = code.apply(targetEnvironment);
          executeCodeImpl(codeValue);
        }
      }
    );
  }

  private static boolean isToolwindowHorizontal(ToolWindow toolWindow) {
    return toolWindow.getAnchor() == ToolWindowAnchor.BOTTOM || toolWindow.getAnchor() == ToolWindowAnchor.TOP;
  }

  private void addToolwindowPositionListener(Project project) {
    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        ToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(project).getToolWindow();
        if (myIsToolwindowHorizontal != isToolwindowHorizontal(consoleToolWindow)) {
          myIsToolwindowHorizontal = !myIsToolwindowHorizontal;
          if (isShowVars) {
            restoreWindow();
            ConsoleCommunication communication = getFile().getCopyableUserData(CONSOLE_COMMUNICATION_KEY);
            if (communication instanceof PydevConsoleCommunication) {
              showVariables((PydevConsoleCommunication)communication);
            }
          }
        }
      }
    });
  }

  //the main function for drawing the queue
  public void showQueue() {
    JBPopupListener listener = new JBPopupListener() {

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        isShowQueue = false;
      }
    };
    String commandQueueName = getConsoleDisplayName(getProject());
    myCommandQueue = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(myCommandQueuePanel, null)
      .setMovable(true)
      .setResizable(true)
      .setShowShadow(true)
      .setCancelOnClickOutside(false)
      .setTitle(PyBundle.message(
        "python.console.command.queue.add.title",
        commandQueueName != null ? commandQueueName : "Python Console"))
      .setCancelButton(new MinimizeButton(IdeBundle.message("tooltip.hide")))
      .addListener(listener)
      .setBorderColor(JBColor.background())
      .setCancelOnOtherWindowOpen(true)
      .createPopup();

    var title = (TitlePanel)((AbstractPopup)myCommandQueue).getTitle();
    title.getLabel().setForeground(JBColor.foreground());
    title.setActive(true);
    ((AbstractPopup)myCommandQueue).addResizeListener(this::commandQueueWasResized, this);

    if (commandQueueDimension != null) {
      myCommandQueue.setSize(commandQueueDimension);
    }
    var editor = getConsoleEditor();
    if (UIUtil.isShowing(editor.getContentComponent())) {
      myCommandQueue.showInBestPositionFor(getConsoleEditor());
    }

    Disposer.register(this, myCommandQueue);
  }

  private void commandQueueWasResized() {
    commandQueueDimension = myCommandQueue.getSize();
  }

  @Override
  protected @NotNull JComponent createCenterComponent() {
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
    JBSplitter p = new JBSplitter(!myIsToolwindowHorizontal, 2f / 3);
    p.setFirstComponent((JComponent)console);
    p.setSecondComponent(mySplitView.getPanel());
    p.setShowDividerControls(true);
    p.setHonorComponentsMinimumSize(true);

    add(p, BorderLayout.CENTER);
    validate();
    repaint();
  }

  // helper function for drawing the CommandQueue
  public void restoreQueueWindow(boolean removeCommand) {
    if (myCommandQueue != null) {
      ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(myCommandQueue));
    }
    if (removeCommand) {
      ApplicationManager.getApplication().getService(CommandQueueForPythonConsoleService.class)
        .removeCommand(myRunner.getPydevConsoleCommunication(), true);
    }
  }

  public void restoreWindow() {
    Component component = getComponent(0);
    if (mySplitView != null && component instanceof JBSplitter pane) {
      removeAll();
      Disposer.dispose(mySplitView);
      mySplitView = null;
      add(pane.getFirstComponent(), BorderLayout.CENTER);
      validate();
      repaint();
    }
  }

  public @Nullable String getSdkHomePath() {
    return mySdkHomePath;
  }

  public boolean isInitialized() {
    return myInitialized.isDone();
  }

  public void initialized() {
    myInitialized.setDone();
  }

  public boolean isShowVars() {
    return isShowVars;
  }

  public void setShowVars(boolean showVars) {
    isShowVars = showVars;
  }

  public boolean isShowQueue() {
    return isShowQueue;
  }

  public void setShowQueue(boolean showQueue) {
    isShowQueue = showQueue;
  }

  public void whenInitialized(Runnable runnable) {
    myInitialized.doWhenDone(runnable);
  }

  public void setRunner(PydevConsoleRunner runner) {
    myRunner = runner;
  }

  public PydevConsoleRunner getRunner() {
    return myRunner;
  }

  @TestOnly
  public @Nullable XDebuggerTreeNode getDebuggerTreeRootNode() {
    return mySplitView.getTree().getRoot();
  }

  public void setOnClearCallback(@Nullable OnClearCallback onClearCallback) {
    myOnClearCallback = onClearCallback;
  }

  @Override
  public void clear() {
    super.clear();
    OnClearCallback onClearCallback = myOnClearCallback;
    if (onClearCallback != null) {
      onClearCallback.onClear();
    }
  }

  @Override
  public void dispose() {
    super.dispose();

    if (!getProject().isDisposed()) {
      ConsoleCommunication communication = getFile().getCopyableUserData(CONSOLE_COMMUNICATION_KEY);
      if (communication != null) {
        ApplicationManager.getApplication().getService(CommandQueueForPythonConsoleService.class).removeListener(communication);
      }
    }

    var editor = myCommandQueuePanel.getQueueEditor();
    commandQueueDimension = null;
    if (!editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  public void setToolbar(ActionToolbar toolbar) {
    myToolbar = toolbar;
  }

  public ActionToolbar getToolbar() {
    return myToolbar;
  }

  // needs for correctly display the queue's name after renaming
  private static @Nullable String getConsoleDisplayName(@NotNull Project project) {
    ToolWindow window = PythonConsoleToolWindow.getInstance(project).getToolWindow();
    final Content content = window.getContentManager().getSelectedContent();
    if (content == null) return null;
    return content.getDisplayName();
  }

  public void storeExecutionCounterLineNumber(int counter, int lineNumber) {
    Map<Integer, Integer> counterMap = getHistoryViewer().getUserData(COUNTER_LINE_NUMBER);
    if (counterMap != null) {
      counterMap.put(counter, lineNumber);
    }
  }

  public @Nullable Integer getExecutionCounterLineNumber(int counter) {
    Map<Integer, Integer> counterMap = getHistoryViewer().getUserData(COUNTER_LINE_NUMBER);
    if (counterMap != null) {
      return counterMap.getOrDefault(counter, null);
    }
    return null;
  }

  public interface OnClearCallback {
    void onClear();
  }
}