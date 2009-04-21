package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.utils.MavenSettings;
import com.intellij.openapi.options.Configurable;

public class MavenSettingsTest extends MavenTestCase {
  public void testImportingSettings() throws Exception {
    assertTrue(new MavenImportingSettings().equals(new MavenImportingSettings()));
    MavenImportingConfigurable importingConfigurable = new MavenImportingConfigurable(new MavenImportingSettings());
    importingConfigurable.reset();
    assertFalse(importingConfigurable.isModified());
  }

  public void testNotModifiedAfterCreation() throws Exception {
    MavenSettings s = new MavenSettings(myProject);
    s.reset();
    assertFalse(s.isModified());

    for (Configurable each : s.getConfigurables()) {
      each.reset();
      assertFalse(each.isModified());
    }
  }
}
