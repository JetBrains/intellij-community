/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class DecorationLayer extends JComponent {
  private final DesignerEditorPanel myDesigner;
  private final EditableArea myArea;
  private boolean myShowSelection = true;

  public DecorationLayer(DesignerEditorPanel designer, EditableArea area) {
    myDesigner = designer;
    myArea = area;
  }

  public EditableArea getArea() {
    return myArea;
  }

  public boolean showSelection() {
    return myShowSelection;
  }

  public void showSelection(boolean value) {
    if (myShowSelection != value) {
      myShowSelection = value;
      repaint();
    }
  }

  @Nullable
  public InputTool findTargetTool(int x, int y) {
    List<RadComponent> selection = myArea.getSelection();
    for (RadComponent component : selection) {
      ComponentDecorator decorator = getDecorator(component, selection);
      InputTool tracker = decorator.findTargetTool(this, component, x, y);
      if (tracker != null) {
        return tracker;
      }
    }
    return null;
  }

  @Override
  public void paint(Graphics g) {
    try {
      if (myArea.getRootComponent() != null) {
        Graphics2D g2d = (Graphics2D)g;
        paintStaticDecorators(g2d);
        if (myShowSelection) {
          paintSelection(g2d);
        }
      }
    }
    catch (Throwable e) {
      myDesigner.showError("Paint operation", e);
    }
  }

  private void paintStaticDecorators(Graphics2D g) {
    final List<StaticDecorator> decorators = new ArrayList<>();
    final List<RadComponent> selection = myArea.getSelection();

    myArea.getRootComponent().accept(new RadComponentVisitor() {
      @Override
      public void endVisit(RadComponent component) {
        component.addStaticDecorators(decorators, selection);
      }
    }, true);

    for (StaticDecorator decorator : decorators) {
      decorator.decorate(this, g);
    }
  }

  private void paintSelection(Graphics2D g) {
    List<RadComponent> selection = myArea.getSelection();
    for (RadComponent component : selection) {
      ComponentDecorator decorator = getDecorator(component, selection);
      decorator.decorate(this, g, component);
    }
  }

  private ComponentDecorator getDecorator(RadComponent component, List<RadComponent> selection) {
    RadComponent parent = component.getParent();
    if (parent == null) {
      return myArea.getRootSelectionDecorator();
    }
    return parent.getLayout().getChildSelectionDecorator(component, selection);
  }

  public double getZoom() {
    return myDesigner.getZoom();
  }
}