/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.configurationStore.JdomSerializer;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTrackerSettings;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.idea.maven.utils.MavenSettings;

import static com.intellij.configurationStore.DefaultStateSerializerKt.deserializeState;

public class MavenSettingsTest extends MavenTestCase {
  public void testCloningGeneralSettingsWithoutListeners() {
    final String[] log = new String[]{""};

    MavenGeneralSettings s = new MavenGeneralSettings();
    s.addListener(new MavenGeneralSettings.Listener() {
      @Override
      public void changed() {
        log[0] += "changed ";
      }
    });

    s.setMavenHomeType(MavenWrapper.INSTANCE);
    assertEquals("changed ", log[0]);

    s.clone().setMavenHomeType(BundledMaven3.INSTANCE);
    assertEquals("changed ", log[0]);
  }

  public void testCloningImportingSettingsWithoutListeners() {
    final String[] log = new String[]{""};

    MavenImportingSettings s = new MavenImportingSettings();
    s.addListener(new MavenImportingSettings.Listener() {
      @Override
      public void createModuleForAggregatorsChanged() {
        log[0] += "changed ";
      }

      @Override
      public void updateAllProjectStructure() {
      }
    });

    s.setCreateModulesForAggregators(true);
    s.setCreateModulesForAggregators(false);
    assertEquals("changed ", log[0]);

    s.clone().setCreateModulesForAggregators(true);
    s.clone().setCreateModulesForAggregators(false);
    assertEquals("changed ", log[0]);
  }

  public void testImportingSettings() {
    allowAccessToDirsIfExists(System.getenv("JAVA_HOME"));
    assertEquals(new MavenImportingSettings(), new MavenImportingSettings());
    MavenImportingConfigurable importingConfigurable = new MavenImportingConfigurable(getProject());
    importingConfigurable.reset();
    assertFalse(importingConfigurable.isModified());
  }

  public void testNotModifiedAfterCreation() {
    MavenSettings s = new MavenSettings(getProject());
    s.createComponent();
    s.reset();
    try {
      assertFalse(s.isModified());
    }
    finally {
      s.disposeUIResources(); //prevent memory leaks
    }

    for (Configurable each : s.getConfigurables()) {
      each.createComponent();
      each.reset();
      try {
        assertFalse(each.isModified());
      }
      finally {
        each.disposeUIResources(); //prevent memory leaks
      }
    }
  }

  @SuppressWarnings("deprecation")
  public void testMavenSettingsMigration() {
    replaceService(getProject(), ExternalSystemProjectTrackerSettings.class, new AutoImportProjectTrackerSettings(), () -> {
      ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(getProject());
      MavenWorkspaceSettingsComponent workspaceSettingsComponent = loadWorkspaceComponent(
        """
          <MavenImportPreferences>
            <option name="importingSettings">
              <MavenImportingSettings>
                <option name="importAutomatically" value="true" />
              </MavenImportingSettings>
            </option>
          </MavenImportPreferences>
          """);
      assertFalse(workspaceSettingsComponent.getSettings().getImportingSettings().isImportAutomatically());
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.ALL, projectTrackerSettings.getAutoReloadType());
      assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent));
    });
    replaceService(getProject(), ExternalSystemProjectTrackerSettings.class, new AutoImportProjectTrackerSettings(), () -> {
      ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(getProject());
      MavenWorkspaceSettingsComponent workspaceSettingsComponent = loadWorkspaceComponent(
        """
          <MavenImportPreferences>
            <option name="importingSettings">
              <MavenImportingSettings>
                <option name="importAutomatically" value="false" />
              </MavenImportingSettings>
            </option>
          </MavenImportPreferences>
          """);
      assertFalse(workspaceSettingsComponent.getSettings().getImportingSettings().isImportAutomatically());
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.SELECTIVE, projectTrackerSettings.getAutoReloadType());
      assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent));
    });
    replaceService(getProject(), ExternalSystemProjectTrackerSettings.class, new AutoImportProjectTrackerSettings(), () -> {
      ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(getProject());
      MavenWorkspaceSettingsComponent workspaceSettingsComponent = loadWorkspaceComponent("<MavenWorkspacePersistedSettings />");
      assertFalse(workspaceSettingsComponent.getSettings().getImportingSettings().isImportAutomatically());
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.SELECTIVE, projectTrackerSettings.getAutoReloadType());
      assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent));
    });
  }

  private MavenWorkspaceSettingsComponent loadWorkspaceComponent(CharSequence rawWorkspaceSettingsComponent) {
    try {
      MavenWorkspaceSettingsComponent workspaceSettingsComponent = new MavenWorkspaceSettingsComponent(getProject());
      Element workspaceSettingsElement = JDOMUtil.load(rawWorkspaceSettingsComponent);
      MavenWorkspacePersistedSettings workspaceSettings = deserializeState(workspaceSettingsElement, MavenWorkspacePersistedSettings.class, null);
      workspaceSettingsComponent.loadState(workspaceSettings);
      return workspaceSettingsComponent;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> void replaceService(ComponentManager componentManager, Class<T> serviceInterface, T instance, Runnable action) {
    Disposable parentDisposable = Disposer.newDisposable();
    try {
      ServiceContainerUtil.replaceService(componentManager, serviceInterface, instance, parentDisposable);
      action.run();
    }
    finally {
      Disposer.dispose(parentDisposable);
    }
  }

  private static String storeWorkspaceComponent(MavenWorkspaceSettingsComponent workspaceSettingsComponent) {
    try {
      MavenWorkspacePersistedSettings workspaceSettings = workspaceSettingsComponent.getState();
      JdomSerializer jdomSerializer = XmlSerializer.getJdomSerializer();
      SkipDefaultsSerializationFilter serializationFilter = jdomSerializer.getDefaultSerializationFilter();
      Element workspaceSettingsElement = jdomSerializer.serialize(workspaceSettings, serializationFilter, true);
      return new XMLOutputter().outputString(workspaceSettingsElement);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
