package com.jetbrains.edu.coursecreator;

import com.intellij.lang.Language;
import com.jetbrains.edu.courseFormat.Course;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCUtils {
  @Nullable
  public static CCLanguageManager getStudyLanguageManager(@NotNull final Course course) {
    Language language = Language.findLanguageByID(course.getLanguage());
    return language == null ? null :  CCLanguageManager.INSTANCE.forLanguage(language);
  }
}
