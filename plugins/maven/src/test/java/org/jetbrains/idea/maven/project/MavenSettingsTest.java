package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.utils.MavenSettings;

public class MavenSettingsTest extends MavenTestCase {
  public void testCloningGeneralSettingsWithoutListeners() throws Exception {
    final String[] log = new String[]{""};

    MavenGeneralSettings s = new MavenGeneralSettings();
    s.addListener(new MavenGeneralSettings.Listener() {
      public void pathChanged() {
        log[0] += "changed ";
      }
    });

    s.setMavenHome("home");
    assertEquals("changed ", log[0]);

    s.clone().setMavenHome("new home");
    assertEquals("changed ", log[0]);
  }

  public void testCloningImportingSettingsWithoutListeners() throws Exception {
    final String[] log = new String[]{""};

    MavenImportingSettings s = new MavenImportingSettings();
    s.addListener(new MavenImportingSettings.Listener() {
      public void autoImportChanged() {
      }

      public void createModuleGroupsChanged() {
      }

      public void createModuleForAggregatorsChanged() {
        log[0] += "changed ";
      }
    });

    s.setCreateModulesForAggregators(true);
    assertEquals("changed ", log[0]);

    s.clone().setCreateModulesForAggregators(false);
    assertEquals("changed ", log[0]);
  }

  public void testImportingSettings() throws Exception {
    assertTrue(new MavenImportingSettings().equals(new MavenImportingSettings()));
    MavenImportingConfigurable importingConfigurable = new MavenImportingConfigurable(new MavenImportingSettings());
    importingConfigurable.reset();
    assertFalse(importingConfigurable.isModified());
  }

  public void testNotModifiedAfterCreation() throws Exception {
    MavenSettings s = new MavenSettings(myProject);
    s.reset();
    try {
      assertFalse(s.isModified());
    }
    finally {
      s.disposeUIResources(); //prevent memory leaks
    }

    for (Configurable each : s.getConfigurables()) {
      each.reset();
      try {
        assertFalse(each.isModified());
      }
      finally {
        each.disposeUIResources(); //prevent memory leaks
      }
    }
  }
}
