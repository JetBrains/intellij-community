package com.intellij.uiDesigner.propertyInspector;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class PropertyEditorAdapter implements PropertyEditorListener{
  public void valueCommited(final PropertyEditor source) {}

  public void editingCanceled(final PropertyEditor source) {}

  public void preferredSizeChanged(final PropertyEditor source) {}
}
