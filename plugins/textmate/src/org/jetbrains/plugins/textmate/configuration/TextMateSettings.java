package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@State(name = "TextMateSettings", storages = @Storage(value = "textmate_os.xml", roamingType = RoamingType.DISABLED))
public class TextMateSettings implements PersistentStateComponent<TextMateSettings.TextMateSettingsState> {

  private TextMateSettingsState myState;

  public static TextMateSettings getInstance() {
    return ServiceManager.getService(TextMateSettings.class);
  }

  @Nullable
  @Override
  public TextMateSettingsState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull TextMateSettingsState state) {
    myState = state;
  }

  public Collection<BundleConfigBean> getBundles() {
    return myState != null ? myState.getBundles() : Collections.emptyList();
  }

  public static class TextMateSettingsState {
    @OptionTag
    private List<BundleConfigBean> bundles = new ArrayList<>();

    @Transient
    public List<BundleConfigBean> getBundles() {
      return bundles;
    }

    // transient because XML serializer should set value directly, but our setter transforms data and accepts not List, but Collection
    @Transient
    public void setBundles(@NotNull Collection<BundleConfigBean> bundles) {
      List<BundleConfigBean> newList = new ArrayList<>(bundles.size());
      for (BundleConfigBean bundle : bundles) {
        newList.add(bundle.copy());
      }
      this.bundles = newList;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TextMateSettingsState state = (TextMateSettingsState)o;

      if (!bundles.equals(state.bundles)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return bundles.hashCode();
    }
  }
}


