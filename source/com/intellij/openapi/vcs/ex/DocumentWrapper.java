package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;

import java.util.ArrayList;

/**
 * author: lesya
 */
public class DocumentWrapper{
  private final Document myDocument;
  public DocumentWrapper(Document document) {
    myDocument = document;
  }

  public int getLineNum(int offset){
    return myDocument.getLineNumber(offset);
  }

  public String[] getLines(){
    return getLines(0, myDocument.getLineCount() - 1);
  }

  public String[] getLines(int from, int to){
    ArrayList<String> result = new ArrayList<String>();
    for (int i = from; i <= to; i++){
      if (i >= myDocument.getLineCount()) break;
      result.add(getLine(i));
    }
    return result.toArray(new String[result.size()]);
  }

  private String getLine(int i) {
    int lineStartOffset = myDocument.getLineStartOffset(i);
    String line = myDocument.getCharsSequence().subSequence(lineStartOffset, myDocument.getLineEndOffset(i)).toString();
    return line;
  }
}

