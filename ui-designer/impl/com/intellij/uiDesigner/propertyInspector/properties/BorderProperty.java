package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BorderTypeEditor;
import com.intellij.uiDesigner.propertyInspector.editors.ColorEditor;
import com.intellij.uiDesigner.propertyInspector.editors.FontEditor;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.*;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.shared.BorderType;
import org.jetbrains.annotations.NonNls;
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
      new MyTitleProperty(),
      new MyTitleIntEnumProperty(this, "title justification", true),
      new MyTitleIntEnumProperty(this, "title position", false),
      new MyTitleFontProperty(this),
      new MyTitleColorProperty(this)
    };
  }

  public BorderType getValue(final RadContainer component){
    return component.getBorderType();
  }

  protected void setValueImpl(final RadContainer component,final BorderType value) throws Exception{
  }

  @NotNull
  public Property[] getChildren(final RadComponent component){
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<BorderType> getRenderer(){
    return myRenderer;
  }

  public PropertyEditor<BorderType> getEditor(){
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

    public PropertyEditor<BorderType> getEditor() {
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
      final String resolvedValue = StringDescriptorManager.getInstance(component.getModule()).resolve(component, descriptor);
      if (descriptor != null) {
        descriptor.setResolvedValue(resolvedValue);
      }
      return descriptor;
    }

    protected void setValueImpl(final RadContainer component,final StringDescriptor value) throws Exception {
      StringDescriptor title=value;
      if(title != null && StringDescriptorManager.getInstance(component.getModule()).resolve(component, title).length()==0){
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

    public PropertyEditor<StringDescriptor> getEditor() {
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

  private static IntEnumEditor.Pair[] ourJustificationPairs = new IntEnumEditor.Pair[] {
    new IntEnumEditor.Pair(0, UIDesignerBundle.message("property.default")),
    new IntEnumEditor.Pair(1, UIDesignerBundle.message("property.left")),
    new IntEnumEditor.Pair(2, UIDesignerBundle.message("property.center")),
    new IntEnumEditor.Pair(3, UIDesignerBundle.message("property.right")),
    new IntEnumEditor.Pair(4, UIDesignerBundle.message("property.leading")),
    new IntEnumEditor.Pair(5, UIDesignerBundle.message("property.trailing"))
  };

  private static IntEnumEditor.Pair[] ourPositionPairs = new IntEnumEditor.Pair[] {
    new IntEnumEditor.Pair(0, UIDesignerBundle.message("property.default")),
    new IntEnumEditor.Pair(1, UIDesignerBundle.message("property.above.top")),
    new IntEnumEditor.Pair(2, UIDesignerBundle.message("property.top")),
    new IntEnumEditor.Pair(3, UIDesignerBundle.message("property.below.top")),
    new IntEnumEditor.Pair(4, UIDesignerBundle.message("property.above.bottom")),
    new IntEnumEditor.Pair(5, UIDesignerBundle.message("property.bottom")),
    new IntEnumEditor.Pair(6, UIDesignerBundle.message("property.below.bottom"))
  };

  private static class MyTitleIntEnumProperty extends Property<RadContainer, Integer> {
    private IntEnumRenderer myRenderer;
    private IntEnumEditor myEditor;
    private final boolean myJustification;

    public MyTitleIntEnumProperty(final Property parent, @NonNls final String name, final boolean isJustification) {
      super(parent, name);
      myJustification = isJustification;
    }

    public Integer getValue(final RadContainer component) {
      return myJustification ? component.getBorderTitleJustification() : component.getBorderTitlePosition();
    }

    protected void setValueImpl(final RadContainer component, final Integer value) throws Exception {
      if (myJustification) {
        component.setBorderTitleJustification(value.intValue());
      }
      else {
        component.setBorderTitlePosition(value.intValue());
      }
    }

    @NotNull
    public PropertyRenderer<Integer> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new IntEnumRenderer(myJustification ? ourJustificationPairs : ourPositionPairs);
      }
      return myRenderer;
    }

    public PropertyEditor<Integer> getEditor() {
      if (myEditor == null) {
        myEditor = new IntEnumEditor(myJustification ? ourJustificationPairs : ourPositionPairs);
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadContainer component) {
      return getValue(component).intValue() != 0;
    }

    @Override
    public void resetValue(final RadContainer component) throws Exception {
      setValue(component, 0);
    }
  }

  private static class MyTitleFontProperty extends Property<RadContainer, FontDescriptor> {
    private FontRenderer myRenderer;
    private FontEditor myEditor;

    public MyTitleFontProperty(final Property parent) {
      super(parent, "title font");
    }

    public FontDescriptor getValue(final RadContainer component) {
      return component.getBorderTitleFont();
    }

    protected void setValueImpl(final RadContainer component, final FontDescriptor value) throws Exception {
      component.setBorderTitleFont(value);
    }

    @NotNull
    public PropertyRenderer<FontDescriptor> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new FontRenderer();
      }
      return myRenderer;
    }

    public PropertyEditor<FontDescriptor> getEditor() {
      if (myEditor == null) {
        myEditor = new FontEditor(UIDesignerBundle.message("border.title.editor.title"));
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadContainer component) {
      return component.getBorderTitleFont() != null;
    }

    @Override public void resetValue(final RadContainer component) throws Exception {
      component.setBorderTitleFont(null);
    }
  }

  private static class MyTitleColorProperty extends Property<RadContainer, ColorDescriptor> {
    private ColorRenderer myRenderer;
    private ColorEditor myEditor;

    public MyTitleColorProperty(final Property parent) {
      super(parent, "title color");
    }

    public ColorDescriptor getValue(final RadContainer component) {
      return component.getBorderTitleColor();
    }

    protected void setValueImpl(final RadContainer component, final ColorDescriptor value) throws Exception {
      component.setBorderTitleColor(value);
    }

    @NotNull
    public PropertyRenderer<ColorDescriptor> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new ColorRenderer();
      }
      return myRenderer;
    }

    public PropertyEditor<ColorDescriptor> getEditor() {
      if (myEditor == null) {
        myEditor = new ColorEditor(UIDesignerBundle.message("border.title.editor.title"));
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadContainer component) {
      return component.getBorderTitleColor() != null;
    }

    @Override public void resetValue(final RadContainer component) throws Exception {
      component.setBorderTitleColor(null);
    }
  }
}
