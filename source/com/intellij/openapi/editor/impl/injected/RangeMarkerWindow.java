/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 22, 2007
 * Time: 9:09:57 PM
 */
package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import org.jetbrains.annotations.NotNull;

public class RangeMarkerWindow extends RangeMarkerImpl {
  private final DocumentWindow myDocumentWindow;

  public RangeMarkerWindow(DocumentWindow documentWindow, RangeMarker hostMarker) {
    super(hostMarker.getDocument(), hostMarker.getStartOffset(), hostMarker.getEndOffset());
    myDocumentWindow = documentWindow;
  }

  @NotNull
  public Document getDocument() {
    return myDocumentWindow;
  }

  public int getStartOffset() {
    int hostOffset = super.getStartOffset();
    return myDocumentWindow.hostToInjected(hostOffset);
  }

  public int getEndOffset() {
    int hostOffset = super.getEndOffset();
    return myDocumentWindow.hostToInjected(hostOffset);
  }
}