package com.intellij.openapi.editor.impl.event;

import java.util.EventListener;

public interface MarkupModelListener extends EventListener {
  void rangeHighlighterChanged(MarkupModelEvent event);
}
