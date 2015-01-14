package com.jetbrains.edu.coursecreator;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.coursecreator.format.Course;
import org.jetbrains.annotations.NotNull;


public class CCProjectGeneratorUtil {
  private CCProjectGeneratorUtil() {
  }

  public static void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @NotNull final String name, @NotNull final String author,
                              @NotNull final String description) {

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = new Course(name, author, description);
    service.setCourse(course);

    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
    if (projectDir == null) return;
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
        try {
          FileTemplateUtil.createFromTemplate(template, "test_helper.py", null, projectDir);
        }
        catch (Exception ignored) {
        }
        DirectoryUtil.createSubdirectories("hints", projectDir, "\\/");
        final PsiDirectory lessonDir = CCCreateLesson.createLesson(projectDir, 1, null, null, course);
        CCCreateTask.createTask(null, project, lessonDir, false);
      }
    }.execute();
  }

}
