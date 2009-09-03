package com.intellij.uiDesigner.propertyInspector;

import java.util.EventListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface PropertyEditorListener extends EventListener {
  /**
   * This method is invoked when user finished editing.
   * For example, user pressed "Enter" in text field or selected
   * somthing from combo box. This doesn't mean that editing
   * is cancelled. PropertyInspector, for example, applies
   * new value and continue editing.
   */
  void valueCommitted(PropertyEditor source, final boolean continueEditing, final boolean closeEditorOnError);

  /**
   * This method is invoked when user cancelled editing.
   * Foe example, user pressed "Esc" in the text field.
   */
  void editingCanceled(PropertyEditor source);

  /**
   * Editor can notify listeners that its preferred size changed.
   * In some cases (for example, during inplace editing) it's possible
   * to adjust size of the editor component.
   */
  void preferredSizeChanged(PropertyEditor source);
}
