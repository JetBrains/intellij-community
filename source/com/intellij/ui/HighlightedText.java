package com.intellij.ui;

import com.intellij.openapi.editor.markup.TextAttributes;

import java.util.ArrayList;

public class HighlightedText {
  private final StringBuffer myBuffer;
  private final ArrayList myHighlightedRegions;

  public HighlightedText() {
    myBuffer = new StringBuffer();
    myHighlightedRegions = new ArrayList(3);
  }

  public void appendText(String text, TextAttributes attributes) {
    int startOffset = myBuffer.length();
    myBuffer.append(text);
    if (attributes != null) {
      myHighlightedRegions.add(new HighlightedRegion(startOffset, myBuffer.length(), attributes));
    }
  }

  public void insertTextAtStart(String text, TextAttributes attributes) {
    int textLength = text.length();
    for (int i=0; i < myHighlightedRegions.size(); i++) {
      HighlightedRegion info = (HighlightedRegion)myHighlightedRegions.get(i);
      info.startOffset += textLength;
      info.endOffset += textLength;
    }
    myBuffer.insert(0, text);
    if (attributes != null) {
      myHighlightedRegions.add(new HighlightedRegion(0, textLength, attributes));
    }
  }

  public boolean equals(Object o) {
    if (!(o instanceof HighlightedText)) return false;

    HighlightedText highlightedText = (HighlightedText)o;

    if (!myBuffer.equals(highlightedText.myBuffer)) return false;
    if (!myHighlightedRegions.equals(highlightedText.myHighlightedRegions)) return false;

    return true;
  }

  public String getText() {
    return myBuffer.toString();
  }

  public void applyToComponent(HighlightableComponent renderer) {
    renderer.setText(myBuffer.toString());
    for (int i=0; i < myHighlightedRegions.size(); i++) {
      HighlightedRegion info = (HighlightedRegion)myHighlightedRegions.get(i);
      renderer.addHighlighter(info.startOffset, info.endOffset, info.textAttributes);
    }
  }

}
