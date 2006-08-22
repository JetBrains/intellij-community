/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * Due to many bugs and "features" in <code>JComboBox</code> implementation we provide
 * our own "patch". First of all it has correct preferred and minimum sizes that has sense
 * when combo box is editable. Also this implementation fixes some bugs with clicking
 * of default button. The SUN's combo box eats first "Enter" if the selected value from
 * the list and changed it. They say that combo box "commit" changes and only second
 * "Enter" clicks default button. This implementation clicks the default button
 * immediately. As the result of our patch combo box has internal wrapper for ComboBoxEditor.
 * It means that <code>getEditor</code> method always returns not the same value you set
 * by <code>setEditor</code> method. Moreover adding and removing of action listeners
 * isn't supported by the implementation of wrapper.
 *
 * @author Vladimir Kondratyev
 */
public class ComboBox extends JComboBox {
  private int myMinimumAndPreferredWidth;

  public ComboBox() {
    this(-1);
  }

  /**
   * @param minimumAndPreferredWidth preferred width of the combobox. Value <code>-1</code> means undefined.
   */
  public ComboBox(final int minimumAndPreferredWidth) {
    this(new DefaultComboBoxModel(), minimumAndPreferredWidth);
  }

  public ComboBox(final ComboBoxModel model, final int minimumAndPreferredWidth) {
    super(model);
    myMinimumAndPreferredWidth = minimumAndPreferredWidth;
    registerCancelOnEscape();
  }

  public ComboBox(final Object[] items, final int preferredWidth) {
    super(items);
    myMinimumAndPreferredWidth = preferredWidth;
    registerCancelOnEscape();
  }

  public void setMinimumAndPreferredWidth(final int minimumAndPreferredWidth) {
    myMinimumAndPreferredWidth = minimumAndPreferredWidth;
  }

  private static DialogWrapperDialog getParentDialog(Component c) {
    do {
      if (c == null || c instanceof DialogWrapperDialog) return (DialogWrapperDialog)c;
      c = c.getParent();
    }
    while (true);
  }

  private void registerCancelOnEscape() {
    registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final DialogWrapperDialog dialogWrapperDialog = getParentDialog(ComboBox.this);
          final DialogWrapper dialogWrapper = dialogWrapperDialog == null ? null : dialogWrapperDialog.getDialogWrapper();

          if (isPopupVisible()) {
            setPopupVisible(false);
          }
          else {
            //noinspection HardCodedStringLiteral
            final Object clientProperty = getClientProperty("tableCellEditor");
            if(clientProperty instanceof CellEditor){
              // If combo box is inside editable table then we need to cancel editing
              // and do not close heavy weight dialog container (if any)
              ((CellEditor)clientProperty).cancelCellEditing();
            }
            else if (dialogWrapper != null) {
              dialogWrapper.doCancelAction();
            }
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );
  }

  public final void setEditor(final ComboBoxEditor editor) {
    super.setEditor(new MyEditor(this, editor));
  }

  public final Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public final Dimension getPreferredSize() {
    int width = myMinimumAndPreferredWidth;
    if (width < 0) {
      width = super.getPreferredSize().width;
    }
    final int height = getPreferredHeight();
    return new Dimension(width, height);
  }

  private int getPreferredHeight() {
    final Border border = getBorder();
    final ComboBoxEditor editor = getEditor();
    int height;
    if (editor != null) {
      height = editor.getEditorComponent().getPreferredSize().height;
      if (border != null) {
        height += border.getBorderInsets(this).top + border.getBorderInsets(this).bottom;
      }
    }
    else {
      height = super.getPreferredSize().height;
    }
    return height;
  }

  private static final class MyEditor implements ComboBoxEditor {
    private final JComboBox myComboBox;
    private final ComboBoxEditor myDelegate;

    public MyEditor(final JComboBox comboBox, final ComboBoxEditor delegate) {
      myComboBox = comboBox;
      myDelegate = delegate;
      if (myDelegate != null) {
        myDelegate.addActionListener(
          new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
              if (myComboBox.isPopupVisible()) {
                myComboBox.setPopupVisible(false);
              }
              else {
                //noinspection HardCodedStringLiteral
                final Object clientProperty = myComboBox.getClientProperty("tableCellEditor");
                if(clientProperty instanceof CellEditor){
                  // If combo box is inside editable table then we need to cancel editing
                  // and do not close heavy weight dialog container (if any)
                  ((CellEditor)clientProperty).stopCellEditing();
                }
                else{
                  myComboBox.setSelectedItem(getItem());
                  final JRootPane rootPane = myComboBox.getRootPane();
                  if (rootPane != null) {
                    final JButton button = rootPane.getDefaultButton();
                    if (button != null) {
                      button.doClick();
                    }
                  }
                }
              }
            }
          }
        );
      }
    }

    public void addActionListener(final ActionListener l) {}

    public Component getEditorComponent() {
      if (myDelegate != null) {
        return myDelegate.getEditorComponent();
      }
      else {
        return null;
      }
    }

    public Object getItem() {
      if (myDelegate != null) {
        return myDelegate.getItem();
      }
      else {
        return null;
      }
    }

    public void removeActionListener(final ActionListener l) {}

    public void selectAll() {
      if (myDelegate != null) {
        myDelegate.selectAll();
      }
    }

    public void setItem(final Object obj) {
      if (myDelegate != null) {
        myDelegate.setItem(obj);
      }
    }
  }
}
