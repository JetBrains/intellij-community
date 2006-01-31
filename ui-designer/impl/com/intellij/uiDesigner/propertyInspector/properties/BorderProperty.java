package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BorderTypeEditor;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BorderTypeRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BorderProperty extends Property {
  private Project myProject;
  private final Property[] myChildren;
  private final BorderTypeRenderer myRenderer;

  public BorderProperty(final Project project){
    super(null, "border");
    myProject = project;
    myChildren=new Property[]{
      new MyTypeProperty(),
      new MyTitleProperty()
    };
    myRenderer=new BorderTypeRenderer();
  }

  public Object getValue(final RadComponent component){
    if(!(component instanceof RadContainer)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    return container.getBorderType();
  }

  protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
    if(!(component instanceof RadContainer)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
  }

  @NotNull
  public Property[] getChildren(){
    return myChildren;
  }

  @NotNull
  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return null;
  }

  @Override public boolean isModified(final RadComponent component) {
    final RadContainer container=(RadContainer)component;
    return !container.getBorderType().equals(BorderType.NONE) || container.getBorderTitle() != null;
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    final RadContainer container=(RadContainer)component;
    container.setBorderType(BorderType.NONE);
    container.setBorderTitle(null);
  }

  /**
   * Border type subproperty
   */
  private final class MyTypeProperty extends Property{
    BorderTypeRenderer myRenderer;
    BorderTypeEditor myEditor;

    public MyTypeProperty(){
      super(BorderProperty.this, "type");
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

    @NotNull
    public PropertyRenderer getRenderer() {
      if (myRenderer == null) {
        myRenderer = new BorderTypeRenderer();
      }
      return myRenderer;
    }

    public PropertyEditor getEditor() {
      if (myEditor == null) {
        myEditor = new BorderTypeEditor();
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadComponent component) {
      return !getValue(component).equals(BorderType.NONE);
    }

    @Override public void resetValue(RadComponent component) throws Exception {
      setValueImpl(component, BorderType.NONE);
    }
  }

  /**
   * Title subproperty
   */
  private final class MyTitleProperty extends Property{
    private StringRenderer myRenderer;
    private StringEditor myEditor;

    public MyTitleProperty(){
      super(BorderProperty.this, "title");
    }

    public Object getValue(final RadComponent component){
      final RadContainer container = (RadContainer)component;
      final StringDescriptor descriptor = container.getBorderTitle();
      final String resolvedValue = ReferenceUtil.resolve(component, descriptor);
      if (descriptor != null) {
        descriptor.setResolvedValue(resolvedValue);
      }
      return descriptor;
    }

    protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
      final RadContainer container=(RadContainer)component;
      StringDescriptor title=(StringDescriptor)value;
      if(title != null && ReferenceUtil.resolve(component, title).length()==0){
        title=null;
      }
      container.setBorderTitle(title);
    }

    @NotNull
    public PropertyRenderer getRenderer() {
      if (myRenderer == null) {
        myRenderer = new StringRenderer();
      }
      return myRenderer;
    }

    public PropertyEditor getEditor() {
      if (myEditor == null) {
        myEditor = new StringEditor(myProject);
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadComponent component) {
      return getValue(component) != null;
    }

    @Override public void resetValue(RadComponent component) throws Exception {
      final RadContainer container=(RadContainer)component;
      container.setBorderTitle(null);
    }
  }
}
