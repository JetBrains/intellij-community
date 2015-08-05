package com.jetbrains.edu.coursecreator;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.StudyOrderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class CCUtils {
  private static final Logger LOG = Logger.getInstance(CCUtils.class);

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

  /**
   * This method decreases index and updates directory names of
   * all tasks/lessons that have higher index than specified object
   * @param dirs directories that are used to get tasks/lessons
   * @param getStudyOrderable function that is used to get task/lesson from VirtualFile. This function can return null
   * @param thresholdObject task/lesson which index is used as threshold
   * @param prefix task or lesson directory name prefix
   */
  public static void updateHigherElements(VirtualFile[] dirs,
                                          @NotNull Function<VirtualFile, StudyOrderable> getStudyOrderable,
                                          @NotNull final StudyOrderable thresholdObject,
                                          final String prefix) {
    int threshold = thresholdObject.getIndex();
    for (final VirtualFile dir : dirs) {
      final StudyOrderable orderable = getStudyOrderable.fun(dir);
      if (orderable == null) {
        continue;
      }
      int index = orderable.getIndex();
      if (index > threshold) {
        final int newIndex = index - 1;
        orderable.setIndex(newIndex);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              dir.rename(this, prefix + newIndex);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        });
      }
    }
  }
}
