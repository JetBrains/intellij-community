package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.diagnostic.Logger;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundReferenceRenderer extends CompoundNodeRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer");

  public CompoundReferenceRenderer(final NodeRendererSettings rendererSettings, String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    super(rendererSettings, name, labelRenderer, childrenRenderer);
    myProperties.setClassName("java.lang.Object");
    LOG.assertTrue(labelRenderer == null || labelRenderer instanceof ReferenceRenderer);
    LOG.assertTrue(childrenRenderer == null || childrenRenderer instanceof ReferenceRenderer);
  }

  public void setLabelRenderer(ValueLabelRenderer labelRenderer) {
    super.setLabelRenderer(NodeRendererSettings.getInstance().isDefault(labelRenderer) ? null : labelRenderer);
  }

  public void setChildrenRenderer(ChildrenRenderer childrenRenderer) {
    super.setChildrenRenderer(NodeRendererSettings.getInstance().isDefault(childrenRenderer) ? null : childrenRenderer);
  }

  public ChildrenRenderer getChildrenRenderer() {
    return super.getChildrenRenderer() != null ? super.getChildrenRenderer() : getDefaultRenderer();
  }

  private NodeRenderer getDefaultRenderer() {
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();
    return  getClassName().endsWith("]") ? (NodeRenderer)rendererSettings.getArrayRenderer() : (NodeRenderer)rendererSettings.getClassRenderer();
  }

  public ValueLabelRenderer getLabelRenderer() {
    return super.getLabelRenderer() != null ? super.getLabelRenderer() : getDefaultRenderer();
  }

  private ChildrenRenderer getRawChildrenRenderer() {
    NodeRenderer classRenderer = getDefaultRenderer();
    return myChildrenRenderer == classRenderer ? null : myChildrenRenderer;
  }

  private ValueLabelRenderer getRawLabelRenderer() {
    NodeRenderer classRenderer = getDefaultRenderer();
    return myLabelRenderer == classRenderer ? null : myLabelRenderer;
  }

  public void setClassName(String name) {
    LOG.assertTrue(name != null);
    myProperties.setClassName(name);
    if(getRawLabelRenderer() != null) {
      ((ReferenceRenderer)myLabelRenderer).setClassName(name);
    }

    if(getRawChildrenRenderer() != null) {
      ((ReferenceRenderer)myChildrenRenderer).setClassName(name);
    }
  }

  public String getClassName() {
    return myProperties.getClassName();
  }
}
