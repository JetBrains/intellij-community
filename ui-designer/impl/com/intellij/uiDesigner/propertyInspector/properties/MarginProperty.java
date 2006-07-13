package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * This is "synthetic" property of the RadContainer.
 * It represets margins of GridLayoutManager. <b>Note, that
 * this property exists only in RadContainer</b>.
 *
 * @see com.intellij.uiDesigner.core.GridLayoutManager#getMargin
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MarginProperty extends AbstractInsetsProperty<RadContainer> {
  private static final Insets DEFAULT_INSETS = new Insets(0, 0, 0, 0);

  public static MarginProperty getInstance(Project project) {
    return project.getComponent(MarginProperty.class);
  }

  public MarginProperty(){
    super("margins");
  }

  public Insets getValue(final RadContainer component) {
    if (component.getLayout() instanceof AbstractLayout) {
      final AbstractLayout layoutManager=(AbstractLayout) component.getLayout();
      return layoutManager.getMargin();
    }
    return DEFAULT_INSETS;
  }

  protected void setValueImpl(final RadContainer component, @NotNull final Insets value) throws Exception{
    final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
    layoutManager.setMargin(value);
  }

  @Override public boolean isModified(final RadContainer component) {
    return !getValue(component).equals(DEFAULT_INSETS);
  }

  @Override public void resetValue(RadContainer component) throws Exception {
    setValueImpl(component, DEFAULT_INSETS);
  }
}
