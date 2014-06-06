/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.xml.ui;

import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.DomElement;

import java.awt.*;


/**
 * User: Sergey.Vasiliev
 * Date: Nov 18, 2005
 */
public abstract class AbstractDomElementComponent<T extends DomElement> extends CompositeCommittable implements CommittablePanel {
  protected T myDomElement;

  protected AbstractDomElementComponent(final T domElement) {
    myDomElement = domElement;
  }

  public T getDomElement() {
    return myDomElement;
  }

  protected static void setEnabled(Component component, boolean enabled) {
    UIUtil.setEnabled(component, enabled, true);
  }
}
