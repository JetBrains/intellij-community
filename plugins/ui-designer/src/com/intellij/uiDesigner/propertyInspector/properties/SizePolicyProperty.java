// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.SizePolicyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class SizePolicyProperty extends Property<RadComponent, Integer> {
  private final Property[] myChildren;
  private final SizePolicyRenderer myRenderer;

  public SizePolicyProperty(final @NonNls String name){
    super(null, name);
    myChildren=new Property[]{
      new MyBooleanProperty("Can Shrink", GridConstraints.SIZEPOLICY_CAN_SHRINK),
      new MyBooleanProperty("Can Grow", GridConstraints.SIZEPOLICY_CAN_GROW),
      new MyBooleanProperty("Want Grow", GridConstraints.SIZEPOLICY_WANT_GROW)
    };
    myRenderer=new SizePolicyRenderer();
  }

  protected abstract int getValueImpl(GridConstraints constraints);

  protected abstract void setValueImpl(GridConstraints constraints,int policy);

  @Override
  public final Integer getValue(final RadComponent component) {
    return getValueImpl(component.getConstraints());
  }

  @Override
  protected final void setValueImpl(final RadComponent component, final Integer value) throws Exception {
    setValueImpl(component.getConstraints(), value.intValue());
  }

  @Override
  public final Property @NotNull [] getChildren(final RadComponent component){
    return myChildren;
  }

  @Override
  public final @NotNull PropertyRenderer<Integer> getRenderer(){
    return myRenderer;
  }

  @Override
  public final PropertyEditor<Integer> getEditor(){
    return null;
  }

  @Override public boolean isModified(final RadComponent component) {
    final GridConstraints defaultConstraints = FormEditingUtil.getDefaultConstraints(component);
    return getValueImpl(component.getConstraints()) != getValueImpl(defaultConstraints);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValueImpl(component, getValueImpl(FormEditingUtil.getDefaultConstraints(component)));
  }

  /**
   * Subproperty for "can shrink" bit
   */
  private class MyBooleanProperty extends Property<RadComponent, Boolean> {
    private final BooleanRenderer myRenderer;
    private final BooleanEditor myEditor;
    private final int myPropertyMask;

    MyBooleanProperty(final @NonNls String name, final int propertyMask) {
      super(SizePolicyProperty.this, name);
      myPropertyMask = propertyMask;
      myRenderer=new BooleanRenderer();
      myEditor=new BooleanEditor();
    }

    @Override
    public final Boolean getValue(final RadComponent component) {
      final GridConstraints constraints=component.getConstraints();
      return (getValueImpl(constraints) & myPropertyMask) != 0;
    }

    @Override
    protected final void setValueImpl(final RadComponent component, final Boolean value) throws Exception{
      final boolean canShrink=value.booleanValue();
      int newValue=getValueImpl(component.getConstraints());
      if(canShrink){
        newValue|=myPropertyMask;
      }else{
        newValue&=~myPropertyMask;
      }
      SizePolicyProperty.this.setValueImpl(component.getConstraints(),newValue);
    }

    @Override
    public final @NotNull PropertyRenderer<Boolean> getRenderer(){
      return myRenderer;
    }

    @Override
    public final PropertyEditor<Boolean> getEditor(){
      return myEditor;
    }
  }
}
