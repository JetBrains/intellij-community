package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class StepicAdaptiveReactionsPanel extends JPanel {
  private final ReactionButtonPanel myHardPanel;
  private final ReactionButtonPanel myBoringPanel;
  @NotNull private final Project myProject;
  private static final int TOO_HARD_REACTION = 0;
  private static final int TOO_BORING_REACTION = -1;
  private static final String HARD_REACTION = "Too Hard";
  private static final String BORING_REACTION = "Too Boring";
  private static final String SOLVED_TASK_TOOLTIP = "Reaction Disabled Due To Task Is Solved";
  private static final String HARD_LABEL_TOOLTIP = "Click To Get An Easier Task";
  private static final String BORING_LABEL_TOOLTIP = "Click To Get A More Challenging Task";

  public StepicAdaptiveReactionsPanel(@NotNull final Project project) {
    myProject = project;
    setLayout(new GridBagLayout());
    setBackground(UIUtil.getTextFieldBackground());

    myHardPanel = new ReactionButtonPanel(HARD_REACTION, HARD_LABEL_TOOLTIP, TOO_HARD_REACTION);
    myBoringPanel = new ReactionButtonPanel(BORING_REACTION, BORING_LABEL_TOOLTIP, TOO_BORING_REACTION);
    addFileListener();

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    add(Box.createVerticalStrut(3), c);
    c.gridx = 1;
    c.gridy = 1;
    add(Box.createHorizontalStrut(3), c);
    c.weightx = 1;
    c.gridx = 2;
    add(myHardPanel, c);
    c.gridx = 3;
    c.weightx = 0;
    add(Box.createHorizontalStrut(3), c);
    c.weightx = 1;
    c.gridx = 4;
    add(myBoringPanel, c);
    c.gridx = 5;
    c.weightx = 0;
    add(Box.createHorizontalStrut(3), c);
  }

  public void setEnabledRecursive(final boolean isEnabled) {
    ApplicationManager.getApplication().invokeLater(() -> {
      super.setEnabled(isEnabled);
      myHardPanel.setEnabledRecursive(isEnabled);
      myBoringPanel.setEnabledRecursive(isEnabled);
    });
  }

  private void addFileListener() {
    final FileEditorManagerListener editorManagerListener = new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {

      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        final com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getTaskFromSelectedEditor(myProject);
        final boolean isEnabled = task != null && task.getStatus() != StudyStatus.Solved;
        StepicAdaptiveReactionsPanel.this.setEnabledRecursive(isEnabled);
      }
    };
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorManagerListener);
  }

  private class ReactionButtonPanel extends JPanel {
    private final JPanel myButtonPanel;
    private final JLabel myLabel;

    public ReactionButtonPanel(@NotNull final String text,
                               @NotNull final String enabledTooltip,
                               int reaction) {
      com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getTaskFromSelectedEditor(myProject);
      final boolean isEnabled = task != null && task.getStatus() != StudyStatus.Solved;

      myLabel = new JLabel(text);

      myButtonPanel = new JPanel();
      myButtonPanel.setLayout(new BoxLayout(myButtonPanel, BoxLayout.PAGE_AXIS));
      myButtonPanel.setToolTipText(isEnabled && task.getStatus() == StudyStatus.Solved ? enabledTooltip : SOLVED_TASK_TOOLTIP);
      myButtonPanel.add(Box.createVerticalStrut(5));
      myButtonPanel.add(myLabel);
      myButtonPanel.add(Box.createVerticalStrut(5));

      setEnabledRecursive(isEnabled);

      setLayout(new GridBagLayout());
      setBorder(BorderFactory.createEtchedBorder());
      add(myButtonPanel);
      addMouseListener(reaction);
    }

    private void addMouseListener(int reaction) {
      final ReactionMouseAdapter mouseAdapter = new ReactionMouseAdapter(this, reaction);
      this.addMouseListener(mouseAdapter);
      myButtonPanel.addMouseListener(mouseAdapter);
      myLabel.addMouseListener(mouseAdapter);
    }

    public void setEnabledRecursive(final boolean isEnabled) {
      ApplicationManager.getApplication().invokeLater(() -> {
        super.setEnabled(isEnabled);
        myButtonPanel.setEnabled(isEnabled);
        myLabel.setEnabled(isEnabled);
      });
    }

    private class ReactionMouseAdapter extends MouseAdapter {
      private final ReactionButtonPanel myPanel;
      private final int myReaction;

      public ReactionMouseAdapter(@NotNull final ReactionButtonPanel mainPanel, int reaction) {
        myReaction = reaction;
        myPanel = mainPanel;
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1 && isEnabled()) {
          final com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getCurrentTask(myProject);
          if (task != null && task.getStatus() != StudyStatus.Solved) {
            final ProgressIndicatorBase progress = new ProgressIndicatorBase();
            progress.setText("Loading Next Recommendation");
            ProgressManager.getInstance().run(new Task.Backgroundable(myProject,
                                                                      "Loading Next Recommendation") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                StepicAdaptiveReactionsPanel.this.setEnabledRecursive(false);
                ApplicationManager.getApplication().invokeLater(()->setBackground(UIUtil.getLabelBackground()));
                EduAdaptiveStepicConnector.addNextRecommendedTask(myProject, myReaction, indicator);
                StepicAdaptiveReactionsPanel.this.setEnabledRecursive(true);
              }
            });
          }
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        final com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getCurrentTask(myProject);
        if (task != null && task.getStatus() != StudyStatus.Solved && myPanel.isEnabled()) {
          setBackground(JBColor.GRAY);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setBackground(UIUtil.getLabelBackground());
      }

      private void setBackground(Color color) {
        myPanel.setBackground(color);
        myButtonPanel.setBackground(color);
      }
    }
  }
}

