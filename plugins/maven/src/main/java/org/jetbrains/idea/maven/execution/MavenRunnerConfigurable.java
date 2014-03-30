package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenRunnerConfigurable extends MavenRunnerPanel implements SearchableConfigurable, Configurable.NoScroll {

  public MavenRunnerConfigurable(@NotNull Project p, boolean isRunConfiguration) {
    super(p, isRunConfiguration);
  }

  @Nullable
  protected abstract MavenRunnerSettings getState();

  public boolean isModified() {
    MavenRunnerSettings s = new MavenRunnerSettings();
    setData(s);
    return !s.equals(getState());
  }

  public void apply() throws ConfigurationException {
    setData(getState());
  }

  public void reset() {
    getData(getState());
  }

  @Nls
  public String getDisplayName() {
    return RunnerBundle.message("maven.tab.runner");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.runner";
  }

  @NotNull
  public String getId() {
    //noinspection ConstantConditions
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public void disposeUIResources() {

  }
}
