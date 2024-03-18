// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import com.intellij.uiDesigner.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.IntEnumRenderer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


public class RadFlowLayoutManager extends RadAbstractIndexedLayoutManager {
  private static final MyAlignProperty ALIGN_PROPERTY = new MyAlignProperty();

  @Override
  public String getName() {
    return UIFormXmlConstants.LAYOUT_FLOW;
  }

  @Override
  public LayoutManager createLayout() {
    return new FlowLayout();
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    FlowLayout layout = (FlowLayout) radContainer.getLayout();
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_FLOW_ALIGN, layout.getAlignment());
  }

  @Override
  public @NotNull ComponentDropLocation getDropLocation(RadContainer container, final Point location) {
    FlowLayout flowLayout = (FlowLayout) container.getLayout();
    return new FlowDropLocation(container, location, flowLayout.getAlignment(),
                                (flowLayout.getHgap()+1)/2, (flowLayout.getVgap()+1)/2);
  }

  @Override public Property[] getContainerProperties(final Project project) {
    return new Property[] {
      ALIGN_PROPERTY,
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project) };
  }

  private static class MyAlignProperty extends Property<RadContainer, Integer> {
    private IntEnumRenderer myRenderer;
    private IntEnumEditor myEditor;
    private IntEnumEditor.Pair[] myPairs;

    MyAlignProperty() {
      super(null, "Alignment");
    }

    private void initPairs() {
      if (myPairs == null) {
        myPairs = new IntEnumEditor.Pair[] {
          new IntEnumEditor.Pair(FlowLayout.CENTER, UIDesignerBundle.message("property.center")),
          new IntEnumEditor.Pair(FlowLayout.LEFT, UIDesignerBundle.message("property.left")),
          new IntEnumEditor.Pair(FlowLayout.RIGHT, UIDesignerBundle.message("property.right")),
          new IntEnumEditor.Pair(FlowLayout.LEADING, UIDesignerBundle.message("property.leading")),
          new IntEnumEditor.Pair(FlowLayout.TRAILING, UIDesignerBundle.message("property.trailing"))
        };
      }
    }

    @Override
    public Integer getValue(final RadContainer component) {
      final LayoutManager layout = component.getLayout();
      if (!(layout instanceof FlowLayout flowLayout)) return null;
      return flowLayout.getAlignment();
    }

    @Override
    protected void setValueImpl(final RadContainer component, final Integer value) throws Exception {
      FlowLayout flowLayout = (FlowLayout) component.getLayout();
      flowLayout.setAlignment(value.intValue());
    }

    @Override
    public @NotNull PropertyRenderer<Integer> getRenderer() {
      if (myRenderer == null) {
        initPairs();
        myRenderer = new IntEnumRenderer(myPairs);
      }
      return myRenderer;
    }

    @Override
    public @NotNull PropertyEditor<Integer> getEditor() {
      if (myEditor == null) {
        initPairs();
        myEditor = new IntEnumEditor(myPairs);
      }
      return myEditor;
    }

    @Override public boolean isModified(final RadContainer component) {
      final LayoutManager layout = component.getLayout();
      if (!(layout instanceof FlowLayout flowLayout)) return false;
      return flowLayout.getAlignment() != FlowLayout.CENTER;
    }
  }
}
