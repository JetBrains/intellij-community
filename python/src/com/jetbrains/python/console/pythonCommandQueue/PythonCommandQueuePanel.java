// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.pythonCommandQueue;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.console.actions.ShowCommandQueueAction;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main panel for PopupWindow (CommandQueue)
 */
public final class PythonCommandQueuePanel extends JPanel {
  private final JPanel myPanel = new JPanel();
  private final JBSplitter mySplitter;
  private final PythonConsoleView myConsole;
  private final GotItTooltip tooltip;
  private final EditorEx myQueueEditor;
  private final Map<ConsoleCommunication.ConsoleCodeFragment, QueueElementPanel> myQueueElementPanelMap = new ConcurrentHashMap<>();
  private final CommandQueueForPythonConsoleService myService = ApplicationManager.getApplication()
    .getService(CommandQueueForPythonConsoleService.class);


  private QueueElementPanel selectedCommand;
  private ConsoleCommunication communication;

  private static final int PREFERED_WIDTH = 500;
  private static final int PREFERED_HEIGHT = 150;
  private static final int QUEUE_MINIMUM_WIDTH = 200;
  private static final int QUEUE_MINIMUM_HEIGHT = -1;
  private static final int BORDER_OFFSETS = 7;


  public PythonCommandQueuePanel(@NotNull PythonConsoleView console) {
    setLayout(new GridLayout());
    setBorder(JBUI.Borders.empty());
    setPreferredSize(new Dimension(PREFERED_WIDTH, PREFERED_HEIGHT));
    myConsole = console;
    tooltip = new GotItTooltip(PyBundle.message("python.console.command.queue.got.it.tooltip.id"),
                               PyBundle.message("python.console.command.queue.got.it.tooltip.text"), myConsole)
      .withHeader(PyBundle.message("python.console.command.queue.got.it.tooltip.title"));
    mySplitter = new JBSplitter(true);
    mySplitter.setSplitterProportionKey(PyBundle.message(
      "python.console.command.queue.add.title", getClass().getName()));
    mySplitter.setOrientation(false);
    mySplitter.setBackground(JBColor.background());
    JBScrollPane scrollPane = new JBScrollPane(myPanel);
    //scrollPane.setMinimumSize(new Dimension(QUEUE_MINIMUM_WIDTH, QUEUE_MINIMUM_HEIGHT));
    mySplitter.setFirstComponent(scrollPane);
    myQueueEditor = createEmptyEditor();
    mySplitter.setSecondComponent(myQueueEditor.getComponent());
    add(mySplitter);

    myPanel.setLayout(new VerticalLayout(0));
    myPanel.setBorder(JBUI.Borders.empty(BORDER_OFFSETS));
    myPanel.setBackground(JBColor.background());

    repaintAll();
  }

