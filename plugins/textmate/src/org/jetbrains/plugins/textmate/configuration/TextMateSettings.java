package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TextMateBundleToLoad;
import org.jetbrains.plugins.textmate.TextMateServiceImpl;

import java.util.*;

/**
 * @deprecated use {@link TextMateUserBundlesSettings} and {@link TextMateBuiltinBundlesSettings} instead
 */
@Deprecated(forRemoval = true)
public final class TextMateSettings implements PersistentStateComponent<TextMateSettings.TextMateSettingsState> {
  public static TextMateSettings getInstance() {
    return new TextMateSettings();
  }

  @NotNull
  @Override
  public TextMateSettingsState getState() {
    TextMateSettingsState state = new TextMateSettingsState();
    ArrayList<BundleConfigBean> bundles = new ArrayList<>();
    TextMateUserBundlesSettings settings = TextMateUserBundlesSettings.getInstance();
    if (settings != null) {
      for (Map.Entry<String, TextMatePersistentBundle> entry : settings.getBundles().entrySet()) {
        bundles.add(new BundleConfigBean(entry.getValue().getName(),
                                         entry.getKey(),
                                         entry.getValue().getEnabled()));
      }
    }
    TextMateBuiltinBundlesSettings builtinBundlesSettings = TextMateBuiltinBundlesSettings.getInstance();
    if (builtinBundlesSettings != null) {
      Set<String> turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames();
      for (TextMateBundleToLoad bundle : TextMateServiceImpl.discoverBuiltinBundles(builtinBundlesSettings)) {
        bundles.add(new BundleConfigBean(bundle.getName(), bundle.getPath(), !turnedOffBundleNames.contains(bundle.getName())));
      }
    }
    state.setBundles(bundles);
    return state;
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
    return getState().getBundles();
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


