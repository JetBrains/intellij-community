package com.jetbrains.edu.coursecreator;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class CCUtils {
  private static final Logger LOG = Logger.getInstance(CCUtils.class);

  @Nullable
  public static CCLanguageManager getStudyLanguageManager(@NotNull final Course course) {
    Language language = Language.findLanguageByID(course.getLanguage());
    return language == null ? null : CCLanguageManager.INSTANCE.forLanguage(language);
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
   * @param dirs              directories that are used to get tasks/lessons
   * @param getStudyItem function that is used to get task/lesson from VirtualFile. This function can return null
   * @param threshold         index is used as threshold
   * @param prefix            task or lesson directory name prefix
   */
  public static void updateHigherElements(VirtualFile[] dirs,
                                          @NotNull final Function<VirtualFile, ? extends StudyItem> getStudyItem,
                                          final int threshold,
                                          final String prefix,
                                          final int delta) {
    ArrayList<VirtualFile> dirsToRename = new ArrayList<VirtualFile>
      (Collections2.filter(Arrays.asList(dirs), new Predicate<VirtualFile>() {
        @Override
        public boolean apply(VirtualFile dir) {
          final StudyItem item = getStudyItem.fun(dir);
          if (item == null) {
            return false;
          }
          int index = item.getIndex();
          return index > threshold;
        }
      }));
    Collections.sort(dirsToRename, new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile o1, VirtualFile o2) {
        StudyItem item1 = getStudyItem.fun(o1);
        StudyItem item2 = getStudyItem.fun(o2);
        //if we delete some dir we should start increasing numbers in dir names from the end
        return (-delta) * EduUtils.INDEX_COMPARATOR.compare(item1, item2);
      }
    });

    for (final VirtualFile dir : dirsToRename) {
      final StudyItem item = getStudyItem.fun(dir);
      final int newIndex = item.getIndex() + delta;
      item.setIndex(newIndex);
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

  public static boolean isLessonDir(PsiDirectory sourceDirectory) {
    if (sourceDirectory == null) {
      return false;
    }
    CCProjectService service = CCProjectService.getInstance(sourceDirectory.getProject());
    Course course = service.getCourse();
    if (course != null && course.getLesson(sourceDirectory.getName()) != null) {
      return true;
    }
    return false;
  }
}
