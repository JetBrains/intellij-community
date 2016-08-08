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
package com.intellij.designer.designSurface;

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyEditorListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class InplaceEditingLayer extends JComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.designSurface.InplaceEditingLayer");

  private final FocusWatcher myFocusWatcher = new FocusWatcher() {
    protected void focusLostImpl(FocusEvent e) {
      Component opposite = e.getOppositeComponent();
      if (e.isTemporary() || opposite != null && SwingUtilities.isDescendingFrom(opposite, getTopComponent())) {
        // Do nothing if focus moves inside top component hierarchy
        return;
      }
      // [vova] we need LaterInvocator here to prevent write-access assertions
      ApplicationManager.getApplication().invokeLater(() -> finishEditing(true), ModalityState.NON_MODAL);
    }
  };
  private final ComponentSelectionListener mySelectionListener = new ComponentSelectionListener() {
    @Override
    public void selectionChanged(EditableArea area) {
      finishEditing(true);
    }
  };
  private PropertyEditorListener myEditorListener = new PropertyEditorListener() {
    @Override
    public void valueCommitted(PropertyEditor source, boolean continueEditing, boolean closeEditorOnError) {
      finishEditing(true);
    }

    @Override
    public void editingCanceled(PropertyEditor source) {
      finishEditing(false);
    }

    @Override
    public void preferredSizeChanged(PropertyEditor source) {
      adjustInplaceComponentSize();
    }
  };

  private RadComponent myRadComponent;
  private List<Property> myProperties;
  private List<PropertyEditor> myEditors;

  private final DesignerEditorPanel myDesigner;
  private JComponent myInplaceComponent;
  private int myPreferredWidth;

  public InplaceEditingLayer(DesignerEditorPanel designer) {
    myDesigner = designer;
  }

  public void startEditing(@Nullable InplaceContext inplaceContext) {
    try {
      List<RadComponent> selection = myDesigner.getSurfaceArea().getSelection();
      if (selection.size() != 1) {
        return;
      }

      myRadComponent = selection.get(0);
      myProperties = myRadComponent.getInplaceProperties();
      if (myProperties.isEmpty()) {
        myRadComponent = null;
        myProperties = null;
        return;
      }

      myInplaceComponent = new JPanel(new GridLayoutManager(myProperties.size(), 2));
      myInplaceComponent.setBorder(new LineMarginBorder(5, 5, 5, 5));
      new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          finishEditing(false);
        }
      }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myInplaceComponent);

      myEditors = new ArrayList<>();

      JComponent componentToFocus = null;
      Font font = null;

      if (inplaceContext == null) {
        inplaceContext = new InplaceContext();
      }

      int row = 0;
      for (Property property : myProperties) {
        JLabel label = new JLabel(property.getName() + ":");
        if (font == null) {
          font = label.getFont().deriveFont(Font.BOLD);
        }
        label.setFont(font);

        myInplaceComponent.add(label,
                               new GridConstraints(row, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null,
                                                   null));

        PropertyEditor editor = property.getEditor();
        myEditors.add(editor);

        JComponent component = editor.getComponent(myRadComponent, myDesigner, property.getValue(myRadComponent), inplaceContext);

        myInplaceComponent.add(component,
                               new GridConstraints(row++, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, 0, null, null, null));

        if (componentToFocus == null) {
          componentToFocus = editor.getPreferredFocusedComponent();
        }
      }

      for (PropertyEditor editor : myEditors) {
        editor.addPropertyEditorListener(myEditorListener);
      }

      Rectangle bounds = myRadComponent.getBounds(this);
      Dimension size = myInplaceComponent.getPreferredSize();
      myPreferredWidth = Math.max(size.width, bounds.width);
      myInplaceComponent.setBounds(bounds.x, bounds.y, myPreferredWidth, size.height);
      add(myInplaceComponent);

      myDesigner.getSurfaceArea().addSelectionListener(mySelectionListener);

      if (componentToFocus == null) {
        componentToFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myInplaceComponent);
      }
      if (componentToFocus == null) {
        componentToFocus = myInplaceComponent;
      }
      if (componentToFocus.requestFocusInWindow()) {
        myFocusWatcher.install(myInplaceComponent);
      }
      else {
        grabFocus();
        final JComponent finalComponentToFocus = componentToFocus;
        ApplicationManager.getApplication().invokeLater(() -> {
          finalComponentToFocus.requestFocusInWindow();
          myFocusWatcher.install(myInplaceComponent);
        });
      }

      enableEvents(AWTEvent.MOUSE_EVENT_MASK);
      repaint();
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private void finishEditing(boolean commit) {
    myDesigner.getSurfaceArea().removeSelectionListener(mySelectionListener);

    if (myInplaceComponent != null) {
      if (commit) {
        myDesigner.getToolProvider().execute(() -> {
          int size = myProperties.size();

          for (int i = 0; i < size; i++) {
            Property property = myProperties.get(i);
            Object oldValue = property.getValue(myRadComponent);
            Object newValue = myEditors.get(i).getValue();

            if (!Comparing.equal(oldValue, newValue)) {
              property.setValue(myRadComponent, newValue);
            }
          }
        }, DesignerBundle.message("command.set.property.value"), true);
      }

      for (PropertyEditor editor : myEditors) {
        editor.removePropertyEditorListener(myEditorListener);
      }

      removeInplaceComponent();
      myFocusWatcher.deinstall(myInplaceComponent);
      myInplaceComponent = null;
    }

    myRadComponent = null;
    myProperties = null;
    myEditors = null;

    myDesigner.getPreferredFocusedComponent().requestFocusInWindow();

    disableEvents(AWTEvent.MOUSE_EVENT_MASK);
    repaint();
  }

  private void adjustInplaceComponentSize() {
    myInplaceComponent.revalidate();
    Dimension size = myInplaceComponent.getPreferredSize();
    myInplaceComponent.setSize(Math.max(size.width, myPreferredWidth), myInplaceComponent.getHeight());
    myInplaceComponent.revalidate();
    repaint();
  }

  private void removeInplaceComponent() {
    // [vova] before removing component from Swing tree we have to
    // request component into glass layer. Otherwise focus from component being removed
    // can go to some RadComponent.

    LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(myDesigner.getPreferredFocusedComponent());
    try {
      remove(myInplaceComponent);
    }
    finally {
      LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
    }
  }

  /**
   * When there is an inplace editor we "listen" all mouse event
   * and finish editing by any MOUSE_PRESSED or MOUSE_RELEASED event.
   * We are acting like yet another glass pane over the standard glass layer.
   */
  protected void processMouseEvent(MouseEvent e) {
    if (myInplaceComponent != null && (MouseEvent.MOUSE_PRESSED == e.getID() || MouseEvent.MOUSE_RELEASED == e.getID())) {
      finishEditing(true);
    }
    // [vova] this is very important! Without this code Swing doen't close popup menu on our
    // layered pane. Swing adds MouseListeners to all component to close popup. If we do not
    // invoke super then we lock all mouse listeners.
    super.processMouseEvent(e);
  }

  public boolean isEditing() {
    return myInplaceComponent != null;
  }
}