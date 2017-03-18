package com.jetbrains.edu.coursecreator.intellij;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.InvalidDataException;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.intellij.generation.EduCourseModuleBuilder;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;

class EduCCModuleBuilder extends EduCourseModuleBuilder {
  private CCNewProjectPanel myPanel = new CCNewProjectPanel();
  private ComboBox myLanguageComboBox = new ComboBox();
  private static final Logger LOG = Logger.getInstance(EduCCModuleBuilder.class);

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    ModuleWizardStep javaSettingsStep =
      ProjectWizardStepFactory.getInstance().createJavaSettingsStep(settingsStep, this, Conditions.alwaysTrue());
    Function<JTextField, String> getValue = JTextComponent::getText;
    getWizardInputField("ccname", "", "Name:", myPanel.getNameField(), getValue).addToSettings(settingsStep);
    getWizardInputField("ccauthor", "", "Author:", myPanel.getAuthorField(), getValue).addToSettings(settingsStep);

    LanguageExtensionPoint[] extensions = new ExtensionPointName<LanguageExtensionPoint>(EduPluginConfigurator.EP_NAME).getExtensions();
    myLanguageComboBox.removeAllItems();
    for (LanguageExtensionPoint extension : extensions) {
      String languageId = extension.getKey();
      Language language = Language.findLanguageByID(languageId);
      if (language == null) {
        LOG.info("Language with id " + languageId + " not found");
        continue;
      }
      myLanguageComboBox.addItem(new LanguageWrapper(language));
    }
    getWizardInputField("cclang", "", "Language:", myLanguageComboBox, comboBox -> (String)comboBox.getSelectedItem())
      .addToSettings(settingsStep);
    getWizardInputField("ccdescr", "", "Description:", myPanel.getDescriptionField(), JTextArea::getText).addToSettings(settingsStep);
    return javaSettingsStep;
  }

  @Override
  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    setSourcePaths(Collections.emptyList());
    super.setupRootModel(rootModel);
  }


  @NotNull
  @Override
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists,
           JDOMException, ConfigurationException {
    Module module = super.createModule(moduleModel);
    Project project = module.getProject();
    final Course course = new Course();
    course.setName(myPanel.getName());
    course.setAuthorsAsString(myPanel.getAuthors());
    course.setDescription(myPanel.getDescription());
    LanguageWrapper wrapper = (LanguageWrapper)myLanguageComboBox.getSelectedItem();
    Language language = wrapper.getLanguage();
    course.setLanguage(language.getID());
    course.setCourseMode(CCUtils.COURSE_MODE);
    File courseDir = new File(StudyProjectGenerator.OUR_COURSES_DIR, myPanel.getName() + "-" + project.getName());
    course.setCourseDirectory(courseDir.getPath());
    Lesson lesson = new Lesson();
    Task task = new Task();
    task.setName("task1");
    lesson.addTask(task);
    lesson.setName("lesson1");
    course.addLesson(lesson);
    course.initCourse(false);
    StudyTaskManager.getInstance(project).setCourse(course);
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
    String languageName = language.getDisplayName();
    if (configurator == null) {
      LOG.error("EduPluginConfigurator for language " + languageName + " not found");
      return module;
    }
    configurator.createCourseModuleContent(moduleModel, project, course, getModuleFileDirectory());
    return module;
  }

  @NotNull
  private <T extends JComponent> WizardInputField<T> getWizardInputField(String id,
                                                                         String defaultValue,
                                                                         String label,
                                                                         T component,
                                                                         Function<T, String> getValue) {
    return new WizardInputField<T>(id, defaultValue) {
      @Override
      public String getLabel() {
        return label;
      }

      @Override
      public T getComponent() {
        return component;
      }

      @Override
      public String getValue() {
        return getValue.apply(component);
      }
    };
  }


  private class LanguageWrapper {
    private final Language myLanguage;

    LanguageWrapper(Language language) {
      myLanguage = language;
    }

    Language getLanguage() {
      return myLanguage;
    }

    @Override
    public String toString() {
      return myLanguage.getDisplayName();
    }
  }
}
