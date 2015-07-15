package com.jetbrains.edu.learning.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.HideableTitledPanel;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.EmptyClipboardOwner;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of StudyEditor which has panel with special buttons and task text
 * also @see {@link StudyFileEditorProvider}
 */
public class StudyEditor implements TextEditor {
  private static final String TASK_TEXT_HEADER = "Task Text";
  private final FileEditor myDefaultEditor;
  private final JComponent myComponent;
  private final TaskFile myTaskFile;
  private JButton myCheckButton;
  private JButton myNextTaskButton;
  private JButton myPrevTaskButton;
  private JButton myRefreshButton;
  private static final Map<Document, EduDocumentListener> myDocumentListeners = new HashMap<Document, EduDocumentListener>();
  private final Project myProject;

  public JButton getCheckButton() {
    return myCheckButton;
  }

  public JButton getPrevTaskButton() {
    return myPrevTaskButton;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  private static JButton addButton(@NotNull final JComponent parentComponent, @NotNull final String actionID,
                                   @NotNull final Icon icon, @Nullable String defaultShortcutString) {
    final AnAction action = ActionManager.getInstance().getAction(actionID);
    String toolTipText = KeymapUtil.createTooltipText(action.getTemplatePresentation().getText(), action);
    if (!toolTipText.contains("(") && defaultShortcutString != null) {
      KeyboardShortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(defaultShortcutString), null);
      toolTipText += " (" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    final JButton newButton = new JButton();
    newButton.setToolTipText(toolTipText);
    newButton.setIcon(icon);
    newButton.setSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    parentComponent.add(newButton);
    return newButton;
  }

  public static void addDocumentListener(@NotNull final Document document, @NotNull final EduDocumentListener listener) {
    document.addDocumentListener(listener);
    myDocumentListeners.put(document, listener);
  }

  public StudyEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myProject = project;
    myDefaultEditor = TextEditorProvider.getInstance().createEditor(myProject, file);
    myComponent = myDefaultEditor.getComponent();
    final JPanel studyPanel = new JPanel();
    studyPanel.setLayout(new BoxLayout(studyPanel, BoxLayout.Y_AXIS));
    myTaskFile = StudyUtils.getTaskFile(project, file);
    if (myTaskFile != null) {
      final Task currentTask = myTaskFile.getTask();
      final String taskText = currentTask.getText();
      //initializeTaskText(studyPanel, taskText);
      final JPanel studyButtonPanel = new JPanel(new GridLayout(1, 2));
      final JPanel taskActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      studyButtonPanel.add(taskActionsPanel);
      studyButtonPanel.add(new JPanel());
      initializeButtons(taskActionsPanel, myTaskFile);
      studyPanel.add(studyButtonPanel);
      myComponent.add(studyPanel, BorderLayout.NORTH);
    }
  }

  class CopyListener extends MouseAdapter {
    final JTextPane myTextPane;

