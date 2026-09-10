package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.IntArrayDataStorage;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores one int per segment: the interned {@link TextMateElementType} in the low 16 bits and the lexer
 * state in the high 16 bits.
 * <p>
 * {@link com.intellij.openapi.editor.ex.util.IntBasedStorage} has the same layout, and the array itself is
 * shared through {@link IntArrayDataStorage}, but the packing cannot be shared. The platform storage packs
 * {@link IElementType#getIndex()}, which is -1 for the unregistered {@link TextMateElementType}. The state
 * must be stored, because {@link TextMateHighlightingLexer} is a {@link com.intellij.lexer.RestartableLexer}:
 * the state names the rule stack to restart the line with.
 * <p>
 * The storage needs a {@link com.intellij.lexer.RestartableLexer}. It gives all 16 state bits to the state,
 * so the sign of the packed data says nothing about restartability.
 * {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter} reads that sign for a plain lexer, and
 * {@code TextMateEditorHighlighterProvider} therefore leaves the default storage to a plain lexer.
 */
public final class TextMateLexerDataStorage extends IntArrayDataStorage {
  private static final int MASK = 0xFFFF;
  /**
   * The count of distinct token types one storage can hold. Id 0 means "unknown", so the last usable id
   * is {@code MASK}. A file that needs more loses the color of the extra scopes, but it still lexes.
   */
  private static final int MAX_TOKEN_TYPES = MASK;

  private final Object2IntMap<TextMateElementType> tokenTypeMap;
  private final List<TextMateElementType> tokenTypes;

  public TextMateLexerDataStorage() {
    this(new Object2IntOpenHashMap<>(), new ArrayList<>());
  }

  private TextMateLexerDataStorage(@NotNull Object2IntMap<TextMateElementType> tokenTypeMap,
                                   @NotNull List<TextMateElementType> tokenTypes) {
    this.tokenTypeMap = tokenTypeMap;
    this.tokenTypes = tokenTypes;
  }

  private TextMateLexerDataStorage(int @NotNull [] data,
                                   @NotNull Object2IntMap<TextMateElementType> tokenTypeMap,
                                   @NotNull List<TextMateElementType> tokenTypes) {
    super(data);
    this.tokenTypeMap = tokenTypeMap;
    this.tokenTypes = tokenTypes;
  }

  @Override
  public int packData(@NotNull IElementType tokenType, int state, boolean isRestartableState) {
    return ((state & MASK) << 16) | (packTokenType(tokenType) & MASK);
  }

  @Override
  public int unpackStateFromData(int data) {
    return (data >>> 16) & MASK;
  }

  @Override
  public @NotNull IElementType unpackTokenFromData(int data) {
    int tokenTypeId = data & MASK;
    return tokenTypeId != 0 ? tokenTypes.get(tokenTypeId - 1) : new TextMateElementType(TextMateScope.EMPTY);
  }

  @Override
  public @NotNull DataStorage copy() {
    return new TextMateLexerDataStorage(myData, tokenTypeMap, tokenTypes);
  }

  @Override
  public @NotNull DataStorage createStorage() {
    return new TextMateLexerDataStorage(tokenTypeMap, tokenTypes);
  }

  private int packTokenType(@NotNull IElementType tokenType) {
    if (!(tokenType instanceof TextMateElementType textMateElementType)) {
      return 0;
    }
    synchronized (tokenTypeMap) {
      int existing = tokenTypeMap.getInt(textMateElementType);
      if (existing != 0) {
        return existing;
      }
      if (tokenTypes.size() >= MAX_TOKEN_TYPES) {
        return 0;
      }
      int tokenTypeId = tokenTypes.size() + 1;
      tokenTypes.add(textMateElementType);
      tokenTypeMap.put(textMateElementType, tokenTypeId);
      return tokenTypeId;
    }
  }
}
