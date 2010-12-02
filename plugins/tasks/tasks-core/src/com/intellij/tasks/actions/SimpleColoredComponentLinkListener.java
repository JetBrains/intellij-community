/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.SimpleColoredComponent;

import java.awt.event.MouseEvent;

/**
 * @author Dmitry Avdeev
 */
public class SimpleColoredComponentLinkListener extends LinkMouseListenerBase {

  private final SimpleColoredComponent myComponent;

  public SimpleColoredComponentLinkListener(SimpleColoredComponent component) {
    myComponent = component;
    component.addMouseListener(this);
    component.addMouseMotionListener(this);
  }

  protected Object getTagAt(MouseEvent e) {
    int fragment = myComponent.findFragmentAt(getX(e));
    if (fragment >= 0) {
      return myComponent.getFragmentTag(fragment);
    }
    return null;
  }

  protected int getX(MouseEvent e) {
    return e.getX();
  }
}
