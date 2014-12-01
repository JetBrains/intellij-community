package org.jetbrains.idea.svn.difftool;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "SvnDiffSettings",
  storages = {@Storage(
    file = DiffUtil.DIFF_CONFIG)})
public class SvnDiffSettingsHolder implements PersistentStateComponent<SvnDiffSettingsHolder.SvnDiffSettings> {
  public static class SvnDiffSettings {
    public static final Key<SvnDiffSettings> KEY = Key.create("SvnDiffSettings");

    float SPITTER_PROPORTION = 0.9f;
    boolean HIDE_PROPERTIES = false;

    public SvnDiffSettings() {
    }

    public SvnDiffSettings(float SPITTER_PROPORTION, boolean HIDE_PROPERTIES) {
      this.SPITTER_PROPORTION = SPITTER_PROPORTION;
      this.HIDE_PROPERTIES = HIDE_PROPERTIES;
    }

    @NotNull
    private SvnDiffSettings copy() {
      return new SvnDiffSettings(SPITTER_PROPORTION, HIDE_PROPERTIES);
    }

    public boolean isHideProperties() {
      return HIDE_PROPERTIES;
    }

    public void setHideProperties(boolean value) {
      HIDE_PROPERTIES = value;
    }

    public float getSplitterProportion() {
      return SPITTER_PROPORTION;
    }

    public void setSplitterProportion(float value) {
      SPITTER_PROPORTION = value;
    }

    //
    // Impl
    //

    @NotNull
    public static SvnDiffSettings getSettings() {
      return getInstance().getState().copy();
    }

    @NotNull
    public static SvnDiffSettings getSettingsDefaults() {
      return getInstance().getState();
    }
  }

  private SvnDiffSettings myState = new SvnDiffSettings();

  @NotNull
  public SvnDiffSettings getState() {
    return myState;
  }

  public void loadState(SvnDiffSettings state) {
    myState = state;
  }

  public static SvnDiffSettingsHolder getInstance() {
    return ServiceManager.getService(SvnDiffSettingsHolder.class);
  }
}
