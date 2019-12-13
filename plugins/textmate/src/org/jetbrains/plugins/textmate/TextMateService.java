package org.jetbrains.plugins.textmate;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.language.SnippetsRegistry;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.PreferencesRegistry;
import org.jetbrains.plugins.textmate.language.preferences.TextMateShellVariable;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateCustomTextAttributes;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.util.Map;

public abstract class TextMateService {
  protected static final Logger LOG = Logger.getInstance(TextMateService.class);

  public static TextMateService getInstance() {
    return ServiceManager.getService(TextMateService.class);
  }

  /**
   * Create bundle object from given directory.
   *
   * @param directory
   * @return bundle object or {@code null} if directory doesn't exist or bundle type can't be defined
   */
  @Nullable
  public abstract Bundle createBundle(@NotNull VirtualFile directory);

  /**
   * Unregister all and register all enabled bundles in IDE {@link org.jetbrains.plugins.textmate.configuration.TextMateSettings.TextMateSettingsState#getBundles()}
   * 1. read all enabled bundles
   * 2. prepare syntax table of supported languages
   * 3. prepare preferences table of enabled bundles
   * 4. fill the extensions mapping for {@link org.jetbrains.plugins.textmate.language.TextMateFileType}
   */
  public abstract void reloadEnabledBundles();

  /**
   * @deprecated use {@link #reloadEnabledBundles()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public void registerEnabledBundles(boolean builtin) {
    reloadEnabledBundles();
  }

  @Nullable
  public abstract TextMateLanguageDescriptor getLanguageDescriptorByExtension(@Nullable CharSequence extension);

  @Nullable
  public abstract TextMateShellVariable getVariable(@NotNull String name, @NotNull EditorEx editor);
  
  @NotNull
  public abstract SnippetsRegistry getSnippetsRegistry();

  @NotNull
  public abstract PreferencesRegistry getPreferencesRegistry();

  @Nullable
  public abstract TextMateLanguageDescriptor getLanguageDescriptorByFileName(@NotNull CharSequence fileName);

  /**
   * @return custom highlighting colors defined inside bundles (not in themes). 
   * Note that background color in text attributes is stored in raw format and isn't merged with default background.
   */
  @NotNull
  public abstract Map<CharSequence, TextMateCustomTextAttributes> getCustomHighlightingColors();

  public abstract PlistReader getPlistReader();
}