    public CopyListener(@NotNull final JTextPane textPane) {
      myTextPane = textPane;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          final ToolWindow projectView = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
          if (projectView == null) {
            return;
          }
          final Component focusComponent = projectView.getComponent();
          IdeFocusManager.getInstance(myProject).requestFocus(focusComponent, true);
          final String text = myTextPane.getSelectedText();
          if (text == null) {
            return;
          }
          KeyAdapter keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ev) {
              if (ev.getKeyCode() == KeyEvent.VK_C
                  && ev.getModifiers() == InputEvent.CTRL_MASK) {
                final StringSelection selection = new StringSelection(text);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, EmptyClipboardOwner.INSTANCE);
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    IdeFocusManager.getInstance(myProject).requestFocus(myDefaultEditor.getComponent(), true);
                  }
                });
              }
            }
          };
          focusComponent.addKeyListener(keyAdapter);
        }
      });
    }
  }

  private void initializeTaskText(@NotNull final JPanel studyPanel, @Nullable final String taskText) {
    final JTextPane taskTextPane = new JTextPane();
    taskTextPane.addMouseListener(new CopyListener(taskTextPane));
    taskTextPane.setContentType("text/html");
    taskTextPane.setEditable(false);
    taskTextPane.setText(taskText);
    taskTextPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    final EditorColorsScheme editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    int fontSize = editorColorsScheme.getEditorFontSize();
    final String fontName = editorColorsScheme.getEditorFontName();
    setJTextPaneFont(taskTextPane, new Font(fontName, Font.PLAIN, fontSize));
    taskTextPane.setBackground(UIUtil.getPanelBackground());
    taskTextPane.setBorder(new EmptyBorder(15, 20, 0, 100));
    final HideableTitledPanel taskTextPanel = new HideableTitledPanel(TASK_TEXT_HEADER, taskTextPane, true);
    taskTextPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    studyPanel.add(taskTextPanel);
  }

  private static void setJTextPaneFont(@NotNull final JTextPane textPane, @NotNull final Font font) {
    final MutableAttributeSet attrs = textPane.getInputAttributes();
    StyleConstants.setFontFamily(attrs, font.getFamily());
    StyleConstants.setFontSize(attrs, font.getSize());
    StyleConstants.setItalic(attrs, (font.getStyle() & Font.ITALIC) != 0);
    StyleConstants.setBold(attrs, (font.getStyle() & Font.BOLD) != 0);
    StyleConstants.setForeground(attrs, JBColor.BLACK);
    StyledDocument doc = textPane.getStyledDocument();
    doc.setCharacterAttributes(0, doc.getLength() + 1, attrs, false);
  }

  private void initializeButtons(@NotNull final JPanel taskActionsPanel, @NotNull final TaskFile taskFile) {
    myCheckButton = addButton(taskActionsPanel, StudyCheckAction.ACTION_ID, InteractiveLearningIcons.Resolve, StudyCheckAction.SHORTCUT);
    myPrevTaskButton = addButton(taskActionsPanel, StudyPreviousStudyTaskAction.ACTION_ID, InteractiveLearningIcons.Prev, StudyPreviousStudyTaskAction.SHORTCUT);
    myNextTaskButton = addButton(taskActionsPanel, StudyNextStudyTaskAction.ACTION_ID, AllIcons.Actions.Forward, StudyNextStudyTaskAction.SHORTCUT);
    myRefreshButton = addButton(taskActionsPanel, StudyRefreshTaskFileAction.ACTION_ID, AllIcons.Actions.Refresh, StudyRefreshTaskFileAction.SHORTCUT);
    final JButton myShowHintButton = addButton(taskActionsPanel, StudyShowHintAction.ACTION_ID, InteractiveLearningIcons.ShowHint, StudyShowHintAction.SHORTCUT);
    final List<UserTest> userTests = StudyTaskManager.getInstance(myProject).getUserTests(taskFile.getTask());
    if (!userTests.isEmpty()) {
      final JButton runButton = addButton(taskActionsPanel, StudyRunAction.ACTION_ID, AllIcons.General.Run, null);
      runButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          StudyRunAction studyRunAction = (StudyRunAction)ActionManager.getInstance().getAction("StudyRunAction");
          studyRunAction.run(myProject);
        }
      });
      final JButton watchInputButton = addButton(taskActionsPanel, "WatchInputAction", InteractiveLearningIcons.WatchInput, null);
      watchInputButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          StudyEditInputAction studyEditInputAction =
            (StudyEditInputAction)ActionManager.getInstance().getAction("WatchInputAction");
          studyEditInputAction.showInput(myProject);
      }
        });
    }
    initializeButtonActions(myShowHintButton);
  }

  protected void initializeButtonActions(JButton myShowHintButton) {
    myCheckButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudyCheckAction studyCheckAction = (StudyCheckAction)ActionManager.getInstance().getAction("CheckAction");
        studyCheckAction.check(myProject);
      }
    });

    myNextTaskButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudyNextStudyTaskAction studyNextTaskAction =
          (StudyNextStudyTaskAction)ActionManager.getInstance().getAction("NextTaskAction");
        studyNextTaskAction.navigateTask(myProject);
      }
    });
    myPrevTaskButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudyPreviousStudyTaskAction
          prevTaskAction = (StudyPreviousStudyTaskAction)ActionManager.getInstance().getAction("PreviousTaskAction");
        prevTaskAction.navigateTask(myProject);
      }
    });
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudyRefreshTaskFileAction.refresh(myProject);
      }
    });

    myShowHintButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudyShowHintAction.showHint(myProject);
      }
    });
  }

  public JButton getNextTaskButton() {
    return myNextTaskButton;
  }

  public JButton getRefreshButton() {
    return myRefreshButton;
  }

  private FileEditor getDefaultEditor() {
    return myDefaultEditor;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDefaultEditor.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "Study Editor";
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myDefaultEditor.getState(level);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    myDefaultEditor.setState(state);
  }

  @Override
  public boolean isModified() {
    return myDefaultEditor.isModified();
  }

  @Override
  public boolean isValid() {
    return myDefaultEditor.isValid();
  }

  @Override
  public void selectNotify() {
    myDefaultEditor.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myDefaultEditor.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDefaultEditor.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDefaultEditor.removePropertyChangeListener(listener);
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myDefaultEditor.getBackgroundHighlighter();
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return myDefaultEditor.getCurrentLocation();
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return myDefaultEditor.getStructureViewBuilder();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDefaultEditor);
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myDefaultEditor.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDefaultEditor.putUserData(key, value);
  }


  @Nullable
  public static StudyEditor getSelectedStudyEditor(@NotNull final Project project) {
    try {
      final FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(project).getSplitters().getCurrentWindow().
        getSelectedEditor().getSelectedEditorWithProvider().getFirst();
      if (fileEditor instanceof StudyEditor) {
        return (StudyEditor)fileEditor;
      }
    }
    catch (Exception e) {
      return null;
    }
    return null;
  }

  @Nullable
  public static Editor getSelectedEditor(@NotNull final Project project) {
    final StudyEditor studyEditor = getSelectedStudyEditor(project);
    if (studyEditor != null) {
      FileEditor defaultEditor = studyEditor.getDefaultEditor();
      if (defaultEditor instanceof PsiAwareTextEditorImpl) {
        return ((PsiAwareTextEditorImpl)defaultEditor).getEditor();
      }
    }
    return null;
  }

  public static void removeListener(Document document) {
    final EduDocumentListener listener = myDocumentListeners.get(document);
    if (listener != null) {
      document.removeDocumentListener(listener);
    }
    myDocumentListeners.remove(document);
  }

  @NotNull
  @Override
  public Editor getEditor() {
    if (myDefaultEditor instanceof TextEditor) {
      return ((TextEditor)myDefaultEditor).getEditor();
    }
    return EditorFactory.getInstance().createViewer(new DocumentImpl(""), myProject);
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    if (myDefaultEditor instanceof TextEditor) {
      ((TextEditor)myDefaultEditor).canNavigateTo(navigatable);
    }
    return false;
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    if (myDefaultEditor instanceof TextEditor) {
      ((TextEditor)myDefaultEditor).navigateTo(navigatable);
    }
  }

  public static void deleteGuardedBlocks(@NotNull final Document document) {
    if (document instanceof DocumentImpl) {
      final DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      for (final RangeMarker block : blocks) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                document.removeGuardedBlock(block);
              }
            });
          }
        });
      }
    }
  }
}
