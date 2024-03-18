// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
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

import java.awt.*;
import java.util.function.Supplier;

public final class BorderProperty extends Property<RadContainer, BorderType> {
  public static final @NonNls String NAME = "border";

  private final Project myProject;
  private final Property[] myChildren;

  // Converting this anonymous class to lambda causes javac 11 failure (should compile fine in later javac)
  // suppression can be removed when we migrate to newer Java
  @SuppressWarnings("Convert2Lambda")
  private final NotNullLazyValue<PropertyRenderer<BorderType>> myRenderer = NotNullLazyValue.lazy(
    new Supplier<>() {
      @Override
      public PropertyRenderer<BorderType> get() {
        return new LabelPropertyRenderer<>() {
          @Override
          protected void customize(final @NotNull BorderType value) {
            @NlsSafe String name = value.getName();
            setText(name);
          }
        };
      }
    });

  public BorderProperty(final Project project) {
    super(null, NAME);
    myProject = project;
    myChildren = new Property[]{new MyTypeProperty(), new MyTitleProperty(), new MyTitleIntEnumProperty(this, "title justification", true),
      new MyTitleIntEnumProperty(this, "title position", false), new MyTitleFontProperty(this), new MyBorderColorProperty(this, true)};
  }

  @Override
  public BorderType getValue(final RadContainer component) {
    return component.getBorderType();
  }

  @Override
  protected void setValueImpl(final RadContainer component, final BorderType value) throws Exception {
  }

  @Override
  public Property @NotNull [] getChildren(final RadComponent component) {
    if (!(component instanceof RadContainer)) return Property.EMPTY_ARRAY;
    BorderType borderType = ((RadContainer)component).getBorderType();
    if (borderType.equals(BorderType.EMPTY)) {
      return new Property[]{new MyTypeProperty(), new MySizeProperty(this), new MyTitleProperty(),
        new MyTitleIntEnumProperty(this, "title justification", true), new MyTitleIntEnumProperty(this, "title position", false),
        new MyTitleFontProperty(this), new MyBorderColorProperty(this, true)};
    }
    else if (borderType.equals(BorderType.LINE)) {
      return new Property[]{new MyTypeProperty(), new MyBorderColorProperty(this, false), new MyTitleProperty(),
        new MyTitleIntEnumProperty(this, "title justification", true), new MyTitleIntEnumProperty(this, "title position", false),
        new MyTitleFontProperty(this), new MyBorderColorProperty(this, true)};
    }
    return myChildren;
  }

  @Override
  public @NotNull PropertyRenderer<BorderType> getRenderer() {
    return myRenderer.getValue();
  }

  @Override
  public PropertyEditor<BorderType> getEditor() {
    return null;
  }

  @Override
  public boolean isModified(final RadContainer component) {
    return !component.getBorderType().equals(BorderType.NONE) || component.getBorderTitle() != null;
  }

  @Override
  public void resetValue(RadContainer component) throws Exception {
    component.setBorderType(BorderType.NONE);
    component.setBorderTitle(null);
  }

  /**
   * Border type subproperty
   */
  private final class MyTypeProperty extends Property<RadContainer, BorderType> {
    BorderTypeEditor myEditor;

    MyTypeProperty() {
      super(BorderProperty.this, "type");
    }

    @Override
    public BorderType getValue(final RadContainer component) {
      return component.getBorderType();
    }

    @Override
    protected void setValueImpl(final RadContainer component, final BorderType value) throws Exception {
      component.setBorderType(value);
    }

    @Override
    public @NotNull PropertyRenderer<BorderType> getRenderer() {
      return myRenderer.getValue();
    }

    @Override
    public PropertyEditor<BorderType> getEditor() {
      if (myEditor == null) {
        myEditor = new BorderTypeEditor();
      }
      return myEditor;
    }

    @Override
    public boolean isModified(final RadContainer component) {
      return !getValue(component).equals(BorderType.NONE);
    }

    @Override
    public void resetValue(RadContainer component) throws Exception {
      setValueImpl(component, BorderType.NONE);
    }

    @Override
    public boolean needRefreshPropertyList() {
      return true;
    }
  }

  /**
   * Title subproperty
   */
  private final class MyTitleProperty extends Property<RadContainer, StringDescriptor> {
    private StringRenderer myRenderer;
    private StringEditor myEditor;

    MyTitleProperty() {
      super(BorderProperty.this, "title");
    }

    @Override
    public StringDescriptor getValue(final RadContainer component) {
      final StringDescriptor descriptor = component.getBorderTitle();
      final String resolvedValue = StringDescriptorManager.getInstance(component.getModule()).resolve(component, descriptor);
      if (descriptor != null) {
        descriptor.setResolvedValue(resolvedValue);
      }
      return descriptor;
    }

    @Override
    protected void setValueImpl(final RadContainer component, final StringDescriptor value) throws Exception {
      StringDescriptor title = value;
      if (title != null && StringDescriptorManager.getInstance(component.getModule()).resolve(component, title).isEmpty()) {
        title = null;
      }
      component.setBorderTitle(title);
    }

