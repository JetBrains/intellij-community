package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lexer.Lexer;
import com.intellij.psi.search.TodoPattern;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.TIntIntHashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseFilterLexer implements Lexer {
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

  public int getState() {
    return myOriginalLexer.getState();
  }

  public int getLastState() {
    return myOriginalLexer.getLastState();
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

  public int getSmartUpdateShift() {
    return myOriginalLexer.getSmartUpdateShift();
  }

  protected final void advanceTodoItemCounts(char[] chars, int start, int end) {
    if (myTodoCounts != null){
      start = Math.max(start, myTodoScannedBound);
      if (start >= end) return; // this prevents scanning of the same comment twice

      TodoPattern[] patterns = TodoConfiguration.getInstance().getTodoPatterns();
      for(int index = 0; index < patterns.length; index++){
        Pattern pattern = patterns[index].getPattern();
        if (pattern != null){
          CharSequence input = new CharArrayCharSequence(chars, start, end);
          Matcher matcher = pattern.matcher(input);
          while(matcher.find()){
            if (matcher.start() != matcher.end()){
              myTodoCounts[index]++;
            }
          }
        }
      }

      myTodoScannedBound = end;
    }
  }

  public abstract Object clone();

}
