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
package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;

import java.awt.LayoutManager;

/**
 * @author yole
 */
public abstract class LayoutSourceGenerator {
  public void generateContainerLayout(final LwContainer component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable) {
  }

  public abstract void generateComponentLayout(final LwComponent component,
                                               final FormSourceCodeGenerator generator,
                                               final String variable,
                                               final String parentVariable);

  public String mapComponentClass(final String componentClassName) {
    return componentClassName.replace("$", ".");
  }

  protected void generateLayoutWithGaps(final LwContainer component,
                                        final FormSourceCodeGenerator generator,
                                        final String variable,
                                        final Class<? extends LayoutManager> layoutClass) {
    generator.startMethodCall(variable, "setLayout");

    generator.startConstructor(layoutClass.getName());
    generator.push(Utils.getHGap(component.getLayout()));
    generator.push(Utils.getVGap(component.getLayout()));
    generator.endConstructor();

    generator.endMethod();
  }
}
