package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.util.diff.Diff;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */

public class RangesBuilder {
  private List<Range> myRanges;

  public RangesBuilder(String current, String upToDate) {
    this(EditorFactory.getInstance().createDocument(current), EditorFactory.getInstance().createDocument(upToDate));
  }

  public RangesBuilder(Document current, Document upToDate) {
    this(new DocumentWrapper(current).getLines(),
         new DocumentWrapper(upToDate).getLines(),
         0, 0);
  }

  public RangesBuilder(String[] current, String[] upToDate, int shift, int uShift) {
    myRanges = new ArrayList<Range>();

    //Diff diff = new Diff(upToDate, current);
    //Diff.change ch = diff.diff_2(false);
    Diff.Change ch = Diff.buildChanges(upToDate, current);



    while (ch != null) {
      Range range = Range.createOn(ch, shift, uShift);
      myRanges.add(range);
      ch = ch.link;
    }

  }

  public List<Range> getRanges() {
    return myRanges;
  }

}
