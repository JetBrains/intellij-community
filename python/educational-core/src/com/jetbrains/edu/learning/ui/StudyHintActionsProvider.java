package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StudyHintActionsProvider {
  ExtensionPointName<StudyHintActionsProvider> EP_NAME = ExtensionPointName.create("Edu.hintActionsProvider");

  List<AnAction> getAdditionalHintActions(@Nullable AnswerPlaceholder answerPlaceholder);
}
