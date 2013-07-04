package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenGeneralConfigurable extends MavenGeneralPanel implements SearchableConfigurable {

  protected abstract MavenGeneralSettings getState();

  public boolean isModified() {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(getState());
  }

  public void apply() {
    setData(getState());
  }

  public void reset() {
    getData(getState());
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.dialog.project.maven";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

}
