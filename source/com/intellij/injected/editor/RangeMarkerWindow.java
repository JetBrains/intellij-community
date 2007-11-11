/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 22, 2007
 * Time: 9:09:57 PM
 */
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class RangeMarkerWindow implements RangeMarkerEx {
  private final DocumentWindowImpl myDocumentWindow;
  private final RangeMarkerEx myHostMarker;

  public RangeMarkerWindow(@NotNull DocumentWindowImpl documentWindow, RangeMarkerEx hostMarker) {
    myDocumentWindow = documentWindow;
    myHostMarker = hostMarker;
  }

  @NotNull
  public Document getDocument() {
    return myDocumentWindow;
  }

  public int getStartOffset() {
    int hostOffset = myHostMarker.getStartOffset();
    return myDocumentWindow.hostToInjected(hostOffset);
  }

  public int getEndOffset() {
    int hostOffset = myHostMarker.getEndOffset();
    return myDocumentWindow.hostToInjected(hostOffset);
  }

  ////////////////////////////delegates
  public boolean isValid() {
    return myHostMarker.isValid();
  }

  public void setGreedyToLeft(final boolean greedy) {
    myHostMarker.setGreedyToLeft(greedy);
  }

  public void setGreedyToRight(final boolean greedy) {
    myHostMarker.setGreedyToRight(greedy);
  }

  public <T> T getUserData(final Key<T> key) {
    return myHostMarker.getUserData(key);
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    myHostMarker.putUserData(key, value);
  }

  public void documentChanged(final DocumentEvent e) {
    myHostMarker.documentChanged(e);
  }
}