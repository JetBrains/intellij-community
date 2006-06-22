/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FloatEditor extends AbstractTextFieldEditor<Float> {
  public Float getValue() throws Exception{
    return Float.valueOf(myTf.getText());
  }
}
