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

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RectangleRenderer extends LabelPropertyRenderer<Rectangle> {
  private final StringBuffer myBuffer;

  public RectangleRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(final Rectangle value){
    myBuffer.setLength(0);
    myBuffer.append('[').append(value.x).append(", ");
    myBuffer.append(value.y).append(", ");
    myBuffer.append(value.width).append(", ");
    myBuffer.append(value.height).append("]");

    setText(myBuffer.substring(0, myBuffer.length())); // [jeka] important! do not use toString() on the StringBuffer that is reused
  }
}
