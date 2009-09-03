package com.intellij.uiDesigner.propertyInspector;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class PropertyEditorAdapter implements PropertyEditorListener{
  public void valueCommitted(final PropertyEditor source, final boolean continueEditing, final boolean closeEditorOnError) {}

  public void editingCanceled(final PropertyEditor source) {}

  public void preferredSizeChanged(final PropertyEditor source) {}
}