  public void addCommand(@NotNull ConsoleCommunication.ConsoleCodeFragment command) {
    QueueElementPanel elementPanel = new QueueElementPanel(command,
                                                           myService.isOneElement(communication)
                                                           ? new AnimatedIcon.Default()
                                                           : AllIcons.Nodes.EmptyNode);
    myQueueElementPanelMap.put(command, elementPanel);
    myPanel.add(elementPanel.getQueuePanel());

    if (myService.isOneElement(communication)) {
      elementPanel.unsetCancelButton();
      commandSelected(elementPanel);
    }
    if (myService.isTwoElement(communication)) {
      if (communication instanceof PythonDebugConsoleCommunication) {
        repaintAll();
        return;
      }
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        tooltip.show(getActionButton(), GotItTooltip.BOTTOM_MIDDLE);
      }
    }
    repaintAll();
  }

  private void helperForRemove(@NotNull ConsoleCommunication.ConsoleCodeFragment command) {
    var removedPanel = myQueueElementPanelMap.remove(command);

    if (removedPanel != null) {
      myPanel.remove(removedPanel.getQueuePanel());
    }

    if (!myQueueElementPanelMap.isEmpty()) {
      var firstCommand = myService.getFirstCommand(communication);
      if (firstCommand != null) {
        QueueElementPanel elementPanel = myQueueElementPanelMap.get(firstCommand);
        if (elementPanel != null) {
          elementPanel.setIcon(new AnimatedIcon.Default());
          elementPanel.unsetCancelButton();

          if (selectedCommand == removedPanel) {
            commandSelected(elementPanel);
          }
        }
      }
    }
    else {
      setTextToEditor("");
      selectedCommand = null;
    }
  }

  public void removeCommand(@NotNull ConsoleCommunication.ConsoleCodeFragment command) {
    helperForRemove(command);
    repaintAll();
  }

  public void removeCommandByButton(@NotNull ConsoleCommunication.ConsoleCodeFragment command) {
    myService.removeCommand(communication, command);
    helperForRemove(command);
    repaintAll();
  }

  public void removeAllCommands() {
    myQueueElementPanelMap.clear();
    myPanel.removeAll();
    if (selectedCommand != null) {
      setTextToEditor("");
      selectedCommand = null;
    }
    repaintAll();
  }

  private void repaintAll() {
    revalidate();
    repaint();
  }

  public void setCommunication(@NotNull ConsoleCommunication communication) {
    this.communication = communication;
  }

  void commandSelected(@NotNull QueueElementPanel elementPanel) {
    if (selectedCommand != null) {
      selectedCommand.getQueuePanel().setBackground(JBColor.lazy(UIUtil::getListBackground));
      selectedCommand.setTextColor();
      selectedCommand.setButtonColor();
    }
    selectedCommand = elementPanel;
    selectedCommand.selectPanel();
    setTextToEditor(Objects.requireNonNull(elementPanel.getText()));
    mySplitter.setSecondComponent(myQueueEditor.getComponent());
  }

  @NotNull
  public EditorEx getQueueEditor() {
    return myQueueEditor;
  }

  private ActionButton getActionButton() {
    return UIUtil.uiTraverser(myConsole.getToolbar().getComponent())
      .filter(ActionButton.class)
      .filter((button) -> {
        return ShowCommandQueueAction.isCommandQueueIcon(button.getIcon());
      })
      .first();
  }

  @NotNull
  private EditorEx createEmptyEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = ((EditorFactoryImpl)editorFactory).createDocument(false);
    UndoUtil.disableUndoFor(document);
    return (EditorEx)createEditor();
  }

  @NotNull
  private Editor createEditor() {
    PsiFile consoleFile = myConsole.getFile();
    Language language = consoleFile.getLanguage();
    Project project = consoleFile.getProject();

    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
      "a." + consoleFile.getFileType().getDefaultExtension(),
      language,
      StringUtil.convertLineSeparators(StringUtil.trimEnd("", "\n")), false, true);
    VirtualFile virtualFile = psiFile.getViewProvider().getVirtualFile();
    if (virtualFile instanceof LightVirtualFile) ((LightVirtualFile)virtualFile).setWritable(true);
    Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile));
    EditorFactory editorFactory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)editorFactory.createViewer(document, project);
    editor.getSettings().setFoldingOutlineShown(false);
    editor.getSettings().setLineMarkerAreaShown(false);
    editor.getSettings().setIndentGuidesShown(false);

    SyntaxHighlighter highlighter =
      SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, psiFile.getViewProvider().getVirtualFile());
    editor.setHighlighter(new LexerEditorHighlighter(highlighter, editor.getColorsScheme()));
    return editor;
  }

  private void setTextToEditor(@NotNull String text) {
    ApplicationManager.getApplication().executeOnPooledThread(() ->
                                                              {
                                                                WriteCommandAction
                                                                  .writeCommandAction(myConsole.getProject())
                                                                  .run(() ->
                                                                         myQueueEditor.getDocument()
                                                                           .setText(text));
                                                              });
  }
}