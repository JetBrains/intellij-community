package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.ResourceBundleLoader;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BorderTypeEditor;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BorderTypeRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import com.intellij.uiDesigner.shared.BorderType;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BorderProperty extends Property{
  private final Property[] myChildren;
  private final BorderTypeRenderer myRenderer;

  public BorderProperty(){
    super(null, "border");
    myChildren=new Property[]{
      new MyTypeProperty(),
      new MyTitleProperty()
    };
    myRenderer=new BorderTypeRenderer();
  }

  public Object getValue(final RadComponent component){
    if(!(component instanceof RadContainer)){
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    return container.getBorderType();
  }

  protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
    if(!(component instanceof RadContainer)){
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
  }

  public Property[] getChildren(){
    return myChildren;
  }

  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return null;
  }

  /**
   * Border type subproperty
   */
  private final class MyTypeProperty extends Property{
    private final BorderTypeRenderer myRenderer;
    private final BorderTypeEditor myEditor;

    public MyTypeProperty(){
      super(BorderProperty.this, "type");
      myRenderer=new BorderTypeRenderer();
      myEditor=new BorderTypeEditor();
    }

    public Object getValue(final RadComponent component){
      final RadContainer container=(RadContainer)component;
      return container.getBorderType();
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final RadContainer container=(RadContainer)component;
      final BorderType type=(BorderType)value;
      container.setBorderType(type);
    }

    public Property[] getChildren(){
      return EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }

  /**
   * Title subproperty
   */
  private final class MyTitleProperty extends Property{
    private final StringRenderer myRenderer;
    private final StringEditor myEditor;

    public MyTitleProperty(){
      super(BorderProperty.this, "title");
      myRenderer=new StringRenderer();
      myEditor=new StringEditor();
    }

    public Object getValue(final RadComponent component){
      final RadContainer container = (RadContainer)component;
      final StringDescriptor descriptor = container.getBorderTitle();
      final String resolvedValue = ResourceBundleLoader.resolve(component.getModule(), descriptor);
      if (descriptor != null) {
        descriptor.setResolvedValue(resolvedValue);
      }
      return descriptor;
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final RadContainer container=(RadContainer)component;
      StringDescriptor title=(StringDescriptor)value;
      if(title != null && ResourceBundleLoader.resolve(component.getModule(), title).length()==0){
        title=null;
      }
      container.setBorderTitle(title);
    }

    public Property[] getChildren(){
      return EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer(){
      return myRenderer;
    }

    public PropertyEditor getEditor(){
      return myEditor;
    }
  }
}
