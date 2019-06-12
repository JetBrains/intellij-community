package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.ShortBasedStorage;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TextMateLexerDataStorage extends ShortBasedStorage {
  private final TObjectIntHashMap<TextMateElementType> tokenTypesMap;
  private final List<TextMateElementType> tokenTypes;

  public TextMateLexerDataStorage() {
    this(new TObjectIntHashMap<>(), new SmartList<>());
  }

  private TextMateLexerDataStorage(@NotNull TObjectIntHashMap<TextMateElementType> tokenTypesMap,
                                   @NotNull List<TextMateElementType> tokenTypes) {
    super();
    this.tokenTypesMap = tokenTypesMap;
    this.tokenTypes = tokenTypes;
  }

  private TextMateLexerDataStorage(@NotNull short[] data,
                                   @NotNull TObjectIntHashMap<TextMateElementType> tokenTypesMap,
                                   @NotNull List<TextMateElementType> tokenTypes) {
    super(data);
    this.tokenTypesMap = tokenTypesMap;
    this.tokenTypes = tokenTypes;
  }

  @Override
  public int packData(IElementType tokenType, int state, boolean isRestartableState) {
    if (tokenType instanceof TextMateElementType) {
      synchronized (tokenTypesMap) {
        if (tokenTypesMap.contains(tokenType)) {
          return tokenTypesMap.get((TextMateElementType)tokenType);
        }
        int data = tokenTypes.size() + 1;
        tokenTypes.add((TextMateElementType)tokenType);
        tokenTypesMap.put((TextMateElementType)tokenType, data);
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
    return new TextMateLexerDataStorage(myData, tokenTypesMap, tokenTypes);
  }

  @Override
  public DataStorage createStorage() {
    return new TextMateLexerDataStorage(tokenTypesMap, tokenTypes);
  }
}
