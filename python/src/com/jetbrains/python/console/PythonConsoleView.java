/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
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
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.JBSplitter;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class PythonConsoleView extends LanguageConsoleImpl implements ObservableConsoleView, PyCodeExecutor {

  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);
  private final ConsolePromptDecorator myPromptView;

  private PythonConsoleExecuteActionHandler myExecuteActionHandler;
  private PyConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput;
  private final PyHighlighter myPyHighlighter;
  private final EditorColorsScheme myScheme;
  private boolean myHyperlink;

  private XStandaloneVariablesView mySplitView;
  private ActionCallback myInitialized = new ActionCallback();

  public PythonConsoleView(final Project project, final String title, final Sdk sdk) {
    super(project, title, PythonLanguage.getInstance());

    getVirtualFile().putUserData(LanguageLevel.KEY, PythonSdkType.getLanguageLevelForSdk(sdk));
    // Mark editor as console one, to prevent autopopup completion
    getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());
    getHistoryViewer().putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true);
    super.setPrompt(null);
    setUpdateFoldingsEnabled(false);
    //noinspection ConstantConditions
    myPyHighlighter = new PyHighlighter(
      sdk != null && sdk.getVersionString() != null ? LanguageLevel.fromPythonVersion(sdk.getVersionString()) : LanguageLevel.getDefault());
    myScheme = getConsoleEditor().getColorsScheme();
    PythonConsoleData data = PyConsoleUtil.getOrCreateIPythonData(getVirtualFile());
    myPromptView = new ConsolePromptDecorator(this.getConsoleEditor(), data);
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
  }

  private PyConsoleStartFolding createConsoleFolding() {
    PyConsoleStartFolding startFolding = new PyConsoleStartFolding(this);
    myExecuteActionHandler.getConsoleCommunication().addCommunicationListener(startFolding);
    getEditor().getDocument().addDocumentListener(startFolding);
    ((FoldingModelEx)getEditor().getFoldingModel()).addListener(startFolding, this);
    return startFolding;
  }

  public void addConsoleFolding(boolean isDebugConsole) {
    try {
      if (isDebugConsole && myExecuteActionHandler != null && getEditor() != null) {
        PyConsoleStartFolding folding = createConsoleFolding();
        // in debug console we should add folding from the place where the folding was turned on
        folding.setStartLineOffset(getEditor().getDocument().getTextLength());
        folding.setNumberOfCommandToStop(2);
      }
      else {
        myInitialized.doWhenDone(this::createConsoleFolding);
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
    IdeFocusManager.findInstance().requestFocus(getConsoleEditor().getContentComponent(), true);
  }

  @Override
  public void executeCode(final @Nullable String code, @Nullable final Editor editor) {
    myInitialized.doWhenDone(
      () -> {
        if (code != null) {
          ProgressManager.getInstance().run(new Task.Backgroundable(null, "Executing Code in Console...", false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
              long time = System.currentTimeMillis();
              while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
                if (indicator.isCanceled()) {
                  break;
                }
                if (System.currentTimeMillis() - time > 1000) {
                  if (editor != null) {
                    UIUtil.invokeLaterIfNeeded(
                      () -> HintManager.getInstance()
                        .showErrorHint(editor, myExecuteActionHandler.getCantExecuteMessage()));
                  }
                  return;
                }
                TimeoutUtil.sleep(300);
              }
              if (!indicator.isCanceled()) {
                executeInConsole(code);
              }
            }
          });
        } else {
          requestFocus();
        }
      }
    );
  }


  public void executeInConsole(@NotNull final String code) {
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
                                                                                        new TextRange(0, psiFile.getTextLength())));
        }
      });
      int oldOffset = getConsoleEditor().getCaretModel().getOffset();
      getConsoleEditor().getCaretModel().moveToOffset(document.getTextLength());
      myExecuteActionHandler.runExecuteAction(this);

      if (!StringUtil.isEmpty(oldText)) {
        ApplicationManager.getApplication().runWriteAction(() -> setInputText(oldText));
        getConsoleEditor().getCaretModel().moveToOffset(oldOffset);
      }
    });
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

  protected final void doAddPromptToHistory(boolean isMainPrompt) {
    flushDeferredText();
    EditorEx viewer = getHistoryViewer();
    DocumentEx document = viewer.getDocument();
    RangeHighlighter highlighter = getHistoryViewer().getMarkupModel()
      .addRangeHighlighter(document.getTextLength(), document.getTextLength(), 0, null, HighlighterTargetArea.EXACT_RANGE);
    final String prompt;
    if (isMainPrompt) {
      prompt = myPromptView.getMainPrompt();
      print(prompt + " ", myPromptView.getPromptAttributes());
    }
    else {
      prompt = myPromptView.getIndentPrompt();
      //todo should really be myPromptView.getPromptAttributes() output type
      //but in that case flushing doesn't get handled correctly. Take a look at it later
      print(prompt + " ", ConsoleViewContentType.USER_INPUT);
    }

    highlighter.putUserData(PyConsoleCopyHandler.PROMPT_LENGTH_MARKER, prompt.length() + 1);
  }

  @NotNull
  protected String addTextRangeToHistory(@NotNull TextRange textRange, @NotNull EditorEx inputEditor, boolean preserveMarkup) {
    String text;
    EditorHighlighter highlighter;
    if (inputEditor instanceof EditorWindow) {
      PsiFile file = ((EditorWindow)inputEditor).getInjectedFile();
      highlighter =
        HighlighterFactory.createHighlighter(file.getVirtualFile(), EditorColorsManager.getInstance().getGlobalScheme(), getProject());
      String fullText = InjectedLanguageUtil.getUnescapedText(file, null, null);
      highlighter.setText(fullText);
      text = textRange.substring(fullText);
    }
    else {
      text = inputEditor.getDocument().getText(textRange);
      highlighter = inputEditor.getHighlighter();
    }
    SyntaxHighlighter syntax =
      highlighter instanceof LexerEditorHighlighter ? ((LexerEditorHighlighter)highlighter).getSyntaxHighlighter() : null;
    doAddPromptToHistory(true);

    if (syntax != null) {
      ConsoleViewUtil.printWithHighlighting(this, text, syntax, () -> doAddPromptToHistory(false));
    }
    else {
      print(text, ConsoleViewContentType.USER_INPUT);
    }
    print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    return text;
  }


  @NotNull
  @Override
  protected JComponent createCenterComponent() {
    //workaround for extra lines appearing in the console
    JComponent centerComponent = super.createCenterComponent();
    getHistoryViewer().getSettings().setAdditionalLinesCount(0);
    getHistoryViewer().getSettings().setUseSoftWraps(false);
    getConsoleEditor().getGutter().registerTextAnnotation(this.myPromptView);
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
    JBSplitter pane = (JBSplitter)getComponent(0);
    removeAll();
    if (mySplitView != null) {
      Disposer.dispose(mySplitView);
      mySplitView = null;
    }
    add(pane.getFirstComponent(), BorderLayout.CENTER);
    validate();
    repaint();
  }

  @Nullable
  @Override
  public String getPrompt() {
    if (myPromptView == null) // we're in the constructor!
    {
      return super.getPrompt();
    }
    return myPromptView.getMainPrompt();
  }


  @Override
  public void setPrompt(@Nullable String prompt) {
    if (this.myPromptView == null) // we're in the constructor!
    {
      super.setPrompt(prompt);
      return;
    }
    if (prompt != null) {
      this.myPromptView.setMainPrompt(prompt);
    }
  }


  @Override
  public void setPromptAttributes(@NotNull ConsoleViewContentType textAttributes) {
    myPromptView.setPromptAttributes(textAttributes);
  }

  public void initialized() {
    myInitialized.setDone();
  }
}
