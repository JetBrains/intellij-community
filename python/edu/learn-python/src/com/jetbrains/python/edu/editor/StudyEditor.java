package com.jetbrains.python.edu.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
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
import com.jetbrains.python.edu.StudyDocumentListener;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.actions.*;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import icons.StudyIcons;
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
import java.util.Map;

/**
 * Implementation of StudyEditor which has panel with special buttons and task text
 * also @see {@link com.jetbrains.python.edu.editor.StudyFileEditorProvider}
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
  private static final Map<Document, StudyDocumentListener> myDocumentListeners = new HashMap<Document, StudyDocumentListener>();
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

  private static JButton addButton(@NotNull final JComponent parentComponent, String toolTipText, Icon icon) {
    JButton newButton = new JButton();
    newButton.setToolTipText(toolTipText);
    newButton.setIcon(icon);
    newButton.setSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    parentComponent.add(newButton);
    return newButton;
  }

  public static void addDocumentListener(@NotNull final Document document, @NotNull final StudyDocumentListener listener) {
    myDocumentListeners.put(document, listener);
  }

  @Nullable
  public static StudyDocumentListener getListener(@NotNull final Document document) {
    return myDocumentListeners.get(document);
  }

  public StudyEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myProject = project;
    myDefaultEditor = TextEditorProvider.getInstance().createEditor(myProject, file);
    myComponent = myDefaultEditor.getComponent();
    JPanel studyPanel = new JPanel();
    studyPanel.setLayout(new BoxLayout(studyPanel, BoxLayout.Y_AXIS));
    myTaskFile = StudyTaskManager.getInstance(myProject).getTaskFile(file);
    if (myTaskFile != null) {
      Task currentTask = myTaskFile.getTask();
      String taskText = currentTask.getResourceText(project, currentTask.getText(), false);
      initializeTaskText(studyPanel, taskText);
      JPanel studyButtonPanel = new JPanel(new GridLayout(1, 2));
      JPanel taskActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      studyButtonPanel.add(taskActionsPanel);
      studyButtonPanel.add(new JPanel());
      initializeButtons(taskActionsPanel, myTaskFile);
      studyPanel.add(studyButtonPanel);
      myComponent.add(studyPanel, BorderLayout.NORTH);
    }
  }

  class CopyListener extends MouseAdapter {
    final JTextPane myTextPane;

    public CopyListener(JTextPane textPane) {
      myTextPane = textPane;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ToolWindow projectView = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
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
                StringSelection selection = new StringSelection(text);
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

  private void initializeTaskText(JPanel studyPanel, @Nullable String taskText) {
    JTextPane taskTextPane = new JTextPane();
    taskTextPane.addMouseListener(new CopyListener(taskTextPane));
    taskTextPane.setContentType("text/html");
    taskTextPane.setEditable(false);
    taskTextPane.setText(taskText);
    taskTextPane.addHyperlinkListener(new BrowserHyperlinkListener());
    EditorColorsScheme editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    int fontSize = editorColorsScheme.getEditorFontSize();
    String fontName = editorColorsScheme.getEditorFontName();
    setJTextPaneFont(taskTextPane, new Font(fontName, Font.PLAIN, fontSize), JBColor.BLACK);
    taskTextPane.setBackground(UIUtil.getPanelBackground());
    taskTextPane.setBorder(new EmptyBorder(15, 20, 0, 100));
    HideableTitledPanel taskTextPanel = new HideableTitledPanel(TASK_TEXT_HEADER, taskTextPane, true);
    taskTextPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    studyPanel.add(taskTextPanel);
  }

  private static void setJTextPaneFont(JTextPane jtp, Font font, Color c) {
    MutableAttributeSet attrs = jtp.getInputAttributes();
    StyleConstants.setFontFamily(attrs, font.getFamily());
    StyleConstants.setFontSize(attrs, font.getSize());
    StyleConstants.setItalic(attrs, (font.getStyle() & Font.ITALIC) != 0);
    StyleConstants.setBold(attrs, (font.getStyle() & Font.BOLD) != 0);
    StyleConstants.setForeground(attrs, c);
    StyledDocument doc = jtp.getStyledDocument();
    doc.setCharacterAttributes(0, doc.getLength() + 1, attrs, false);
  }

  private void initializeButtons(@NotNull final JPanel taskActionsPanel, @NotNull final TaskFile taskFile) {
    myCheckButton = addButton(taskActionsPanel, "Check task", StudyIcons.Resolve);
    myPrevTaskButton = addButton(taskActionsPanel, "Prev Task", StudyIcons.Prev);
    myNextTaskButton = addButton(taskActionsPanel, "Next Task", AllIcons.Actions.Forward);
    myRefreshButton = addButton(taskActionsPanel, "Start task again", AllIcons.Actions.Refresh);
    if (!taskFile.getTask().getUserTests().isEmpty()) {
      JButton runButton = addButton(taskActionsPanel, "Run", AllIcons.General.Run);
      runButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          StudyRunAction studyRunAction = (StudyRunAction)ActionManager.getInstance().getAction("StudyRunAction");
          studyRunAction.run(myProject);
        }
      });
      JButton watchInputButton = addButton(taskActionsPanel, "Watch test input", StudyIcons.WatchInput);
      watchInputButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          StudyEditInputAction studyEditInputAction =
            (StudyEditInputAction)ActionManager.getInstance().getAction("WatchInputAction");
          studyEditInputAction.showInput(myProject);
        }
      });
    }
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
        StudyRefreshTaskFileAction studyRefreshTaskAction =
          (StudyRefreshTaskFileAction)ActionManager.getInstance().getAction("RefreshTaskAction");
        studyRefreshTaskAction.refresh(myProject);
      }
    });
  }

  public JButton getNextTaskButton() {
    return myNextTaskButton;
  }

  public JButton getRefreshButton() {
    return myRefreshButton;
  }

  FileEditor getDefaultEditor() {
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
      FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(project).getSplitters().getCurrentWindow().
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
    StudyEditor studyEditor = getSelectedStudyEditor(project);
    if (studyEditor != null) {
      FileEditor defaultEditor = studyEditor.getDefaultEditor();
      if (defaultEditor instanceof PsiAwareTextEditorImpl) {
        return ((PsiAwareTextEditorImpl)defaultEditor).getEditor();
      }
    }
    return null;
  }

  public static void removeListener(Document document) {
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
}
