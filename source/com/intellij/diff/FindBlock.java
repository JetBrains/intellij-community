package com.intellij.diff;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.diff.Diff;

/**
 * author: lesya
 */
public class FindBlock {

  private final Block myCurrentVersion;
  private final Block myResult;

  public FindBlock(String[] prevVersion, Block currentVersion) {
    myResult = new Block(prevVersion, currentVersion.getStart(), currentVersion.getEnd());
    myCurrentVersion = currentVersion;
  }

  public FindBlock(String prevVersion, Block currentVersion) {
    this(LineTokenizer.tokenize(prevVersion.toCharArray(), false), currentVersion);
  }


  public Block getBlockInThePrevVersion() {

    Diff.Change change = Diff.buildChanges(myResult.getSource(), myCurrentVersion.getSource());
    while (change != null) {
      shiftIndices(change.line1, change.line1, change.line0);
      shiftIndices(change.line1, change.line1 + change.inserted, change.line0 + change.deleted);
      change = change.link;
    }

    if (myResult.getEnd() >= myResult.getSource().length){
      myResult.setEnd(myResult.getSource().length - 1);
    }

    return myResult;
  }

  private void shiftIndices(int firstChangeIndex,int line1, int line0) {
    int shift = line1 - line0;

    if (line1 <= myCurrentVersion.getStart()) {
      myResult.setStart(myCurrentVersion.getStart() - shift);
    }

    if (firstChangeIndex <= myCurrentVersion.getEnd()) {
      myResult.setEnd(myCurrentVersion.getEnd() - shift);
    }
  }
}
