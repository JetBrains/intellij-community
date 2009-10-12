/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
