package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;

import java.util.*;

@State(name = "TextMateSettings", storages = @Storage("textmate_os.xml"))
public class TextMateSettings implements PersistentStateComponent<TextMateSettings.TextMateSettingsState> {
  @NonNls public static final String DEFAULT_THEME_NAME = "Mac Classic";

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

  @NotNull
  public String getTextMateThemeName(@NotNull String generalThemeName, @Nullable TextMateService textMateService) {
    if (textMateService == null) {
      return DEFAULT_THEME_NAME;
    }

    String result = myState != null ? myState.getThemesMapping().get(generalThemeName) : null;
    if (result != null) {
      return result;
    }

    for (String name : textMateService.getThemeNames()) {
      if (name.equalsIgnoreCase(generalThemeName)) {
        result = name;
        break;
      }
    }
    result = ObjectUtils.notNull(result, DEFAULT_THEME_NAME);
    if (myState != null) {
      myState.getThemesMapping().put(generalThemeName, result);
    }
    return result;
  }

  public static class TextMateSettingsState {
    private Map<String, String> themesMapping;

    @OptionTag
    private List<BundleConfigBean> bundles = new ArrayList<>();

    public TextMateSettingsState() {
      this(new HashMap<>());
    }

    public TextMateSettingsState(Map<String, String> themesMapping) {
      this.themesMapping = themesMapping;
    }

    @NotNull
    public Map<String, String> getThemesMapping() {
      return themesMapping;
    }

    public void setThemesMapping(Map<String, String> themesMapping) {
      this.themesMapping = themesMapping;
    }

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


