// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.UIDesignerBundle;

public final class IntEditor extends AbstractTextFieldEditor<Integer> {
  private final int myLowBoundary;

  /**
   * @param lowBoundary minimal integer value that editor accepts.
   */
  public IntEditor(final int lowBoundary){
    myLowBoundary = lowBoundary;
  }

  @Override
  public Integer getValue() throws Exception{
    try {
      final Integer value = Integer.valueOf(myTf.getText());
      if(value.intValue() < myLowBoundary){
        throw new RuntimeException(UIDesignerBundle.message("error.value.should.not.be.less", myLowBoundary));
      }
      return value;
    }
    catch (final NumberFormatException exc) {
      throw new RuntimeException(UIDesignerBundle.message("error.not.an.integer"));
    }
  }
}