    @Override
    public @NotNull PropertyRenderer<StringDescriptor> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new StringRenderer();
      }
      return myRenderer;
    }

    @Override
    public PropertyEditor<StringDescriptor> getEditor() {
      if (myEditor == null) {
        myEditor = new StringEditor(myProject);
      }
      return myEditor;
    }

    @Override
    public boolean isModified(final RadContainer component) {
      return getValue(component) != null;
    }

    @Override
    public void resetValue(RadContainer component) throws Exception {
      component.setBorderTitle(null);
    }
  }

  private static final IntEnumEditor.Pair[] ourJustificationPairs =
    new IntEnumEditor.Pair[]{new IntEnumEditor.Pair(0, UIDesignerBundle.message("property.default")),
      new IntEnumEditor.Pair(1, UIDesignerBundle.message("property.left")),
      new IntEnumEditor.Pair(2, UIDesignerBundle.message("property.center")),
      new IntEnumEditor.Pair(3, UIDesignerBundle.message("property.right")),
      new IntEnumEditor.Pair(4, UIDesignerBundle.message("property.leading")),
      new IntEnumEditor.Pair(5, UIDesignerBundle.message("property.trailing"))};

  private static final IntEnumEditor.Pair[] ourPositionPairs =
    new IntEnumEditor.Pair[]{new IntEnumEditor.Pair(0, UIDesignerBundle.message("property.default")),
      new IntEnumEditor.Pair(1, UIDesignerBundle.message("property.above.top")),
      new IntEnumEditor.Pair(2, UIDesignerBundle.message("property.top")),
      new IntEnumEditor.Pair(3, UIDesignerBundle.message("property.below.top")),
      new IntEnumEditor.Pair(4, UIDesignerBundle.message("property.above.bottom")),
      new IntEnumEditor.Pair(5, UIDesignerBundle.message("property.bottom")),
      new IntEnumEditor.Pair(6, UIDesignerBundle.message("property.below.bottom"))};

  private static class MyTitleIntEnumProperty extends Property<RadContainer, Integer> {
    private IntEnumRenderer myRenderer;
    private IntEnumEditor myEditor;
    private final boolean myJustification;

    MyTitleIntEnumProperty(final Property parent, final @NonNls String name, final boolean isJustification) {
      super(parent, name);
      myJustification = isJustification;
    }

    @Override
    public Integer getValue(final RadContainer component) {
      return myJustification ? component.getBorderTitleJustification() : component.getBorderTitlePosition();
    }

    @Override
    protected void setValueImpl(final RadContainer component, final Integer value) throws Exception {
      if (myJustification) {
        component.setBorderTitleJustification(value.intValue());
      }
      else {
        component.setBorderTitlePosition(value.intValue());
      }
    }

    @Override
    public @NotNull PropertyRenderer<Integer> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new IntEnumRenderer(myJustification ? ourJustificationPairs : ourPositionPairs);
      }
      return myRenderer;
    }

    @Override
    public PropertyEditor<Integer> getEditor() {
      if (myEditor == null) {
        myEditor = new IntEnumEditor(myJustification ? ourJustificationPairs : ourPositionPairs);
      }
      return myEditor;
    }

    @Override
    public boolean isModified(final RadContainer component) {
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

    MyTitleFontProperty(final Property parent) {
      super(parent, "title font");
    }

    @Override
    public FontDescriptor getValue(final RadContainer component) {
      return component.getBorderTitleFont();
    }

    @Override
    protected void setValueImpl(final RadContainer component, final FontDescriptor value) throws Exception {
      component.setBorderTitleFont(value);
    }

    @Override
    public @NotNull PropertyRenderer<FontDescriptor> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new FontRenderer();
      }
      return myRenderer;
    }

    @Override
    public PropertyEditor<FontDescriptor> getEditor() {
      if (myEditor == null) {
        myEditor = new FontEditor(UIDesignerBundle.message("border.title.editor.title"));
      }
      return myEditor;
    }

    @Override
    public boolean isModified(final RadContainer component) {
      return component.getBorderTitleFont() != null;
    }

    @Override
    public void resetValue(final RadContainer component) throws Exception {
      component.setBorderTitleFont(null);
    }
  }

  private static class MyBorderColorProperty extends Property<RadContainer, ColorDescriptor> {
    private ColorRenderer myRenderer;
    private ColorEditor myEditor;
    private final boolean myTitleColor;

    MyBorderColorProperty(final Property parent, final boolean titleColor) {
      super(parent, titleColor ? "title color" : "color");
      myTitleColor = titleColor;
    }

    @Override
    public ColorDescriptor getValue(final RadContainer component) {
      return myTitleColor ? component.getBorderTitleColor() : component.getBorderColor();
    }

    @Override
    protected void setValueImpl(final RadContainer component, final ColorDescriptor value) throws Exception {
      if (myTitleColor) {
        component.setBorderTitleColor(value);
      }
      else {
        component.setBorderColor(value);
      }
    }

    @Override
    public @NotNull PropertyRenderer<ColorDescriptor> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new ColorRenderer();
      }
      return myRenderer;
    }

    @Override
    public PropertyEditor<ColorDescriptor> getEditor() {
      if (myEditor == null) {
        myEditor = new ColorEditor(
          myTitleColor ? UIDesignerBundle.message("border.title.editor.title") : UIDesignerBundle.message("border.color.editor.title"));
      }
      return myEditor;
    }

    @Override
    public boolean isModified(final RadContainer component) {
      return getValue(component) != null;
    }

    @Override
    public void resetValue(final RadContainer component) throws Exception {
      setValueImpl(component, null);
    }
  }

  private static class MySizeProperty extends AbstractInsetsProperty<RadContainer> {
    MySizeProperty(final Property parent) {
      super(parent, "size");
    }

    @Override
    public Insets getValue(final RadContainer container) {
      return container.getBorderSize();
    }

    @Override
    protected void setValueImpl(final RadContainer container, final Insets insets) throws Exception {
      container.setBorderSize(insets);
    }
  }
}
