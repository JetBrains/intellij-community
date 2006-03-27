package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.RectangleRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroRectangleProperty extends IntrospectedProperty<Rectangle> {
  private final RectangleRenderer myRenderer;
  private final Property[] myChildren;

  public IntroRectangleProperty(final String name, final Method readMethod, final Method writeMethod){
    super(name, readMethod, writeMethod);
    myRenderer=new RectangleRenderer();
    myChildren=new Property[]{
      new MyXProperty(),
      new MyYProperty(),
      new MyWidthProperty(),
      new MyHeightProperty()
    };
  }

  public void write(final Rectangle value,final XmlWriter writer){
    writer.addAttribute("x",value.x);
    writer.addAttribute("y",value.y);
    writer.addAttribute("width",value.width);
    writer.addAttribute("height",value.height);
  }

  @NotNull
  public Property[] getChildren(){
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<Rectangle> getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return null;
  }

  /**
   * X subproperty
   */
  private final class MyXProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyXProperty(){
      super(IntroRectangleProperty.this, "x");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(Integer.MIN_VALUE);
    }

    public Object getValue(final RadComponent component){
      final Rectangle r=(Rectangle)getParent().getValue(component);
      return new Integer(r.x);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Rectangle r=(Rectangle)getParent().getValue(component);
      r.x=((Integer)value).intValue();
      getParent().setValue(component,r);
    }

    @NotNull
    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Y subproperty
   */
  private final class MyYProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyYProperty(){
      super(IntroRectangleProperty.this, "y");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(Integer.MIN_VALUE);
    }

    public Object getValue(final RadComponent component){
      final Rectangle r=(Rectangle)getParent().getValue(component);
      return new Integer(r.y);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Rectangle r=(Rectangle)getParent().getValue(component);
      r.y=((Integer)value).intValue();
      getParent().setValue(component,r);
    }

    @NotNull
    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * WIDTH subproperty
   */
  private final class MyWidthProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyWidthProperty(){
      super(IntroRectangleProperty.this, "width");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(0);
    }

    public Object getValue(final RadComponent component){
      final Rectangle r=(Rectangle)getParent().getValue(component);
      return new Integer(r.width);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Rectangle r=(Rectangle)getParent().getValue(component);
      r.width=((Integer)value).intValue();
      getParent().setValue(component,r);
    }

    @NotNull
    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * HEIGHT subproperty
   */
  private final class MyHeightProperty extends Property{
    private final IntRenderer myRenderer;
    private final IntEditor myEditor;

    public MyHeightProperty(){
      super(IntroRectangleProperty.this, "height");
      myRenderer = new IntRenderer();
      myEditor = new IntEditor(0);
    }

    public Object getValue(final RadComponent component){
      final Rectangle r=(Rectangle)getParent().getValue(component);
      return new Integer(r.height);
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final Rectangle r=(Rectangle)getParent().getValue(component);
      r.height=((Integer)value).intValue();
      getParent().setValue(component,r);
    }

    @NotNull
    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }
}
