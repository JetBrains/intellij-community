package org.jetbrains.plugins.textmate.editor;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.preferences.Preferences;
import org.jetbrains.plugins.textmate.language.preferences.TextMateAutoClosingPair;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TextMateEditorUtils {
  public static @Nullable TextMateScope getCurrentScopeSelector(@NotNull EditorEx editor) {
    TextMateScope result = getCurrentScopeFromEditor(editor);
    //retrieve the root scope of a file
    if (result == null) {
      final VirtualFile file = editor.getVirtualFile();
      if (file != null) {
        final TextMateLanguageDescriptor languageDescriptor = TextMateService.getInstance().getLanguageDescriptorByFileName(file.getName());
        if (languageDescriptor != null) {
          return new TextMateScope(languageDescriptor.getRootScopeName(), null);
        }
      }
    }
    return result;
  }

  private static @Nullable TextMateScope getCurrentScopeFromEditor(@NotNull EditorEx editor) {
    final EditorHighlighter highlighter = editor.getHighlighter();
    SelectionModel selection = editor.getSelectionModel();
    final int offset = selection.hasSelection() ? selection.getSelectionStart() : editor.getCaretModel().getOffset();
    final HighlighterIterator iterator = highlighter.createIterator(offset);
    TextMateScope result = null;
    if (offset != 0 || !iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();
      result = tokenType instanceof TextMateElementType ? ((TextMateElementType)tokenType).getScope() : null;
    }
    return result;
  }

  public static @Nullable TextMateBracePair findRightHighlightingPair(int leftBraceStartOffset,
                                                                      @NotNull CharSequence fileText,
                                                                      @Nullable TextMateScope currentScope) {
    if (!TextMateService.getInstance().getPreferenceRegistry().isPossibleLeftHighlightingBrace(fileText.charAt(leftBraceStartOffset))) {
      return null;
    }
    Set<TextMateBracePair> pairs = getAllPairsForMatcher(currentScope);
    for (TextMateBracePair pair : pairs) {
      int endOffset = leftBraceStartOffset + pair.getLeft().length();
      if (endOffset < fileText.length() && StringUtil.equals(pair.getLeft(), fileText.subSequence(leftBraceStartOffset, endOffset))) {
        return pair;
      }
    }
    return null;
  }

  public static @Nullable TextMateBracePair findLeftHighlightingPair(int rightBraceEndOffset,
                                                                     @NotNull CharSequence fileText,
                                                                     @Nullable TextMateScope currentSelector) {
    if (!TextMateService.getInstance().getPreferenceRegistry().isPossibleRightHighlightingBrace(fileText.charAt(rightBraceEndOffset - 1))) {
      return null;
    }
    Set<TextMateBracePair> pairs = getAllPairsForMatcher(currentSelector);
    for (TextMateBracePair pair : pairs) {
      int startOffset = rightBraceEndOffset - pair.getRight().length();
      if (startOffset >= 0 && StringUtil.equals(pair.getRight(), fileText.subSequence(startOffset, rightBraceEndOffset))) {
        return pair;
      }
    }
    return null;
  }

  private static Set<TextMateBracePair> getAllPairsForMatcher(@Nullable TextMateScope selector) {
    if (selector == null) {
      return Constants.Companion.getDEFAULT_HIGHLIGHTING_BRACE_PAIRS();
    }
    Set<TextMateBracePair> result = new HashSet<>();
    List<Preferences> preferencesForSelector = TextMateService.getInstance().getPreferenceRegistry().getPreferences(selector);
    for (Preferences preferences : preferencesForSelector) {
      final Set<TextMateBracePair> highlightingPairs = preferences.getHighlightingPairs();
      if (highlightingPairs != null) {
        if (highlightingPairs.isEmpty()) {
          // smart typing pairs can be defined in preferences but can be empty (in order to disable smart typing completely)
          return Collections.emptySet();
        }
        result.addAll(highlightingPairs);
      }
    }
    return result;
  }

  public static Set<TextMateAutoClosingPair> getSmartTypingPairs(@Nullable TextMateScope currentScope) {
    if (currentScope == null) {
      return Constants.Companion.getDEFAULT_SMART_TYPING_BRACE_PAIRS();
    }
    List<Preferences> preferencesForSelector = TextMateService.getInstance().getPreferenceRegistry().getPreferences(currentScope);
    final HashSet<TextMateAutoClosingPair> result = new HashSet<>();
    for (Preferences preferences : preferencesForSelector) {
      final @Nullable Set<TextMateAutoClosingPair> smartTypingPairs = preferences.getSmartTypingPairs();
      if (smartTypingPairs != null) {
        if (smartTypingPairs.isEmpty()) {
          // smart typing pairs defined in preferences and can be empty (in order to disable smart typing completely)
          return Collections.emptySet();
        }
        result.addAll(smartTypingPairs);
      }
    }
    return result;
  }

  private TextMateEditorUtils() {
  }
}
