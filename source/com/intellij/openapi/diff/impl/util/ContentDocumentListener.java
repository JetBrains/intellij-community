package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.Disposeable;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.DiffVersionComponent;

public class ContentDocumentListener implements DiffContent.Listener {
  private final DiffVersionComponent myDiffComponent;

  private ContentDocumentListener(DiffVersionComponent diffComponent) {
    myDiffComponent = diffComponent;
  }

  public static void install(final DiffContent content, DiffVersionComponent component) {
    final ContentDocumentListener listener = new ContentDocumentListener(component);
    content.onAssigned(true);
    content.addListener(listener);
    Disposeable disposeable = new Disposeable() {
        public void dispose() {
          content.onAssigned(false);
        }
      };
    component.addDisposable(disposeable);
  }

  public void contentInvalid() {
    myDiffComponent.removeContent();
  }
}
