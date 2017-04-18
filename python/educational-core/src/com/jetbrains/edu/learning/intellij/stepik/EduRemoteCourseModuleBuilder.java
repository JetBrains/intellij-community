package com.jetbrains.edu.learning.intellij.stepik;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.InvalidDataException;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.intellij.generation.EduProjectGenerator;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

class EduRemoteCourseModuleBuilder extends ModuleBuilder {
  private static final Logger LOG = Logger.getInstance(EduRemoteCourseModuleBuilder.class);
  private final Course myCourse;
  private final ModuleType myModuleType;
  private final ModuleBuilder myModuleBuilder;
  private EduProjectGenerator myGenerator = new EduProjectGenerator();

  public EduRemoteCourseModuleBuilder(Course course) {
    myCourse = course;
    myModuleType = EduPluginConfigurator.INSTANCE.forLanguage(myCourse.getLanguageById()).getModuleType();
    myModuleBuilder = myModuleType.createModuleBuilder();
  }

  @Nullable
  @Override
  public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    if (module == null) {
      return null;
    }
    String languageName = myCourse.getLanguageID();
    Language language = Language.findLanguageByID(languageName);
    if (language == null) {
      return module;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
    if (configurator == null) {
      return module;
    }
    configurator.configureModule(module);
    return module;
  }

  @Nullable
  @Override
  public String getBuilderId() {
    return "remote.course.builder." + myCourse.getName();
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return myModuleBuilder.modifyProjectTypeStep(settingsStep);
  }

  @NotNull
  @Override
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    Module baseModule = super.createModule(moduleModel);
    String languageName = myCourse.getLanguageID();
    Language language = Language.findLanguageByID(languageName);
    if (language != null) {
      EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
      if (configurator != null) {
        Project project = baseModule.getProject();
        myGenerator.setSelectedCourse(myCourse);
        myGenerator.generateProject(project, project.getBaseDir());
        Course course = StudyTaskManager.getInstance(project).getCourse();
        if (course == null) {
          LOG.info("failed to generate course");
          return baseModule;
        }
        configurator.createCourseModuleContent(moduleModel, project, course, getModuleFileDirectory());
      }
    }
    return baseModule;
  }

  @Override
  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    myModuleBuilder.setupRootModel(modifiableRootModel);
  }

  @Override
  public ModuleType getModuleType() {
    return myModuleType;
  }

  @Override
  public void setContentEntryPath(String moduleRootPath) {
    myModuleBuilder.setContentEntryPath(moduleRootPath);
    super.setContentEntryPath(moduleRootPath);
  }
}
