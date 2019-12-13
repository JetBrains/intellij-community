package org.jetbrains.plugins.textmate.editor;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.Preferences;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;

import java.util.*;

public final class TextMateEditorUtils {
  @Nullable
  public static CharSequence getCurrentScopeSelector(@NotNull EditorEx editor) {
    final EditorHighlighter highlighter = editor.getHighlighter();
    SelectionModel selection = editor.getSelectionModel();
    final int offset = selection.hasSelection() ? selection.getSelectionStart() : editor.getCaretModel().getOffset();
    final HighlighterIterator iterator = highlighter.createIterator(offset);
    String result = null;
    if (offset != 0 || !iterator.atEnd()) {
      final IElementType tokenType = iterator.getTokenType();
      result = tokenType != null ? tokenType.toString() : null;
    }
    //retrieve root scope of file
    if (result == null) {
      final VirtualFile file = editor.getVirtualFile();
      if (file != null) {
        final TextMateLanguageDescriptor languageDescriptor = TextMateService.getInstance().getLanguageDescriptorByFileName(file.getName());
        if (languageDescriptor != null) {
          return languageDescriptor.getScopeName();
        }
      }
    }
    return result;
  }

  @Nullable
  public static TextMateBracePair getHighlightingPairForLeftChar(char c, @Nullable String currentSelector) {
    if (!TextMateService.getInstance().getPreferencesRegistry().isPossibleLeftHighlightingBrace(c)) {
      return null;
    }
    Set<TextMateBracePair> pairs = getAllPairsForMatcher(currentSelector);
    for (TextMateBracePair pair : pairs) {
      if (c == pair.leftChar) {
        return pair;
      }
    }
    return null;
  }

  @Nullable
  public static TextMateBracePair getHighlightingPairForRightChar(char c, @Nullable String currentSelector) {
    if (!TextMateService.getInstance().getPreferencesRegistry().isPossibleRightHighlightingBrace(c)) {
      return null;
    }
    Set<TextMateBracePair> pairs = getAllPairsForMatcher(currentSelector);
    for (TextMateBracePair pair : pairs) {
      if (c == pair.rightChar) {
        return pair;
      }
    }
    return null;
  }

  @Nullable
  public static TextMateBracePair getSmartTypingPairForLeftChar(char c, @Nullable CharSequence currentSelector) {
    if (!TextMateService.getInstance().getPreferencesRegistry().isPossibleLeftSmartTypingBrace(c)) {
      return null;
    }
    Set<TextMateBracePair> pairs = getSmartTypingPairs(currentSelector);
    for (TextMateBracePair pair : pairs) {
      if (c == pair.leftChar) {
        return pair;
      }
    }
    return null;
  }

  @Nullable
  public static TextMateBracePair getSmartTypingPairForRightChar(char c, @Nullable CharSequence currentSelector) {
    if (!TextMateService.getInstance().getPreferencesRegistry().isPossibleRightSmartTypingBrace(c)) {
      return null;
    }
    Set<TextMateBracePair> pairs = getSmartTypingPairs(currentSelector);
    for (TextMateBracePair pair : pairs) {
      if (c == pair.rightChar) {
        return pair;
      }
    }
    return null;
  }

  private static Set<TextMateBracePair> getAllPairsForMatcher(@Nullable String selector) {
    Set<TextMateBracePair> result = new HashSet<>();
    if (selector != null) {
      List<Preferences> preferencesForSelector = TextMateService.getInstance().getPreferencesRegistry().getPreferences(selector);
      for (Preferences preferences : preferencesForSelector) {
        final Set<TextMateBracePair> highlightingPairs = preferences.getHighlightingPairs();
        if (highlightingPairs != null) {
          if (highlightingPairs.isEmpty()) {
            // smart typing pairs can be defined in preferences but can be empty (in order to disable smart typing at all)
            return Collections.emptySet();
          } else {
            result.addAll(highlightingPairs);
          }
        }
      }
      for (Preferences preferences : preferencesForSelector) {
        final Set<TextMateBracePair> smartTypingPairs = preferences.getSmartTypingPairs();
        if (smartTypingPairs != null) {
          result.addAll(preferences.getSmartTypingPairs());
        }
      }
    }
    result.addAll(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS);
    return result;
  }

  private static Set<TextMateBracePair> getSmartTypingPairs(@Nullable CharSequence currentSelector) {
    if (currentSelector != null) {
      List<Preferences> preferencesForSelector = TextMateService.getInstance().getPreferencesRegistry().getPreferences(currentSelector);
      for (Preferences preferences : preferencesForSelector) {
        final Set<TextMateBracePair> smartTypingPairs = preferences.getSmartTypingPairs();
        if (smartTypingPairs != null) {
          // smart typing pairs defined in preferences and can be empty (in order to disable smart typing at all)
          if (smartTypingPairs.isEmpty()) {
            return Collections.emptySet();
          }
          else {
            final HashSet<TextMateBracePair> result = new HashSet<>(smartTypingPairs);
            result.addAll(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS);
            return result;
          }
        }
      }
    }
    return new HashSet<>(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS);
  }

  private TextMateEditorUtils() {
  }

  @NotNull
  public static <K, V> Map<K, V> compactMap(@NotNull Map<K, V> map) {
    if (map.isEmpty()) {
      return Collections.emptyMap();
    }
    if (map.size() == 1) {
      Map.Entry<K, V> singleEntry = map.entrySet().iterator().next();
      return Collections.singletonMap(singleEntry.getKey(), singleEntry.getValue());
    }
    if (!(map instanceof HashMap)) {
      return map;
    }
    HashMap<K, V> result = new HashMap<>(map.size(), 1.0f);
    result.putAll(map);
    return result;
  }

  public static void processExtensions(@NotNull CharSequence fileName, @NotNull Processor<? super CharSequence> processor) {
    if (!processor.process(fileName)) {
      return;
    }
    int index = StringUtil.indexOf(fileName, '.');
    while (index >= 0) {
      CharSequence extension = fileName.subSequence(index + 1, fileName.length());
      if (extension.length() == 0) break;
      if (!processor.process(extension)) {
        return;
      }
      index = StringUtil.indexOf(fileName, '.', index + 1);
    }
  }
}
