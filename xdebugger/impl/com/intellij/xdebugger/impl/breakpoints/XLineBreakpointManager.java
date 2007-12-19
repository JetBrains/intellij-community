package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import java.util.Collection;

/**
 * @author nik
 */
public class XLineBreakpointManager {
  private MultiValuesMap<Document, XLineBreakpointImpl> myBreakpoints = new MultiValuesMap<Document, XLineBreakpointImpl>();
  private MergingUpdateQueue myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null);
  private DocumentAdapter myDocumentListener = new DocumentAdapter() {
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.get(document);
      if (breakpoints != null && !breakpoints.isEmpty()) {
        myBreakpointsUpdateQueue.queue(new Update("document:" + document) {
          public void run() {
            updateBreakpoints(document);
          }
        });
      }
    }
  };

  public XLineBreakpointManager() {
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener);
  }

  public void registerBreakpoint(XLineBreakpointImpl breakpoint) {
    RangeHighlighter highlighter = breakpoint.getHighlighter();
    if (highlighter != null && highlighter.isValid()) {
      myBreakpoints.put(highlighter.getDocument(), breakpoint);
    }
  }

  public void unregisterBreakpoint(final XLineBreakpointImpl breakpoint) {
    RangeHighlighter highlighter = breakpoint.getHighlighter();
    if (highlighter != null) {
      myBreakpoints.remove(highlighter.getDocument(), breakpoint);
    }
  }

  private void updateBreakpoints(final Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.get(document);
    if (breakpoints == null) return;
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      breakpoint.updatePosition();
    }
  }

  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentListener);
    myBreakpointsUpdateQueue.dispose();
  }
}
