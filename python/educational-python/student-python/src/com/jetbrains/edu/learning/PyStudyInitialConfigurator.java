package com.jetbrains.edu.learning;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.edu.learning.actions.PyStudyIntroductionCourseAction;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class PyStudyInitialConfigurator {
  private static final Logger LOG = Logger.getInstance(PyStudyInitialConfigurator.class.getName());
  @NonNls private static final String CONFIGURED_V35 = "StudyPyCharm.InitialConfiguration35";

  /**
   * @noinspection UnusedParameters
   */
  public PyStudyInitialConfigurator(MessageBus bus,
                                    CodeInsightSettings codeInsightSettings,
                                    final PropertiesComponent propertiesComponent,
                                    FileTypeManager fileTypeManager,
                                    final ProjectManagerEx projectManager) {
    final File file = new File(getCoursesRoot(), "Introduction to Python.zip");
    if (!propertiesComponent.getBoolean(CONFIGURED_V35) && file.exists()) {
      try {
        File[] children = StudyProjectGenerator.OUR_COURSES_DIR.listFiles(
          (dir, name) -> name.equals(PyStudyIntroductionCourseAction.INTRODUCTION_TO_PYTHON));
        if (children != null) {
          for (File child : children) {
            FileUtil.delete(child);
          }
        }
        copyCourse(file, StudyProjectGenerator.OUR_COURSES_DIR);
        propertiesComponent.setValue(CONFIGURED_V35, "true");
      }
      catch (IOException e) {
        LOG.warn("Couldn't copy bundled courses " + e);
      }
    }
  }

  private static void copyCourse(File bundledCourse, File userCourseDir) throws IOException {
    String fileName = bundledCourse.getName();
    String unzippedName = fileName.substring(0, fileName.indexOf("."));
    File courseDir = new File(userCourseDir, unzippedName);
    ZipUtil.unzip(null, courseDir, bundledCourse, null, null, true);
  }

  private static File getCoursesRoot() {
    @NonNls String jarPath = PathUtil.getJarPathForClass(PyStudyInitialConfigurator.class);
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);


      File pluginBaseDir = jarFile.getParentFile();
      return new File(pluginBaseDir, "courses");
    }

    return new File(jarPath, "courses");
  }
}
