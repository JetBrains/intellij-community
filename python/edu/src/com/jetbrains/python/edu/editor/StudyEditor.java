package com.jetbrains.python.edu.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HideableTitledPanel;
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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of StudyEditor which has panel with special buttons and task text
 * also @see {@link com.jetbrains.python.edu.editor.StudyFileEditorProvider}
 */
public class StudyEditor implements FileEditor {
  private static final String TASK_TEXT_HEADER = "Task Text";
  private final FileEditor myDefaultEditor;
  private final JComponent myComponent;
  private JButton myCheckButton;
  private JButton myNextTaskButton;
  private JButton myPrevTaskButton;
  private JButton myRefreshButton;
  private JButton myWatchInputButton;
  private static final Map<Document, StudyDocumentListener> myDocumentListeners = new HashMap<Document, StudyDocumentListener>();

  public JButton getWatchInputButton() {
    return myWatchInputButton;
  }

  public JButton getCheckButton() {
    return myCheckButton;
  }

  public JButton getPrevTaskButton() {
    return myPrevTaskButton;
  }

  private JButton addButton(@NotNull final JComponent parentComponent, String toolTipText, Icon icon) {
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
    myDefaultEditor = TextEditorProvider.getInstance().createEditor(project, file);
    myComponent = myDefaultEditor.getComponent();
    JPanel studyPanel = new JPanel();
    studyPanel.setLayout(new BoxLayout(studyPanel, BoxLayout.Y_AXIS));
    TaskFile taskFile = StudyTaskManager.getInstance(project).getTaskFile(file);
    if (taskFile != null) {
      Task currentTask = taskFile.getTask();
      String taskText = currentTask.getResourceText(project, currentTask.getText(), false);
      final JLabel taskTextLabel = new JLabel(taskText);
      taskTextLabel.setBorder(new EmptyBorder(15, 20, 0, 100));
      int fontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
      String fontName = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName();
      taskTextLabel.setFont(new Font(fontName, Font.PLAIN, fontSize));
      HideableTitledPanel taskTextPanel = new HideableTitledPanel(TASK_TEXT_HEADER, taskTextLabel, true);
      taskTextPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
      studyPanel.add(taskTextPanel);
      JPanel studyButtonPanel = new JPanel(new GridLayout(1, 2));
      JPanel taskActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      studyButtonPanel.add(taskActionsPanel);
      studyButtonPanel.add(new JPanel());
      initializeButtons(project, taskActionsPanel, taskFile);
      studyPanel.add(studyButtonPanel);
      myComponent.add(studyPanel, BorderLayout.NORTH);
    }
  }

  private void initializeButtons(@NotNull final Project project, @NotNull final JPanel taskActionsPanel, @NotNull final TaskFile taskFile) {
    myCheckButton = addButton(taskActionsPanel, "Check task", StudyIcons.Resolve);
    myPrevTaskButton = addButton(taskActionsPanel, "Prev Task", StudyIcons.Prev);
    myNextTaskButton = addButton(taskActionsPanel, "Next Task", StudyIcons.Next);
    myRefreshButton = addButton(taskActionsPanel, "Start task again", StudyIcons.Refresh24);
    if (!taskFile.getTask().getUserTests().isEmpty()) {
      JButton runButton = addButton(taskActionsPanel, "Run", StudyIcons.Run);
      runButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          StudyRunAction studyRunAction = (StudyRunAction)ActionManager.getInstance().getAction("StudyRunAction");
          studyRunAction.run(project);
        }
      });
      myWatchInputButton = addButton(taskActionsPanel, "Watch test input", StudyIcons.WatchInput);
      myWatchInputButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          WatchInputAction watchInputAction = (WatchInputAction)ActionManager.getInstance().getAction("WatchInputAction");
          watchInputAction.showInput(project);
        }
      });
    }
    myCheckButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CheckAction checkAction = (CheckAction)ActionManager.getInstance().getAction("CheckAction");
        checkAction.check(project);
      }
    });

    myNextTaskButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NextTaskAction nextTaskAction = (NextTaskAction)ActionManager.getInstance().getAction("NextTaskAction");
        nextTaskAction.navigateTask(project);
      }
    });
    myPrevTaskButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PreviousTaskAction prevTaskAction = (PreviousTaskAction)ActionManager.getInstance().getAction("PreviousTaskAction");
        prevTaskAction.navigateTask(project);
      }
    });
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        RefreshTaskAction refreshTaskAction = (RefreshTaskAction)ActionManager.getInstance().getAction("RefreshTaskAction");
        refreshTaskAction.refresh(project);
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
    return myComponent;
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
      FileEditor fileEditor =
        FileEditorManagerImpl.getInstanceEx(project).getSplitters().getCurrentWindow().getSelectedEditor().getSelectedEditorWithProvider()
          .getFirst();
      if (fileEditor instanceof StudyEditor) {
        return (StudyEditor)fileEditor;
      }
    } catch (Exception e) {
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
}
