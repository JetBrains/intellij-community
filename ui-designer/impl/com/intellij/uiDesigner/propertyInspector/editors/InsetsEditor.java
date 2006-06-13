/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;

import java.awt.*;

/**
 * @author yole
 */
public class InsetsEditor extends IntRegexEditor<Insets> {
  public InsetsEditor(LabelPropertyRenderer<Insets> renderer) {
    super(Insets.class, renderer, new int[] { 0, 0, 0, 0 });
  }
}
