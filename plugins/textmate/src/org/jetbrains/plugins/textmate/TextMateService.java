package org.jetbrains.plugins.textmate;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.language.SnippetsRegistry;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.Preferences;
import org.jetbrains.plugins.textmate.language.preferences.TextMateShellVariable;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateCustomTextAttributes;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTheme;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.util.List;
import java.util.Map;

public abstract class TextMateService {
  protected static final Logger LOG = Logger.getInstance(TextMateService.class);

  public static TextMateService getInstance() {
    return ServiceManager.getService(TextMateService.class);
  }

  public abstract void reloadThemesFromDisk();

  /**
   * Create bundle object from given directory.
   *
   * @param directory
   * @return bundle object or {@code null} if directory doesn't exist or bundle type can't be defined
   */
  @Nullable
  public abstract Bundle createBundle(@NotNull VirtualFile directory);

  /**
   * Register new TextMate theme in IDE from given file.
   * On this method invocation the theme also will be mapped to IDE native theme with the same name (if it exists)
   *
   * @param themeFile
   * @return true on success registration and false otherwise
   */
  public abstract boolean registerTheme(@Nullable VirtualFile themeFile);

  /**
   * @return current textmate theme (defining based on current native theme and configured mapping)
   */
  public abstract TextMateTheme getCurrentTheme();

  /**
   * Register all enabled bundles in IDE {@link org.jetbrains.plugins.textmate.configuration.TextMateSettings.TextMateSettingsState#getBundles()}
   * 1. read all enabled bundles
   * 2. prepare syntax table of supported languages
   * 3. prepare preferences table of enabled bundles
   * 4. registering {@link org.jetbrains.plugins.textmate.language.TextMateFileType} for extensions declared in bundles
   */
  public abstract void registerEnabledBundles(boolean loadBuiltin);

  /**
   * Unregister bundles scenario:
   * <ul>
   * <li>removes textmate file types (according to argument value)</li>
   * <li>clear textmate syntax table</li>
   * <li>clear preferences table</li>
   * <li>clear extensions mapping</li>
   * </ul>
   * <p/>
   * <p/>
   */
  public abstract void unregisterAllBundles(boolean unregisterFileTypes);

  @Nullable
  public abstract TextMateLanguageDescriptor getLanguageDescriptorByExtension(String extension);

  @Nullable
  public abstract TextMateShellVariable getVariable(@NotNull String name, @NotNull EditorEx editor);
  
  @NotNull
  public abstract SnippetsRegistry getSnippetsRegistry();

  @Nullable
  public abstract TextMateLanguageDescriptor getLanguageDescriptorByFileName(String fileName);

  /**
   * @return names of all registered TextMate themes {@link this#registerTheme(VirtualFile)}
   */
  public abstract String[] getThemeNames();

  /**
   * @param selector scope of current context
   * @return preferences that matched to current context.
   *         Sorted by weigh matching {@link TextMateSelectorWeigher}
   */
  @NotNull
  public abstract List<Preferences> getPreferencesForSelector(String selector);

  /**
   * @return custom highlighting colors defined inside bundles (not in themes). 
   * Note that background color in text attributes is stored in raw format and isn't merged with default background.
   */
  @NotNull
  public abstract Map<String, TextMateCustomTextAttributes> getCustomHighlightingColors();

  public abstract PlistReader getPlistReader();
  
  public abstract void addListener(@NotNull TextMateBundleListener listener);
  public abstract void removeListener(@NotNull TextMateBundleListener listener);
  public abstract void clearListeners();

  public interface TextMateBundleListener {
    void colorSchemeChanged();
  }
}
