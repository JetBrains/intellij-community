package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.ShortBasedStorage;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class TextMateLexerDataStorage extends ShortBasedStorage {
  private final Object2IntMap<TextMateElementType> tokenTypeMap;
  private final List<TextMateElementType> tokenTypes;

  public TextMateLexerDataStorage() {
    this(new Object2IntOpenHashMap<>(), new ArrayList<>());
  }

  private TextMateLexerDataStorage(@NotNull Object2IntMap<TextMateElementType> tokenTypeMap,
                                   @NotNull List<TextMateElementType> tokenTypes) {
    super();
    this.tokenTypeMap = tokenTypeMap;
    this.tokenTypes = tokenTypes;
  }

  private TextMateLexerDataStorage(short @NotNull [] data,
                                   @NotNull Object2IntMap<TextMateElementType> tokenTypeMap,
                                   @NotNull List<TextMateElementType> tokenTypes) {
    super(data);
    this.tokenTypeMap = tokenTypeMap;
    this.tokenTypes = tokenTypes;
  }

  @Override
  public int packData(IElementType tokenType, int state, boolean isRestartableState) {
    if (tokenType instanceof TextMateElementType) {
      synchronized (tokenTypeMap) {
        if (tokenTypeMap.containsKey(tokenType)) {
          return tokenTypeMap.getInt(tokenType) * (isRestartableState ? 1 : -1);
        }
        int data = tokenTypes.size() + 1;
        tokenTypes.add((TextMateElementType)tokenType);
        tokenTypeMap.put((TextMateElementType)tokenType, data);
        return isRestartableState ? data : -data;
      }
    }
    return 0;
  }

  @Override
  public IElementType unpackTokenFromData(int data) {
    return data != 0 ? tokenTypes.get(Math.abs(data) - 1) : new TextMateElementType("empty");
  }

  @Override
  public DataStorage copy() {
    return new TextMateLexerDataStorage(myData, tokenTypeMap, tokenTypes);
  }

  @Override
  public DataStorage createStorage() {
    return new TextMateLexerDataStorage(tokenTypeMap, tokenTypes);
  }
}
