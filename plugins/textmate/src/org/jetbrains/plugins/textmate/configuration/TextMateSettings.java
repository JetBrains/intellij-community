package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@State(name = "TextMateSettings", storages = @Storage(StoragePathMacros.CACHE_FILE))
@Service
public final class TextMateSettings implements PersistentStateComponent<TextMateSettings.TextMateSettingsState> {
  private TextMateSettingsState myState;

  public static TextMateSettings getInstance() {
    return ApplicationManager.getApplication().getService(TextMateSettings.class);
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

  public static final class TextMateSettingsState {
    @XCollection(style = XCollection.Style.v2)
    private final ArrayList<BundleConfigBean> bundles = new ArrayList<>();

    @Transient
    public List<BundleConfigBean> getBundles() {
      return new ArrayList<>(bundles);
    }

    // transient because XML serializer should set value directly, but our setter transforms data and accepts not List, but Collection
    @Transient
    public void setBundles(@NotNull Collection<BundleConfigBean> value) {
      bundles.clear();
      bundles.ensureCapacity(value.size());
      for (BundleConfigBean bundle : value) {
        bundles.add(bundle.copy());
      }
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


