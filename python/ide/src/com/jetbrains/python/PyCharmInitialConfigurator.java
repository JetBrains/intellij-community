// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import org.jetbrains.annotations.NonNls;

/**
 * Initialize PyCharm.
 *
 * This class is called <strong>only in PyCharm</strong>.
 * It does not work in plugin
 * @author yole
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class PyCharmInitialConfigurator {
  @NonNls private static final String DISPLAYED_PROPERTY = "PyCharm.initialConfigurationShown";

  public PyCharmInitialConfigurator(MessageBus bus, final PropertiesComponent propertiesComponent, final FileTypeManager fileTypeManager) {
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration", "true");
      EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V2")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V2", true);
      final CodeStyleSettings settings = CodeStyle.getDefaultSettings();
      settings.getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V3")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V3", "true");
      final String ignoredFilesList = fileTypeManager.getIgnoredFilesList();
      ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> FileTypeManager.getInstance().setIgnoredFilesList(ignoredFilesList + ";*$py.class")));
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V4")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V4", true);
      PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP = false;
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V5")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V5", true);
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;
    }
    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V6")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V6", true);
      CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE = true;
    }

    if (!propertiesComponent.getBoolean("PyCharm.InitialConfiguration.V7")) {
      propertiesComponent.setValue("PyCharm.InitialConfiguration.V7", true);
    }

    if (!propertiesComponent.isValueSet(DISPLAYED_PROPERTY)) {
      bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void welcomeScreenDisplayed() {
          ApplicationManager.getApplication().invokeLater(() -> {
            propertiesComponent.setValue(DISPLAYED_PROPERTY, "true");
          });
        }
      });
    }

    Registry.get("ide.ssh.one.time.password").setValue(true);
  }
}
