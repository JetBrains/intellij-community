// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.psi.PsiNameHelper;
import com.intellij.uiDesigner.UIDesignerBundle;

public final class ClassToBindRenderer extends LabelPropertyRenderer<String> {
  @Override
  public void customize(final String value){
    final String className = PsiNameHelper.getShortClassName(value);
    if(value.length() == className.length()){ // class in default package
      setText(className);
    }
    else{
      final String packageName = value.substring(0, value.length() - className.length() - 1);
      setText(UIDesignerBundle.message("class.in.package", className, packageName));
    }
  }
}
