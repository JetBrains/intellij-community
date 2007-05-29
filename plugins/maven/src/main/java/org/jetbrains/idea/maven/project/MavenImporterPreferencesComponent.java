package org.jetbrains.idea.maven.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;

/**
 * @author Vladislav.Kaznacheev
 */
@State(name = "MavenImportPreferences", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenImporterPreferencesComponent implements PersistentStateComponent<MavenImporterPreferences> {

  public static MavenImporterPreferencesComponent getInstance(final Project project) {
    return project.getComponent(MavenImporterPreferencesComponent.class);
  }

  private MavenImporterPreferences preferences = new MavenImporterPreferences();

  public MavenImporterPreferences getState() {
    return preferences;
  }

  public void loadState(MavenImporterPreferences state) {
    preferences = state;
  }
}
