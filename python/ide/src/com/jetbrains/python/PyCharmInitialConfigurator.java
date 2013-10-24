/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.application.options.InitialConfigurationDialog;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class PyCharmInitialConfigurator {
  @NonNls private static final String DISPLAYED_PROPERTY = "PyCharm.initialConfigurationShown";

  public PyCharmInitialConfigurator(MessageBus bus, final PropertiesComponent propertiesComponent, final FileTypeManager fileTypeManager) {
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration", false)) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration", "true");
      EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V2", false)) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V2", "true");
      final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance().getCurrentSettings();
      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
      settings.getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
      UISettings.getInstance().SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = true;
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V3", false)) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V3", "true");
      UISettings.getInstance().SHOW_MEMORY_INDICATOR = false;
      final String ignoredFilesList = fileTypeManager.getIgnoredFilesList();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          FileTypeManager.getInstance().setIgnoredFilesList(ignoredFilesList + ";*$py.class");
        }
      });
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V4", false)) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V4", "true");
      PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP = false;
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V5", false)) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V5", "true");
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;
    }
    if (!propertiesComponent.isValueSet(DISPLAYED_PROPERTY)) {
      bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
        @Override
        public void welcomeScreenDisplayed() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              propertiesComponent.setValue(DISPLAYED_PROPERTY, "true");
              showInitialConfigurationDialog();
            }
          });
        }
      });
    }
  }

  private static void showInitialConfigurationDialog() {
    final JFrame frame = WindowManager.getInstance().findVisibleFrame();
    new InitialConfigurationDialog(frame, "Python").show();
  }
}
