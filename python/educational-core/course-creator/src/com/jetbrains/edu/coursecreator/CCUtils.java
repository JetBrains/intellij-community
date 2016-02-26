package com.jetbrains.edu.coursecreator;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
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
  public static final String GENERATED_FILES_FOLDER = ".coursecreator";

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


  public static VirtualFile getGeneratedFilesFolder(@NotNull Project project, @NotNull Module module) {
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile folder = baseDir.findChild(GENERATED_FILES_FOLDER);
    if (folder != null) {
      return folder;
    }
    final Ref<VirtualFile> generatedRoot = new Ref<>();
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              generatedRoot.set(baseDir.createChildDirectory(this, GENERATED_FILES_FOLDER));
              final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
              ContentEntry entry = MarkRootActionBase.findContentEntry(model, generatedRoot.get());
              if (entry == null) {
                LOG.info("Failed to find contentEntry for archive folder");
                return;
              }
              entry.addExcludeFolder(generatedRoot.get());
              model.commit();
              module.getProject().save();
            }
            catch (IOException e) {
              LOG.info("Failed to create folder for generated files", e);
            }
          }
        });
      }
    });
    return generatedRoot.get();
  }
}
