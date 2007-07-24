package org.jetbrains.idea.maven.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;

/**
 * @author Vladislav.Kaznacheev
 */
@State(name = "MavenImportPreferences", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenWorkspacePreferencesComponent extends DummyProjectComponent implements PersistentStateComponent<MavenWorkspacePreferences> {

  public MavenWorkspacePreferencesComponent() {
    super("MavenImportPreferences");
  }

  public static MavenWorkspacePreferencesComponent getInstance(final Project project) {
    return project.getComponent(MavenWorkspacePreferencesComponent.class);
  }

  private MavenWorkspacePreferences preferences = new MavenWorkspacePreferences();

  public MavenWorkspacePreferences getState() {
    return preferences;
  }

  public void loadState(MavenWorkspacePreferences state) {
    preferences = state;
  }
}
