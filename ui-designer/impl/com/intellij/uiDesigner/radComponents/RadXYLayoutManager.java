/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.AbstractLayout;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author yole
 */
public class RadXYLayoutManager extends RadLayoutManager {
  public static RadXYLayoutManager INSTANCE = new RadXYLayoutManager();

  public @NonNls String getName() {
    return "XYLayout";
  }

  public LayoutManager createLayout() {
    return new XYLayoutManagerImpl();
  }

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    final AbstractLayout layout = (AbstractLayout)radContainer.getLayout();
    // It has sense to save hpap and vgap even for XY layout. The reason is
    // that XY was previously GRID with non default gaps, so when the user
    // compose XY into the grid again then he will get the same non default gaps.
    writer.addAttribute("hgap", layout.getHGap());
    writer.addAttribute("vgap", layout.getVGap());

    // Margins
    final Insets margin = layout.getMargin();
    writer.startElement("margin");
    try {
      writer.addAttribute("top", margin.top);
      writer.addAttribute("left", margin.left);
      writer.addAttribute("bottom", margin.bottom);
      writer.addAttribute("right", margin.right);
    }
    finally {
      writer.endElement(); // margin
    }
  }

  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    // Constraints of XY layout
    writer.startElement("xy");
    try{
      writer.addAttribute("x", child.getX());
      writer.addAttribute("y", child.getY());
      writer.addAttribute("width", child.getWidth());
      writer.addAttribute("height", child.getHeight());
    }finally{
      writer.endElement(); // xy
    }
  }
}
