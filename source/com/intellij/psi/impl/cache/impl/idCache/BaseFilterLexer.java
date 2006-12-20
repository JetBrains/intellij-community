package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharSequenceSubSequence;
import gnu.trove.TIntIntHashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseFilterLexer extends LexerBase {
  protected final Lexer myOriginalLexer;
  protected final TIntIntHashMap myTable;
  protected final int[] myTodoCounts;

  private int myTodoScannedBound = 0;

  protected BaseFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts) {
    myOriginalLexer = originalLexer;
    myTable = table;
    myTodoCounts = todoCounts;
  }

  public void start(char[] buffer) {
    myOriginalLexer.start(buffer);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myOriginalLexer.start(buffer, startOffset, endOffset);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myOriginalLexer.start(buffer, startOffset, endOffset, initialState);
  }

  public CharSequence getBufferSequence() {
    return myOriginalLexer.getBufferSequence();
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myOriginalLexer.start(buffer, startOffset, endOffset, initialState);
  }

  public int getState() {
    return myOriginalLexer.getState();
  }

  public IElementType getTokenType() {
    return myOriginalLexer.getTokenType();
  }

  public int getTokenStart() {
    return myOriginalLexer.getTokenStart();
  }

  public int getTokenEnd() {
    return myOriginalLexer.getTokenEnd();
  }

  public char[] getBuffer() {
    return myOriginalLexer.getBuffer();
  }

  public int getBufferEnd() {
    return myOriginalLexer.getBufferEnd();
  }

  protected final void advanceTodoItemCounts(char[] chars, int start, int end) {
    advanceTodoItemCounts(new CharArrayCharSequence(chars),start, end);
  }

  protected final void advanceTodoItemCounts(CharSequence chars, int start, int end) {
    if (myTodoCounts != null){
      start = Math.max(start, myTodoScannedBound);
      if (start >= end) return; // this prevents scanning of the same comment twice

      CharSequence input = new CharSequenceSubSequence(chars, start, end);
      advanceTodoItemsCount(input, myTodoCounts);

      myTodoScannedBound = end;
    }
  }

  public static void advanceTodoItemsCount(final CharSequence input, final int[] todoCounts) {
    IndexPattern[] patterns = IdCacheUtil.getIndexPatterns();
    for(int index = 0; index < patterns.length; index++){
      Pattern pattern = patterns[index].getPattern();
      if (pattern != null){
        Matcher matcher = pattern.matcher(input);
        while(matcher.find()){
          if (matcher.start() != matcher.end()){
            todoCounts[index]++;
          }
        }
      }
    }
  }

}
