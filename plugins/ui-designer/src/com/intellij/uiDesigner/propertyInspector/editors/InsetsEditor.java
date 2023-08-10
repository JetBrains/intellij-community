// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;

import java.awt.*;


public class InsetsEditor extends IntRegexEditor<Insets> {
  public InsetsEditor(LabelPropertyRenderer<Insets> renderer) {
    super(Insets.class, renderer, new int[] { 0, 0, 0, 0 });
  }
}
