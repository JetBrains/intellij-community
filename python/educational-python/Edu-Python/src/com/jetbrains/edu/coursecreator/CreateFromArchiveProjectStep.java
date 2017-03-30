package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.jetbrains.edu.coursecreator.actions.CCCreateCourseArchive;
import com.jetbrains.edu.learning.PyStudyDirectoryProjectGenerator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.python.newProject.steps.PyCharmNewProjectStep;
import org.jetbrains.annotations.NotNull;

class CreateFromArchiveProjectStep extends PyCharmNewProjectStep {

  public CreateFromArchiveProjectStep(Project project, Module module) {
    super(new MyCustomization(project, module));
  }

  protected static class MyCustomization extends PyCharmNewProjectStep.Customization {

    private final Project myProject;
    private final Module myModule;
    private PyStudyDirectoryProjectGenerator myGenerator = new PyStudyDirectoryProjectGenerator(true);

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
      Course currentCourse = StudyTaskManager.getInstance(myProject).getCourse();
      if (currentCourse != null) {
        VirtualFile folder = CCUtils.getGeneratedFilesFolder(myProject, myModule);
        String zipName = FileUtil.sanitizeFileName(currentCourse.getName());
        if (zipName.isEmpty()) {
          zipName = EduNames.COURSE;
        }
        String locationDir = folder.getPath();
        CCCreateCourseArchive.createCourseArchive(myProject, myModule, zipName, locationDir, false);
        String path = FileUtil.join(FileUtil.toSystemDependentName(folder.getPath()), zipName + ".zip");
        StudyProjectGenerator generator = myGenerator.getGenerator();
        Course course = generator.addLocalCourse(path);
        assert course != null;
        generator.setSelectedCourse(course);
      }
      return myGenerator;
    }
  }
}
