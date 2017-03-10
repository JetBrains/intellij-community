package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.ui.CCHint;
import com.jetbrains.edu.learning.actions.StudyShowHintAction;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.ui.StudyHint;
import org.jetbrains.annotations.NotNull;

public class CCShowHintAction extends StudyShowHintAction {
  @NotNull
  @Override
  protected StudyHint getHint(Project project, AnswerPlaceholder answerPlaceholder) {
    return new CCHint(answerPlaceholder, project);
  }
}
