package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.SizePolicyRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class SizePolicyProperty extends Property{
  private final Property[] myChildren;
  private final SizePolicyRenderer myRenderer;

  public SizePolicyProperty(@NonNls final String name){
    super(null, name);
    myChildren=new Property[]{
      new MyCanShrinkProperty(),
      new MyCanGrowProperty(),
      new MyWantGrowProperty()
    };
    myRenderer=new SizePolicyRenderer();
  }

  protected abstract int getValueImpl(GridConstraints constraints);

  protected abstract void setValueImpl(GridConstraints constraints,int policy);

  public final Object getValue(final RadComponent component){
    return new Integer(getValueImpl(component.getConstraints()));
  }

  protected final void setValueImpl(final RadComponent component,final Object value) throws Exception{
    final int policy=((Integer)value).intValue();
    setValueImpl(component.getConstraints(),policy);
  }

  @NotNull public final Property[] getChildren(){
    return myChildren;
  }

  @NotNull public final PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public final PropertyEditor getEditor(){
    return null;
  }

  @Override public boolean isModified(final RadComponent component) {
    final GridConstraints defaultConstraints = FormEditingUtil.getDefaultConstraints(component);
    return getValueImpl(component.getConstraints()) != getValueImpl(defaultConstraints);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValueImpl(component, new Integer(getValueImpl(FormEditingUtil.getDefaultConstraints(component))));
  }

  /**
   * Subproperty for "can shrink" bit
   */
  private abstract class MyBooleanProperty extends Property{
    private final BooleanRenderer myRenderer;
    private final BooleanEditor myEditor;

    public MyBooleanProperty(@NonNls final String name){
      super(SizePolicyProperty.this, name);
      myRenderer=new BooleanRenderer();
      myEditor=new BooleanEditor();
    }

    public final Object getValue(final RadComponent component){
      final GridConstraints constraints=component.getConstraints();
      return Boolean.valueOf((getValueImpl(constraints)&getPropertyMask()) != 0);
    }

    protected abstract int getPropertyMask();

    protected final void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final boolean canShrink=((Boolean)value).booleanValue();
      int newValue=getValueImpl(component.getConstraints());
      if(canShrink){
        newValue|=getPropertyMask();
      }else{
        newValue&=~getPropertyMask();
      }
      SizePolicyProperty.this.setValueImpl(component.getConstraints(),newValue);
    }

    @NotNull
    public final PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public final PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Subproperty for "can shrink" bit
   */
  private final class MyCanShrinkProperty extends MyBooleanProperty{
    public MyCanShrinkProperty(){
      super("Can Shrink");
    }

    protected int getPropertyMask(){
      return GridConstraints.SIZEPOLICY_CAN_SHRINK;
    }
  }

  /**
   * Subproperty for "can grow" bit
   */
  private final class MyCanGrowProperty extends MyBooleanProperty{
    public MyCanGrowProperty(){
      super("Can Grow");
    }

    protected int getPropertyMask(){
      return GridConstraints.SIZEPOLICY_CAN_GROW;
    }
  }

  /**
   * Subproperty for "want grow" bit
   */
  private final class MyWantGrowProperty extends MyBooleanProperty{
    public MyWantGrowProperty(){
      super("Want Grow");
    }

    protected int getPropertyMask(){
      return GridConstraints.SIZEPOLICY_WANT_GROW;
    }
  }
}
