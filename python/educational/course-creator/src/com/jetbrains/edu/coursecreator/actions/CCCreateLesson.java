package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.StudyItem;
import icons.EducationalIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CCCreateLesson extends CCCreateStudyItemActionBase {
  public static final String TITLE = "Create New " + EduNames.LESSON_TITLED;

  public CCCreateLesson() {
    super(EduNames.LESSON_TITLED, TITLE, EducationalIcons.Lesson);
  }

  @Nullable
  @Override
  protected PsiDirectory getParentDir(@NotNull Project project, @NotNull Course course, @NotNull PsiDirectory directory) {
    return PsiManager.getInstance(project).findDirectory(project.getBaseDir());
  }

  @Override
  protected void addItem(@NotNull Course course, @NotNull StudyItem item) {
    course.addLesson(((Lesson)item));
  }

  @Override
  protected Function<VirtualFile, ? extends StudyItem> getStudyOrderable(@NotNull final StudyItem item) {
    return new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        if (item instanceof Lesson) {
          return ((Lesson)item).getCourse().getLesson(file.getName());
        }
        return null;
      }
    };
  }

  @Override
  @Nullable
  protected PsiDirectory createItemDir(@NotNull final Project project, @NotNull final StudyItem item,
                                    @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                                    @NotNull final Course course) {
    final PsiDirectory[] lessonDirectory = new PsiDirectory[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        lessonDirectory[0] = DirectoryUtil.createSubdirectories(EduNames.LESSON + item.getIndex(), parentDirectory, "\\/");
      }
    });
    if (lessonDirectory[0] != null) {
      if (view != null) {
        view.selectElement(lessonDirectory[0]);
      }
    }
    return lessonDirectory[0];
  }

  @Override
  protected int getSiblingsSize(@NotNull Course course, @Nullable StudyItem item) {
    return course.getLessons().size();
  }

  @Nullable
  @Override
  protected StudyItem getParentItem(@NotNull Course course, @NotNull PsiDirectory directory) {
    return null;
  }

  @Nullable
  @Override
  protected StudyItem getThresholdItem(@NotNull final Course course, @NotNull final PsiDirectory sourceDirectory) {
    return course.getLesson(sourceDirectory.getName());
  }

  @Override
  protected boolean isAddedAsLast(@NotNull PsiDirectory sourceDirectory,
                                  @NotNull Project project,
                                  @NotNull Course course) {
    return sourceDirectory.getVirtualFile().equals(project.getBaseDir());
  }

  @Override
  protected List<? extends StudyItem> getSiblings(@NotNull Course course, @Nullable StudyItem parentItem) {
    return course.getLessons();
  }

  @Override
  protected String getItemName() {
    return EduNames.LESSON;
  }

  @Override
  protected StudyItem createAndInitItem(@NotNull Course course, @Nullable StudyItem parentItem, String name, int index) {
    Lesson lesson = new Lesson();
    lesson.setName(name);
    lesson.setCourse(course);
    lesson.setIndex(index);
    return lesson;
  }
}