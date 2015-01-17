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

    private SharedSettings SHARED_SETTINGS = new SharedSettings();

    private static class SharedSettings {
      float SPLITTER_PROPORTION = 0.9f;
      boolean HIDE_PROPERTIES = false;
    }

    public SvnDiffSettings() {
    }

    public SvnDiffSettings(@NotNull SharedSettings SHARED_SETTINGS) {
      this.SHARED_SETTINGS = SHARED_SETTINGS;
    }

    @NotNull
    private SvnDiffSettings copy() {
      return new SvnDiffSettings(SHARED_SETTINGS);
    }

    public boolean isHideProperties() {
      return SHARED_SETTINGS.HIDE_PROPERTIES;
    }

    public void setHideProperties(boolean value) {
      SHARED_SETTINGS.HIDE_PROPERTIES = value;
    }

    public float getSplitterProportion() {
      return SHARED_SETTINGS.SPLITTER_PROPORTION;
    }

    public void setSplitterProportion(float value) {
      SHARED_SETTINGS.SPLITTER_PROPORTION = value;
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
