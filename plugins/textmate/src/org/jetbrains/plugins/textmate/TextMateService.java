package org.jetbrains.plugins.textmate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.bundles.TextMateBundleReader;
import org.jetbrains.plugins.textmate.bundles.TextMateFileNameMatcher;
import org.jetbrains.plugins.textmate.configuration.TextMateBuiltinBundlesSettings;
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.PreferencesRegistry;
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistry;
import org.jetbrains.plugins.textmate.language.preferences.SnippetsRegistry;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter;

import java.nio.file.Path;
import java.util.Map;

public abstract class TextMateService {
  protected static final Logger LOG = Logger.getInstance(TextMateService.class);

  public static TextMateService getInstance() {
    return ApplicationManager.getApplication().getService(TextMateService.class);
  }

  /**
   * Create bundle object from given directory.
   *
   * @return bundle object or {@code null} if directory doesn't exist or bundle type can't be defined
   */
  public @Nullable TextMateBundleReader readBundle(@Nullable Path directory) {
    return null;
  }

  /**
   * Unregister all and register all enabled bundles in IDE {@link TextMateUserBundlesSettings#getBundles()}, {@link TextMateBuiltinBundlesSettings#builtinBundles}
   * 1. read all enabled bundles
   * 2. prepare syntax table of supported languages
   * 3. prepare preferences table of enabled bundles
   * 4. fill the extensions mapping for {@link TextMateFileType}
   */
  public abstract void reloadEnabledBundles();

  public abstract @Nullable TextMateLanguageDescriptor getLanguageDescriptorByExtension(@Nullable CharSequence extension);

  public abstract @NotNull ShellVariablesRegistry getShellVariableRegistry();

  public abstract @NotNull SnippetsRegistry getSnippetRegistry();

  public abstract @NotNull PreferencesRegistry getPreferenceRegistry();

  public abstract @Nullable TextMateLanguageDescriptor getLanguageDescriptorByFileName(@NotNull CharSequence fileName);

  public abstract @NotNull Map<TextMateFileNameMatcher, CharSequence> getFileNameMatcherToScopeNameMapping();

  /**
   * @return custom highlighting colors defined inside bundles (not in themes).
   * Note that background color in text attributes is stored in raw format and isn't merged with default background.
   */
  public abstract @NotNull Map<CharSequence, TextMateTextAttributesAdapter> getCustomHighlightingColors();
}
