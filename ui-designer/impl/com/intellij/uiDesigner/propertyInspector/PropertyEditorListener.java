package com.intellij.uiDesigner.propertyInspector;

import java.util.EventListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface PropertyEditorListener extends EventListener{
  /**
   * This method is invoked when user finished editing.
   * For example, user pressed "Enter" in text field or selected
   * somthing from combo box. This doesn't mean that editing
   * is cancelled. PropertyInspector, for example, applies
   * new value and continue editing.
   */
  public void valueCommited(PropertyEditor source);

  /**
   * This method is invoked when user cancelled editing.
   * Foe example, user pressed "Esc" in the text field.
   */
  public void editingCanceled(PropertyEditor source);

  /**
   * Editor can notify listeners that its preferred size changed.
   * In some cases (for example, during inplace editing) it's possible
   * to adjust size of the editor component.
   */
  public void preferredSizeChanged(PropertyEditor source);
}
