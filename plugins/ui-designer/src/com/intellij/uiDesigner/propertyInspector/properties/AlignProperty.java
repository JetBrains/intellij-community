// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntEnumRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;


public abstract class AlignProperty extends Property<RadComponent, Integer> {
  private final boolean myHorizontal;
  private IntEnumRenderer myRenderer;
  private IntEnumEditor myEditor;

  public AlignProperty(final boolean horizontal) {
    super(null, horizontal ? "Horizontal Align" : "Vertical Align");
    myHorizontal = horizontal;
  }

  @Override
  public Integer getValue(final RadComponent component) {
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      return provider.getAlignment(component, myHorizontal);
    }
    return Utils.alignFromConstraints(component.getConstraints(), myHorizontal);
  }

  private static AlignPropertyProvider getAlignPropertyProvider(final RadComponent component) {
    if (component.getParent().getLayoutManager() instanceof AlignPropertyProvider) {
      return ((AlignPropertyProvider) component.getParent().getLayoutManager());
    }
    return null;
  }

  @Override
  protected void setValueImpl(final RadComponent component, final Integer value) throws Exception {
    int anchorMask = myHorizontal ? 0x0C : 3;
    int fillMask = myHorizontal ? 1 : 2;
    int anchor = 0;
    int fill = 0;
    switch (value.intValue()) {
      case GridConstraints.ALIGN_FILL -> fill = myHorizontal ? GridConstraints.FILL_HORIZONTAL : GridConstraints.FILL_VERTICAL;
      case GridConstraints.ALIGN_LEFT -> anchor = myHorizontal ? GridConstraints.ANCHOR_WEST : GridConstraints.ANCHOR_NORTH;
      case GridConstraints.ALIGN_RIGHT -> anchor = myHorizontal ? GridConstraints.ANCHOR_EAST : GridConstraints.ANCHOR_SOUTH;
    }
    GridConstraints gc = component.getConstraints();
    GridConstraints oldGC = (GridConstraints) gc.clone();
    gc.setAnchor((gc.getAnchor() & ~anchorMask) | anchor);
    gc.setFill((gc.getFill() & ~fillMask) | fill);
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      provider.setAlignment(component, myHorizontal, value.intValue());
    }
    component.fireConstraintsChanged(oldGC);
  }

  @Override
  public boolean isModified(final RadComponent component) {
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      return provider.isAlignmentModified(component, myHorizontal);
    }
    final ComponentItem item = component.getPalette().getItem(component.getComponentClassName());
    if (item == null) return false;
    return Utils.alignFromConstraints(component.getConstraints(), myHorizontal) !=
           Utils.alignFromConstraints(item.getDefaultConstraints(), myHorizontal);
  }

  @Override
  public void resetValue(final RadComponent component) throws Exception {
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      provider.resetAlignment(component, myHorizontal);
    }
    else {
      final ComponentItem item = component.getPalette().getItem(component.getComponentClassName());
      if (item != null) {
        setValueEx(component, Utils.alignFromConstraints(item.getDefaultConstraints(), myHorizontal));
      }
    }
  }

  @Override
  @NotNull
  public PropertyRenderer<Integer> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new IntEnumRenderer(getPairs());
    }
    return myRenderer;
  }

  private IntEnumEditor.Pair[] getPairs() {
    return new IntEnumEditor.Pair[] {
      new IntEnumEditor.Pair(GridConstraints.ALIGN_LEFT,
                             myHorizontal ? UIDesignerBundle.message("property.left") : UIDesignerBundle.message("property.top")),
      new IntEnumEditor.Pair(GridConstraints.ALIGN_CENTER, UIDesignerBundle.message("property.center")),
      new IntEnumEditor.Pair(GridConstraints.ALIGN_RIGHT,
                             myHorizontal ? UIDesignerBundle.message("property.right") : UIDesignerBundle.message("property.bottom")),
      new IntEnumEditor.Pair(GridConstraints.ALIGN_FILL, UIDesignerBundle.message("property.fill"))
    };
  }

  @Override
  public PropertyEditor<Integer> getEditor() {
    if (myEditor == null) {
      myEditor = new IntEnumEditor(getPairs());
    }
    return myEditor;
  }
}
