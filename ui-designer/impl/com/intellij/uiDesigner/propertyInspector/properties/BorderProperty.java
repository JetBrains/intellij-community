package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BorderTypeEditor;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.shared.BorderType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BorderProperty extends Property<RadContainer, BorderType> {
  private Project myProject;
  private final Property[] myChildren;

  private final PropertyRenderer<BorderType> myRenderer = new LabelPropertyRenderer<BorderType>() {
    protected void customize(final BorderType value) {
      setText(value.getName());
    }
  };

  public BorderProperty(final Project project){
    super(null, "border");
    myProject = project;
    myChildren=new Property[]{
      new MyTypeProperty(),
      new MyTitleProperty()
    };
  }

  public BorderType getValue(final RadContainer component){
    return component.getBorderType();
  }

  protected void setValueImpl(final RadContainer component,final BorderType value) throws Exception{
  }

  @NotNull
  public Property[] getChildren(){
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<BorderType> getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return null;
  }

  @Override public boolean isModified(final RadContainer component) {
    return !component.getBorderType().equals(BorderType.NONE) || component.getBorderTitle() != null;
  }

  @Override public void resetValue(RadContainer component) throws Exception {
    component.setBorderType(BorderType.NONE);
    component.setBorderTitle(null);
  }

  /**
   * Border type subproperty
   */
  private final class MyTypeProperty extends Property<RadContainer, BorderType> {
    BorderTypeEditor myEditor;

    public MyTypeProperty(){
      super(BorderProperty.this, "type");
    }

    public BorderType getValue(final RadContainer component){
      return component.getBorderType();
    }

    protected void setValueImpl(final RadContainer component,final BorderType value) throws Exception{
      component.setBorderType(value);
    }

    @NotNull
    public PropertyRenderer<BorderType> getRenderer() {
      return myRenderer;
    }

    public PropertyEditor getEditor() {
      if (myEditor == null) {
        myEditor = new BorderTypeEditor();
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadContainer component) {
      return !getValue(component).equals(BorderType.NONE);
    }

    @Override public void resetValue(RadContainer component) throws Exception {
      setValueImpl(component, BorderType.NONE);
    }
  }

  /**
   * Title subproperty
   */
  private final class MyTitleProperty extends Property<RadContainer, StringDescriptor> {
    private StringRenderer myRenderer;
    private StringEditor myEditor;

    public MyTitleProperty(){
      super(BorderProperty.this, "title");
    }

    public StringDescriptor getValue(final RadContainer component) {
      final StringDescriptor descriptor = component.getBorderTitle();
      final String resolvedValue = ReferenceUtil.resolve(component, descriptor);
      if (descriptor != null) {
        descriptor.setResolvedValue(resolvedValue);
      }
      return descriptor;
    }

    protected void setValueImpl(final RadContainer component,final StringDescriptor value) throws Exception {
      StringDescriptor title=value;
      if(title != null && ReferenceUtil.resolve(component, title).length()==0){
        title=null;
      }
      component.setBorderTitle(title);
    }

    @NotNull
    public PropertyRenderer<StringDescriptor> getRenderer() {
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

    @Override public boolean isModified(final RadContainer component) {
      return getValue(component) != null;
    }

    @Override public void resetValue(RadContainer component) throws Exception {
      component.setBorderTitle(null);
    }
  }
}
