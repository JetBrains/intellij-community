package org.intellij.plugins.markdown.injection;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This extension point allows adding support for a new language in code fences.
 * Basically, implementing this EP will allow IDE to provide most of the language features
 * in code fences (i.e. highlighting, completion, ...) if the current language is supported by the IDE
 * (language support is bundled with the current IDE, or there is an active plugin with such support).
 */
public interface CodeFenceLanguageProvider {
  ExtensionPointName<CodeFenceLanguageProvider> EP_NAME = ExtensionPointName.create("org.intellij.markdown.fenceLanguageProvider");

  /**
   * Implement this method to provide custom rule for selecting {@link Language} to inject into the code fences
   * @param infoString the string with "info string" of the code fence. Not trimmed nor lowercased.
   * @return Language which should be injected into the code fence with the given infoString.
   *         No custom injection rule is applied if null is returned.
   * @see <a href="http://spec.commonmark.org/0.27/#info-string">Info String</a>
   * @see <a href="http://spec.commonmark.org/0.27/#code-fence">Code Fence</a>
   */
  @Nullable
  Language getLanguageByInfoString(@NotNull String infoString);

  /**
   * Implement this method to provide custom completion variants for info strings in the all fences.
   * Note that a special insertHandler for handling code fence opening will be prepended for your lookup.
   * That means that the custom insert handlers should be ready for uncommitted documents and rely only on editor and document.
   * See {@link org.intellij.plugins.markdown.editor.CodeFenceLanguageListCompletionProvider} for the details on implementation.
   * @return A list of {@link LookupElement} which will be prepended to the default language list.
   */
  @NotNull
  List<LookupElement> getCompletionVariantsForInfoString(@NotNull CompletionParameters parameters);
}
