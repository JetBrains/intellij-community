package com.jetbrains.edu.coursecreator;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.courseFormat.Course;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCUtils {
  @Nullable
  public static CCLanguageManager getStudyLanguageManager(@NotNull final Course course) {
    Language language = Language.findLanguageByID(course.getLanguage());
    return language == null ? null :  CCLanguageManager.INSTANCE.forLanguage(language);
  }

  public static boolean isAnswerFile(PsiElement element) {
    if (!(element instanceof PsiFile)) {
      return false;
    }
    VirtualFile file = ((PsiFile)element).getVirtualFile();
    return CCProjectService.getInstance(element.getProject()).isAnswerFile(file);
  }
}
