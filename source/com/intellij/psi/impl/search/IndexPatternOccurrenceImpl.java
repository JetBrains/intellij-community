package com.intellij.psi.impl.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
class IndexPatternOccurrenceImpl implements IndexPatternOccurrence {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final IndexPattern myPattern;

  public IndexPatternOccurrenceImpl(PsiFile file, int startOffset, int endOffset, IndexPattern pattern) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPattern = pattern;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myEndOffset);
  }

  @NotNull
  public IndexPattern getPattern() {
    return myPattern;
  }

  public int hashCode(){
    return myFile.hashCode()+myStartOffset+myEndOffset+myPattern.hashCode();
  }

  public boolean equals(Object obj){
    if(!(obj instanceof IndexPatternOccurrenceImpl)){
      return false;
    }
    IndexPatternOccurrenceImpl todoItem=(IndexPatternOccurrenceImpl)obj;
    if(
      !myFile.equals(todoItem.myFile)||
      myStartOffset!=todoItem.myStartOffset||
      myEndOffset!=todoItem.myEndOffset||
      !myPattern.equals(todoItem.myPattern)
    ){
      return false;
    }
    return true;
  }
}
