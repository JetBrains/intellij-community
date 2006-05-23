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

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.LayeredIcon;
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
  private ArrayList<MyCompositeLabel> myList = new ArrayList<MyCompositeLabel>();

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

    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), JComponent.WHEN_FOCUSED);

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

  public MyCompositeLabel getItem(int index){
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


  public int getIndexOf(Object object){
    return myModel.indexOf(object);
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
        final MyCompositeLabel label = new MyCompositeLabel();
        label.getLabel().addMouseListener(new MouseAdapter() {
          public void mouseExited(MouseEvent e) {
            if (!hasChildren(object)) return;
            label.getLabel().setIcon(wrapIcon(object, Color.gray));
            label.repaint();
          }

          public void mouseClicked(MouseEvent e) {
            if (!hasChildren(object)) return;
            final int selectedIndex = myModel.indexOf(object);
            final ListPopup popup = getPopup();
            if (mySelectedIndex == selectedIndex && popup != null){
              cancelPopup();
              if (isInsideIcon(e.getPoint(), object)) {
                label.getLabel().setIcon(wrapIcon(object, Color.black));
                label.getLabel().repaint();
              }
              return;
            }
            if (isInsideIcon(e.getPoint(), object)) {
              getCtrlClickHandler(selectedIndex).run();
              clearBorder();
              mySelectedIndex = selectedIndex;
              paintBorder();
              label.getLabel().setIcon(wrapIcon(object, Color.black));
              label.getLabel().repaint();
            }
          }
        });
        label.getLabel().addMouseMotionListener(new MouseMotionAdapter() {
          public void mouseMoved(MouseEvent e) {
            if (!hasChildren(object)) return;
            if (isInsideIcon(e.getPoint(), object)) {
              label.getLabel().setIcon(wrapIcon(object, Color.black));
            } else {
              label.getLabel().setIcon(wrapIcon(object, Color.gray));
            }
            label.repaint();
          }
        });
        label.setFont(UIUtil.getLabelFont());
        label.getLabel().setIcon(wrapIcon(object, Color.gray));
        label.getColoredComponent().append(getPresentableText(object), getTextAttributes(object, false));
        clearBorder(label.getColoredComponent());
        label.getLabel().setOpaque(false);
        label.getColoredComponent().setOpaque(true);
        label.setBackground(UIUtil.getListBackground());
        myList.add(label);
        installActions(index);
        index ++;
      }
    }
    paintComponent();
  }

  private boolean isInsideIcon(final Point point, final Object object) {
    //noinspection ConstantConditions
    final int height = getIcon(object).getIconHeight();
    return point.x > 0 && point.x < 10 && point.y > height / 2 - 4 && point.y < height/ 2 + 4;
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
    final SimpleColoredComponent label = myList.get(index).getColoredComponent();
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
        cancelPopup();
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
    final MyCompositeLabel toBeContLabel = new MyCompositeLabel();
    toBeContLabel.getColoredComponent().setFont(UIUtil.getLabelFont());
    toBeContLabel.getColoredComponent().append("...", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    clearBorder(toBeContLabel.getColoredComponent());
    final int additionalWidth = toBeContLabel.getPreferredSize().width;
    int wholeWidth = getWidth() - 2 * myLeftButton.getWidth();
    if (mySelectedIndex != -1) {
      myScrollablePanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
      if (myFirstIndex > 0){
        final MyCompositeLabel preList = new MyCompositeLabel();
        preList.getColoredComponent().setFont(UIUtil.getLabelFont());
        preList.getColoredComponent().append("...", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        clearBorder(preList.getColoredComponent());
        myScrollablePanel.add(preList, gc);
        wholeWidth -= additionalWidth;
      }
      for (int i = 0; i < myList.size(); i++) {
        final MyCompositeLabel linkLabel = myList.get(i);
        final int labelWidth = linkLabel.getPreferredSize().width;
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
      final MyCompositeLabel preselected = myList.get(myModel.size() - 1);
      installDottedBorder(preselected.getColoredComponent());
      for (int i = myModel.size() - 1; i >= 0; i--){
        final MyCompositeLabel linkLabel = myList.get(i);
        width += linkLabel.getPreferredSize().width;
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

  private Icon wrapIcon(final Object object, final Color color){
    final Icon icon = getIcon(object);
    if (icon == null || !hasChildren(object)) return icon;
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(icon, 0);
    Icon plusIcon = new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(color);
        g.drawRect(x + 1, y - 4, 8, 8);
        g.drawLine(x + 3, y, x + 7, y);
        if (mySelectedIndex != myModel.indexOf(object) || !isExpanded(object)) g.drawLine(x + 5, y - 2, x + 5, y + 2);
      }

      public int getIconWidth() {
        return 10;
      }

      public int getIconHeight() {
        return 8;
      }
    };
    layeredIcon.setIcon(plusIcon, 1, -12, icon.getIconHeight()/2) ;
    return layeredIcon;
  }

  private void paintBorder(){
    final MyCompositeLabel focusedLabel = myList.get(mySelectedIndex);
    final Object o = myModel.get(mySelectedIndex);
    focusedLabel.getLabel().setIcon(wrapIcon(o, Color.gray));
    final SimpleColoredComponent simpleColoredComponent = focusedLabel.getColoredComponent();
    simpleColoredComponent.clear();
    simpleColoredComponent.append(getPresentableText(o), getTextAttributes(o, true));
    simpleColoredComponent.setBackground(UIUtil.getListSelectionBackground());
    simpleColoredComponent.setForeground(UIUtil.getListSelectionForeground());
    installDottedBorder(simpleColoredComponent);
  }

  private void clearBorder(){
    if (myModel.isEmpty()) return;
    final int index = mySelectedIndex != -1 ? mySelectedIndex : myModel.size() - 1;
    final MyCompositeLabel focusLostLabel = myList.get(index);
    final Object o = myModel.get(index);
    focusLostLabel.getLabel().setIcon(wrapIcon(o, Color.gray));
    final SimpleColoredComponent simpleColoredComponent = focusLostLabel.getColoredComponent();
    simpleColoredComponent.clear();
    simpleColoredComponent.append(getPresentableText(o), getTextAttributes(o, false));
    simpleColoredComponent.setBackground(UIUtil.getListBackground());
    simpleColoredComponent.setForeground(UIUtil.getListForeground());
    clearBorder(simpleColoredComponent);
  }

  protected void shiftFocus(int direction){
    clearBorder();
    mySelectedIndex = getIndexByMode(mySelectedIndex + direction);
    paintBorder();
    while (!myList.get(mySelectedIndex).isShowing()){
      scrollToVisible(direction);
    }
  }

  protected boolean hasChildren(Object object){
    return false;
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

  protected boolean isExpanded(Object object){
    return false;
  }

  protected ListPopup getPopup() {
    return null;
  }

  protected void cancelPopup() {

  }

  protected static class MyCompositeLabel extends JPanel {
    private JLabel myLabel = new JLabel();
    private SimpleColoredComponent myColoredComponent = new SimpleColoredComponent();

    public MyCompositeLabel() {
      super(new GridBagLayout());
      final GridBagConstraints gc =
        new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0);
      add(myLabel, gc);
      gc.insets.left = 1;
      add(myColoredComponent, gc);
    }

    public JLabel getLabel() {
      return myLabel;
    }

    public SimpleColoredComponent getColoredComponent() {
      return myColoredComponent;
    }
  }
}
