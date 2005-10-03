package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.InsetsPropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;

import java.awt.*;

import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class AbstractInsetsProperty extends Property{
  private final Property[] myChildren;
  private final InsetsPropertyRenderer myRenderer;

  public AbstractInsetsProperty(@NonNls final String name){
    super(null, name);
    myChildren=new Property[]{
      new MyTopProperty(this),
      new MyLeftProperty(this),
      new MyBottomProperty(this),
      new MyRightProperty(this)
    };
    myRenderer=new InsetsPropertyRenderer();
  }

  public final Property[] getChildren(){
    return myChildren;
  }

  public final PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public final PropertyEditor getEditor(){
    return null;
  }

  /**
   * Insets.top
   */
  static final class MyTopProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyTopProperty(final Property parent){
      super(parent, "top");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(0);
    }

    public Object getValue(final RadComponent component){
      final Insets insets=(Insets)getParent().getValue(component);
      return new Integer(insets.top);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Insets insets=(Insets)getParent().getValue(component);
      final int top = ((Integer)value).intValue();
      getParent().setValue(component,new Insets(top, insets.left, insets.bottom, insets.right));
    }

    public Property[] getChildren(){
      return Property.EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Insets.left
   */
  static final class MyLeftProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyLeftProperty(final Property parent){
      super(parent, "left");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(0);
    }

    public Object getValue(final RadComponent component){
      final Insets insets=(Insets)getParent().getValue(component);
      return new Integer(insets.left);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Insets insets=(Insets)getParent().getValue(component);
      final int left=((Integer)value).intValue();
      getParent().setValue(component,new Insets(insets.top, left, insets.bottom, insets.right));
    }

    public Property[] getChildren(){
      return Property.EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Insets.bottom
   */
  static final class MyBottomProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyBottomProperty(final Property parent){
      super(parent, "bottom");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(0);
    }

    public Object getValue(final RadComponent component){
      final Insets insets=(Insets)getParent().getValue(component);
      return new Integer(insets.bottom);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Insets insets=(Insets)getParent().getValue(component);
      final int bottom=((Integer)value).intValue();
      getParent().setValue(component,new Insets(insets.top, insets.left, bottom, insets.right));
    }

    public Property[] getChildren(){
      return Property.EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Insets.right
   */
  static final class MyRightProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyRightProperty(final Property parent){
      super(parent, "right");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(0);
    }

    public Object getValue(final RadComponent component){
      final Insets insets=(Insets)getParent().getValue(component);
      return new Integer(insets.right);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Insets insets=(Insets)getParent().getValue(component);
      final int right=((Integer)value).intValue();
      getParent().setValue(component,new Insets(insets.top, insets.left, insets.bottom, right));
    }

    public Property[] getChildren(){
      return Property.EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }
}
