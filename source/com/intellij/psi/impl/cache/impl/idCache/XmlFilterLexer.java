package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import gnu.trove.TIntIntHashMap;

public class XmlFilterLexer extends BaseFilterLexer implements IdTableBuilding.ScanWordProcessor {
  private boolean caseInsensitive;

  public void run(char[] chars, int start, int end) {
    IdTableBuilding.registerOccurence(chars, start, end, myTable, WordInfo.PLAIN_TEXT, caseInsensitive);
  }

  public XmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts) {
    this(originalLexer, table, todoCounts,false);
  }

  public XmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts, boolean _caseInsensitive) {
    super(originalLexer, table, todoCounts);
    caseInsensitive = _caseInsensitive;
  }

  public Object clone() {
    return new XmlFilterLexer((Lexer)myOriginalLexer.clone(), myTable, myTodoCounts, caseInsensitive);
  }

  public void advance() {
    IElementType tokenType = myOriginalLexer.getTokenType();
    if (tokenType == ElementType.XML_COMMENT_CHARACTERS) {
      advanceTodoItemCounts(getBuffer(), getTokenStart(), getTokenEnd());
    }

    IdTableBuilding.scanWords(this, getBuffer(), getTokenStart(), getTokenEnd());
    myOriginalLexer.advance();
  }
}
