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

import org.jetbrains.annotations.NotNull;

import java.awt.Insets;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class InsetsPropertyRenderer extends LabelPropertyRenderer<Insets> {
  private final StringBuffer myBuffer;

  public InsetsPropertyRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(@NotNull final Insets value){
    setText(formatText(value)); 
  }

  public String formatText(final Insets value) {
    myBuffer.setLength(0);
    myBuffer.append('[');
    myBuffer.append(value.top).append(", ");
    myBuffer.append(value.left).append(", ");
    myBuffer.append(value.bottom).append(", ");
    myBuffer.append(value.right);
    myBuffer.append("]");

    // [jeka] important! do not use toString() on the StringBuffer that is reused
    return myBuffer.substring(0, myBuffer.length());
  }
}
