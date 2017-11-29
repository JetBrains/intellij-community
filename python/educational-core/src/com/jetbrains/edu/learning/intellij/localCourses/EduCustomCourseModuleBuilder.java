package com.jetbrains.edu.learning.intellij.localCourses;

import com.intellij.ide.util.projectWizard.*;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.InvalidDataException;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.intellij.generation.EduProjectGenerator;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

class EduCustomCourseModuleBuilder extends JavaModuleBuilder {
  private static final Logger LOG = Logger.getInstance(EduCustomCourseModuleBuilder.class);
  private EduProjectGenerator myGenerator = new EduProjectGenerator();
  private Course mySelectedCourse;

  @Nullable
  @Override
  public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    if (module == null) {
      return null;
    }
    String languageName = mySelectedCourse.getLanguageID();
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
    return "custom.course.builder";
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return new ModuleWizardStep[]{new EduCourseSelectionSettingsStep(myGenerator)};
  }


  private class EduCourseSelectionSettingsStep extends ModuleWizardStep {

    private final StudyProjectGenerator myGenerator;

    EduCourseSelectionSettingsStep(StudyProjectGenerator generator) {
      myGenerator = generator;
    }

    @Override
    public JComponent getComponent() {
      return new EduLocalCoursePanel(myGenerator, EduCustomCourseModuleBuilder.this).getContentPanel();
    }

    @Override
    public void updateDataModel() {
    }
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return ProjectWizardStepFactory.getInstance().createJavaSettingsStep(settingsStep, this, Conditions.alwaysTrue());
  }

  void setSelectedCourse(Course selectedCourse) {
    mySelectedCourse = selectedCourse;
  }

  @NotNull
  @Override
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    Module baseModule = super.createModule(moduleModel);
    if (mySelectedCourse != null) {
      String languageName = mySelectedCourse.getLanguageID();
      Language language = Language.findLanguageByID(languageName);
      if (language != null) {
        EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
        if (configurator != null) {
          myGenerator.setSelectedCourse(mySelectedCourse);
          Project project = baseModule.getProject();
          myGenerator.generateProject(project, project.getBaseDir());
          Course course = StudyTaskManager.getInstance(project).getCourse();
          if (course == null) {
            LOG.info("failed to generate course");
            return baseModule;
          }
          configurator.createCourseModuleContent(moduleModel, project, course, getModuleFileDirectory());
        }
      }
    }
    return baseModule;
  }
}
