package com.jetbrains.edu.learning;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.edu.coursecreator.PyCCProjectGenerator;
import org.jetbrains.annotations.NonNls;

import java.io.File;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class PyStudyInitialConfigurator implements ApplicationComponent {
  @NonNls private static final String CONFIGURED_V40 = "StudyPyCharm.InitialConfiguration40";
  private final PyCCProjectGenerator myGenerator = new PyCCProjectGenerator();

  /**
   * @noinspection UnusedParameters
   */
  public PyStudyInitialConfigurator(MessageBus bus,
                                    CodeInsightSettings codeInsightSettings,
                                    final PropertiesComponent propertiesComponent,
                                    FileTypeManager fileTypeManager,
                                    final ProjectManagerEx projectManager) {
    if (!propertiesComponent.getBoolean(CONFIGURED_V40)) {
      final File courses = new File(PathManager.getConfigPath(), "courses");
      FileUtil.delete(courses);
      propertiesComponent.setValue(CONFIGURED_V40, "true");
    }
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(StudySettings.SETTINGS_CHANGED, () -> {
      ExtensionsArea rootArea = Extensions.getArea(null);
      final boolean creatorEnabled = StudySettings.getInstance().isCourseCreatorEnabled();
      final ExtensionPoint<DirectoryProjectGenerator> extensionPoint = rootArea.getExtensionPoint(DirectoryProjectGenerator.EP_NAME);
      if (creatorEnabled) {
        if (!extensionPoint.hasExtension(myGenerator)) {
          extensionPoint.registerExtension(myGenerator);
        }
      }
      else {
        if (extensionPoint.hasExtension(myGenerator)) {
          extensionPoint.unregisterExtension(myGenerator);
        }
      }
    });
  }
}
