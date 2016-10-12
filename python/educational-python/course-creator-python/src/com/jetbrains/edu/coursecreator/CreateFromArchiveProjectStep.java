package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.jetbrains.edu.coursecreator.actions.CCCreateCourseArchive;
import com.jetbrains.edu.learning.PyStudyDirectoryProjectGenerator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.python.newProject.actions.PyCharmNewProjectStep;
import org.jetbrains.annotations.NotNull;

class CreateFromArchiveProjectStep extends PyCharmNewProjectStep {

  public CreateFromArchiveProjectStep(Project project, Module module) {
    super(new MyCustomization(project, module));
  }

  protected static class MyCustomization extends PyCharmNewProjectStep.Customization {

    private final Project myProject;
    private final Module myModule;
    private PyStudyDirectoryProjectGenerator myGenerator = new PyStudyDirectoryProjectGenerator();

    public MyCustomization(Project project,
                           Module module) {

      myProject = project;
      myModule = module;
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator[] getProjectGenerators() {
      return new DirectoryProjectGenerator[] {};
    }


    @NotNull
    @Override
    protected DirectoryProjectGenerator createEmptyProjectGenerator() {
      Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course != null) {
        VirtualFile folder = CCUtils.getGeneratedFilesFolder(myProject, myModule);
        String zipName = FileUtil.sanitizeFileName(course.getName());
        String locationDir = folder.getPath();
        CCCreateCourseArchive.createCourseArchive(myProject, myModule, zipName, locationDir, false);
        String path = FileUtil.join(FileUtil.toSystemDependentName(folder.getPath()), zipName + ".zip");
        StudyProjectGenerator generator = myGenerator.getGenerator();
        CourseInfo info = generator.addLocalCourse(path);
        assert info != null;
        generator.setSelectedCourse(info);
      }
      return myGenerator;
    }
  }
}
