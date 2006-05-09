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
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.list.DottedBorder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: anna
 * Date: 03-Nov-2005
 */
public class HorizontalList extends JPanel {
  private ArrayList<Object> myModel = new ArrayList<Object>();
  private ArrayList<SimpleColoredComponent> myList = new ArrayList<SimpleColoredComponent>();

  private int myFirstIndex = 0;
  private int mySelectedIndex = -1;

  private JButton myLeftButton = new JButton(IconLoader.getIcon("/general/splitLeft.png"));
  private JButton myRightButton = new JButton(IconLoader.getIcon("/general/splitRight.png"));
  private JPanel myScrollablePanel = new JPanel(new GridBagLayout());
  private JScrollPane myScrollPane = ScrollPaneFactory.createScrollPane(myScrollablePanel);
  private int myPreferredWidth = 400;

  public HorizontalList() {
    this(ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public HorizontalList(Object [] objects) {
    setLayout(new BorderLayout());
    setBackground(UIUtil.getListBackground());

    myScrollablePanel.setBackground(UIUtil.getListBackground());
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

    add(myScrollPane, BorderLayout.CENTER);
    add(myLeftButton, BorderLayout.WEST);
    add(myRightButton, BorderLayout.EAST);

    myModel.addAll(Arrays.asList(objects));

    myLeftButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex == -1 && !myModel.isEmpty()){
          mySelectedIndex = myModel.size() - 1;
          paintBorder();
          myList.get(mySelectedIndex).requestFocusInWindow();
        }
        scrollToVisible(1);
        shiftFocusToVisible(1);
      }
    });
    myLeftButton.setBorder(null);

    myRightButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex == -1 && !myModel.isEmpty()){
          mySelectedIndex = myModel.size() - 1;
          paintBorder();
          myList.get(mySelectedIndex).requestFocusInWindow();
        }
        scrollToVisible(-1);
        shiftFocusToVisible(-1);
      }
    });
    myRightButton.setBorder(null);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(-1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_FOCUSED);


    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(- mySelectedIndex);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(getModelSize() - 1 - mySelectedIndex);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), JComponent.WHEN_FOCUSED);


    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex != -1) {
          getCtrlClickHandler(mySelectedIndex).run();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);

    final ActionListener dblClickAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex != -1) {
          final Runnable doubleClickHandler = getDoubleClickHandler(mySelectedIndex);
          if (doubleClickHandler != null) {
            doubleClickHandler.run();
          }
        }
      }
    };
    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), JComponent.WHEN_FOCUSED);

    /*registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        select();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);*/

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        clearBorder();
        mySelectedIndex = -1;
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);


    updateList();
  }

  public void select() {
    if (!myList.isEmpty()){
      clearBorder();
      mySelectedIndex = myModel.size() - 1;
      paintBorder();
      requestFocusInWindow();
    }
  }

  private void shiftFocusToVisible(int direction){
    while (mySelectedIndex != -1 && !myList.get(mySelectedIndex).isShowing()) {
      shiftFocus(direction);
    }
  }

  private void scrollToVisible(int direction){
    myFirstIndex = getIndexByMode(myFirstIndex + direction);
    paintComponent();
  }

  /**
   * @return was model modified
   */
  protected boolean updateModel(){
    return false;
  }


  public void addElement(Object object){
    myModel.add(object);
  }

  public void removeAllElements(){
    myModel.clear();
  }

  public int getSelectedIndex(){
    return mySelectedIndex;
  }

  public Object getSelectedValue(){
    return getElement(mySelectedIndex);
  }

  public SimpleColoredComponent getItem(int index){
    if (index != -1 && index < myModel.size()){
      return myList.get(index);
    }
    return null;
  }

  public Object getElement(int index){
    if (index != -1 && index < myModel.size()){
      return myModel.get(index);
    }
    return null;
  }

  public int getModelSize(){
    return myModel.size();
  }



  /**
   * to be invoked by alarm
   */
  protected void updateList() {
    immediateUpdateList(updateModel());
  }

  protected void immediateUpdateList(boolean update){
    if (update){
      myFirstIndex = 0;
      mySelectedIndex = -1;
      myList.clear();
      int index = 0;
      for (final Object object : myModel) {
        //noinspection NonStaticInitializer
        final SimpleColoredComponent label = new SimpleColoredComponent(){
          {
            setFocusBorderAroundIcon(true);
          }
        };
        label.setFont(UIUtil.getLabelFont());
        label.setIcon(getIcon(object));
        label.append(getPresentableText(object), getTextAttributes(object, false));
        clearBorder(label);
        label.setOpaque(true);
        label.setBackground(UIUtil.getListBackground());
        myList.add(label);
        installActions(index);
        index ++;
      }
    }
    paintComponent();
  }

  protected SimpleTextAttributes getTextAttributes(final Object object, final boolean selected){
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  private static void clearBorder(SimpleColoredComponent label){
    label.setBorder(BorderFactory.createEmptyBorder(2,2,2,4));
  }

  private static void installDottedBorder(SimpleColoredComponent label){
    label.setBorder(new DottedBorder(new Insets(2,2,2,4), UIUtil.getListForeground()));
  }


  private void installActions(final int index) {
    final SimpleColoredComponent label = myList.get(index);
    final Runnable doubleClickHandler = getDoubleClickHandler(index);
    if (doubleClickHandler != null){
      label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
        public boolean value(final MouseEvent e) {
          return !e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 2;
        }
      }, doubleClickHandler, index));
    }

    final Runnable ctrlClickHandler = getCtrlClickHandler(index);
    if (ctrlClickHandler != null){
      label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
        public boolean value(final MouseEvent e) {
          // You cannot distinguish between 3rd mouse button released with Meta down or not. See SunBug: 4029159
          if (e.getID() != MouseEvent.MOUSE_PRESSED && SystemInfo.isMac) return false;

          final int ex = e.getModifiersEx();
          return !e.isConsumed() && !e.isPopupTrigger() && (ex & (SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK)) != 0;
        }
      }, ctrlClickHandler, index));
    }

    final Runnable rightClickHandler = getRightClickHandler(index);
    if (rightClickHandler != null){
      label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
        public boolean value(final MouseEvent e) {
          return !e.isConsumed() && e.isPopupTrigger();
        }
      }, rightClickHandler, index));
    }

    label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
      public boolean value(final MouseEvent e) {
        return !e.isConsumed() && e.getClickCount() == 1 && !e.isPopupTrigger();
      }
    }, new Runnable() {
      public void run() {
        //just select
        requestFocusInWindow();
      }
    }, index));
  }

  private MouseListener getMouseListener(final Condition<MouseEvent> condition, final Runnable handler, final int index){
    return new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        onClick(e);
      }

      public void mousePressed(MouseEvent e) {
        onClick(e);
      }

      public void mouseReleased(MouseEvent e) {
        onClick(e);
      }

      private void onClick(MouseEvent e){
        if (condition.value(e)){
          clearBorder();
          mySelectedIndex = index;
          paintBorder();
          myList.get(mySelectedIndex).requestFocusInWindow();
          handler.run();
          e.consume();
        }
      }
    };
  }


  private void paintComponent() {
    myScrollablePanel.removeAll();
    myScrollablePanel.revalidate();
    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0);
    int width = 0;
    int widthToTheRight = 0;
    final SimpleColoredComponent toBeContLabel = new SimpleColoredComponent();
    toBeContLabel.setFont(UIUtil.getLabelFont());
    toBeContLabel.append("...", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    clearBorder(toBeContLabel);
    final int additionalWidth = toBeContLabel.getPreferredSize().width;//toBeContLabel.getFontMetrics(toBeContLabel.getFont()).stringWidth("...") + 6;
    int wholeWidth = getWidth() - 2 * myLeftButton.getWidth();
    if (mySelectedIndex != -1) {
      myScrollablePanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
      if (myFirstIndex > 0){
        final SimpleColoredComponent preList = new SimpleColoredComponent();
        preList.setFont(UIUtil.getLabelFont());
        preList.append("...", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        clearBorder(preList);
        myScrollablePanel.add(preList, gc);
        wholeWidth -= additionalWidth;
      }
      for (int i = 0; i < myList.size(); i++) {
        final SimpleColoredComponent linkLabel = myList.get(i);
        //final Icon icon = linkLabel.getIcon();
        final int labelWidth = linkLabel.getPreferredSize().width;//linkLabel.getFontMetrics(linkLabel.getFont()).stringWidth(linkLabel.getText()) + (icon != null ?  icon.getIconWidth() + linkLabel.getIconTextGap() : 0) + 6;
        width += labelWidth;
        if (i + 1 > myFirstIndex){
          widthToTheRight += labelWidth;
          if ( widthToTheRight < wholeWidth) {
            myScrollablePanel.add(linkLabel, gc);
          } else {
            myScrollablePanel.add(toBeContLabel, gc);
            break;
          }
        }
      }
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myScrollablePanel.add(Box.createHorizontalBox(), gc);
    } else if (!myModel.isEmpty()){
      myScrollablePanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myScrollablePanel.add(Box.createHorizontalBox(), gc);

      gc.weightx = 0;
      gc.fill = GridBagConstraints.NONE;
      final SimpleColoredComponent preselected = myList.get(myModel.size() - 1);
      installDottedBorder(preselected);
      for (int i = myModel.size() - 1; i >= 0; i--){
        final SimpleColoredComponent linkLabel = myList.get(i);
        //final Icon icon = linkLabel.getIcon();
        width += linkLabel.getPreferredSize().width;//linkLabel.getFontMetrics(linkLabel.getFont()).stringWidth(linkLabel.getText()) + (icon != null ?  icon.getIconWidth() + linkLabel.getIconTextGap() : 0) + 6;
        if (wholeWidth == 0 || width + additionalWidth < wholeWidth || (i == 0 && width < wholeWidth)){
          myScrollablePanel.add(linkLabel, gc);
        } else {
          myFirstIndex = i + 1;
          myScrollablePanel.add(toBeContLabel, gc);
          wholeWidth -= additionalWidth;
          break;
        }
      }
    }

    myPreferredWidth = width + additionalWidth;
    final boolean scrollBarVisible = wholeWidth != 0 && width >= wholeWidth;
    myLeftButton.setVisible(scrollBarVisible);
    myRightButton.setVisible(scrollBarVisible);
    myLeftButton.setEnabled(widthToTheRight >= wholeWidth && myFirstIndex < myModel.size() - 2);
    myRightButton.setEnabled(myFirstIndex > 0);
    myScrollablePanel.revalidate();
    myScrollablePanel.repaint();
  }

  public Dimension getPreferredSize() {
    final Dimension size = new JButton("1").getPreferredSize();
    size.height += 5;
    return size;
  }

  protected int getPreferredWidth() {
     return myPreferredWidth;
  }

  private int getIndexByMode(int index){
    if (index < 0) return myModel.size() + index;
    if (index >= myModel.size()) return index % myModel.size();
    return index;
  }

  private void paintBorder(){
    final SimpleColoredComponent focusedLabel = myList.get(mySelectedIndex);
    focusedLabel.clear();
    final Object o = myModel.get(mySelectedIndex);
    focusedLabel.setIcon(getIcon(o));
    focusedLabel.append(getPresentableText(o), getTextAttributes(o, true));
    focusedLabel.setBackground(UIUtil.getListSelectionBackground());
    focusedLabel.setForeground(UIUtil.getListSelectionForeground());
    installDottedBorder(focusedLabel);
  }

  private void clearBorder(){
    if (myModel.isEmpty()) return;
    final int index = mySelectedIndex != -1 ? mySelectedIndex : myModel.size() - 1;
    final SimpleColoredComponent focusLostLabel = myList.get(index);
    focusLostLabel.clear();
    final Object o = myModel.get(index);
    focusLostLabel.setIcon(getIcon(o));
    focusLostLabel.append(getPresentableText(o), getTextAttributes(o, false));
    focusLostLabel.setBackground(UIUtil.getListBackground());
    focusLostLabel.setForeground(UIUtil.getListForeground());
    clearBorder(focusLostLabel);
  }

  protected void shiftFocus(int direction){
    clearBorder();
    mySelectedIndex = getIndexByMode(mySelectedIndex + direction);
    paintBorder();
    while (!myList.get(mySelectedIndex).isShowing()){
      scrollToVisible(direction);
    }
  }

  protected String getPresentableText(Object object){
    return object.toString();
  }

  @Nullable
  protected Icon getIcon(Object object){
    return null;
  }

  @Nullable
  protected Runnable getDoubleClickHandler(int index){
    return null;
  }

  protected Runnable getCtrlClickHandler(int index){
    return null;
  }

  protected Runnable getRightClickHandler(int index){
    return null;
  }
}
