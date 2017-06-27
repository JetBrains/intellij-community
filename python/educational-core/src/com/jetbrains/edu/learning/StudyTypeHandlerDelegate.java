package com.jetbrains.edu.learning;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HyperlinkAdapter;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.actions.placeholder.CCActivatePlaceholder;
import com.jetbrains.edu.coursecreator.actions.placeholder.CCAnswerPlaceholderAction;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.Collections;
import java.util.stream.Collectors;

public class StudyTypeHandlerDelegate extends TypedHandlerDelegate {

  public static final String ACTIVATE = "activate";
  public static final String SWITCH = "switch to";

  @Override
  public Result checkAutoPopup(char charTyped, Project project, Editor editor, PsiFile file) {
    return handleTyping(project, editor, file);
  }

  @Override
  public Result beforeCharTyped(char c,
                                Project project,
                                Editor editor,
                                PsiFile file,
                                FileType fileType) {
    return handleTyping(project, editor, file);
  }

  @NotNull
  private static Result handleTyping(Project project, Editor editor, PsiFile file) {
    if (!StudyUtils.isStudyProject(project)) {
      return Result.CONTINUE;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, file.getVirtualFile());
    if (taskFile == null || !(taskFile.getTask() instanceof TaskWithSubtasks)) {
      return Result.CONTINUE;
    }
    int offset = editor.getCaretModel().getOffset();
    if (CCUtils.isCourseCreator(project)) {
      AnswerPlaceholder placeholder = StudyUtils.getAnswerPlaceholder(offset, taskFile.getAnswerPlaceholders());
      if (placeholder == null || placeholder.isActive()) {
        return Result.CONTINUE;
      }
      Integer toSubtask = Collections.max(placeholder.getSubtaskInfos().keySet().stream()
                                            .filter(k -> k < ((TaskWithSubtasks)taskFile.getTask()).getActiveSubtaskIndex())
                                            .collect(Collectors.toList()));
      int userVisibleSubtaskNum = toSubtask + 1;
      String text = String
        .format("<html>To edit this placeholder <a href=\"%s\">activate</a> it or <a href=\"%s\">switch to subtask %d</a></html>", ACTIVATE,
                SWITCH, userVisibleSubtaskNum);
      MyHyperlinkListener listener = new MyHyperlinkListener(taskFile, placeholder, file, editor, project, toSubtask);
      HintManager.getInstance().showInformationHint(editor, HintUtil.createInformationLabel(text,
                                                                                            listener, null,
                                                                                            null));
      return Result.STOP;
    }
    boolean insidePlaceholder = taskFile.getAnswerPlaceholder(offset) != null;
    if (!insidePlaceholder) {
      HintManager.getInstance().showInformationHint(editor, "Text outside of placeholders is not editable in this task");
    }
    return insidePlaceholder ? Result.CONTINUE : Result.STOP;
  }

  private static void activatePlaceholder(TaskFile taskFile, AnswerPlaceholder placeholder, PsiFile file, Editor editor, Project project) {
    AnAction action = ActionManager.getInstance().getAction(CCActivatePlaceholder.ACTION_ID);
    if (action != null) {
      CCAnswerPlaceholderAction.CCState state =
        new CCAnswerPlaceholderAction.CCState(taskFile, placeholder, file, editor, project);
      ((CCActivatePlaceholder)action).performAnswerPlaceholderAction(state);
    }
  }

  private static class MyHyperlinkListener extends HyperlinkAdapter {
    private final TaskFile myTaskFile;
    private final AnswerPlaceholder myPlaceholder;
    private final PsiFile myFile;
    private final Editor myEditor;
    private final Project myProject;
    private final Integer myToSubtask;

    public MyHyperlinkListener(TaskFile taskFile,
                               AnswerPlaceholder placeholder,
                               PsiFile file,
                               Editor editor,
                               Project project,
                               Integer toSubtask) {
      myTaskFile = taskFile;
      myPlaceholder = placeholder;
      myFile = file;
      myEditor = editor;
      myProject = project;
      myToSubtask = toSubtask;
    }

    @Override
    protected void hyperlinkActivated(
      HyperlinkEvent e) {
      String description = e.getDescription();
      if (ACTIVATE.equals(description)) {
        activatePlaceholder(myTaskFile, myPlaceholder,
                            myFile, myEditor,
                            myProject);
        HintManager.getInstance().hideAllHints();
        return;
      }
      if (SWITCH.equals(description)) {
        StudySubtaskUtils.switchStep(myProject,
                                     (TaskWithSubtasks)myTaskFile
                                       .getTask(),
                                     myToSubtask);
      }
    }
  }
}
