package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @deprecated use {@link TextMateUserBundlesSettings} instead
 */
@Deprecated
public final class TextMateSettings implements PersistentStateComponent<TextMateSettings.TextMateSettingsState> {
  public static TextMateSettings getInstance() {
    return new TextMateSettings();
  }

  @Nullable
  @Override
  public TextMateSettingsState getState() {
    TextMateUserBundlesSettings settings = TextMateUserBundlesSettings.getInstance();
    if (settings != null) {
      TextMateSettingsState state = new TextMateSettingsState();
      Collection<BundleConfigBean> bundles = new ArrayList<>();
      for (Map.Entry<String, TextMatePersistentBundle> entry : settings.getBundles().entrySet()) {
        bundles.add(new BundleConfigBean(entry.getValue().getName(),
                                         entry.getKey(),
                                         entry.getValue().getEnabled()));
      }
      state.setBundles(bundles);
      return state;
    }
    else {
      return null;
    }
  }

  @Override
  public void loadState(@NotNull TextMateSettingsState state) {
    TextMateUserBundlesSettings settings = TextMateUserBundlesSettings.getInstance();
    if (settings != null) {
      Map<String, TextMatePersistentBundle> bundles = new HashMap<>();
      for (BundleConfigBean bundle : state.bundles) {
        bundles.put(bundle.getPath(), new TextMatePersistentBundle(bundle.getName(), bundle.isEnabled()));
      }
      settings.setBundlesConfig(bundles);
    }
  }

  public Collection<BundleConfigBean> getBundles() {
    TextMateSettingsState state = getState();
    return state != null ? state.getBundles() : Collections.emptyList();
  }

  public static final class TextMateSettingsState {
    private final ArrayList<BundleConfigBean> bundles = new ArrayList<>();

    public List<BundleConfigBean> getBundles() {
      return new ArrayList<>(bundles);
    }

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


