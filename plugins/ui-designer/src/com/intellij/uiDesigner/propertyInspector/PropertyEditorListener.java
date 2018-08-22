// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector;

import java.util.EventListener;

public interface PropertyEditorListener extends EventListener {
  /**
   * This method is invoked when user finished editing.
   * For example, user pressed "Enter" in text field or selected
   * somthing from combo box. This doesn't mean that editing
   * is cancelled. PropertyInspector, for example, applies
   * new value and continue editing.
   */
  default void valueCommitted(PropertyEditor source, final boolean continueEditing, final boolean closeEditorOnError) {
  }

  /**
   * This method is invoked when user cancelled editing.
   * Foe example, user pressed "Esc" in the text field.
   */
  default void editingCanceled(PropertyEditor source) {
  }

  /**
   * Editor can notify listeners that its preferred size changed.
   * In some cases (for example, during inplace editing) it's possible
   * to adjust size of the editor component.
   */
  default void preferredSizeChanged(PropertyEditor source) {
  }
}
