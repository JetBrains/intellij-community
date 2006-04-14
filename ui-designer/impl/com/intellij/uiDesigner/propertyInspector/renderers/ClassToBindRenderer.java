package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.psi.PsiNameHelper;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ClassToBindRenderer extends LabelPropertyRenderer<String> {
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
