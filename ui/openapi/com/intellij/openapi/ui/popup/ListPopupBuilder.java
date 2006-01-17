/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.openapi.ui.popup;

import com.intellij.ui.ListScrollingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
 * @author max
 */
public class ListPopupBuilder {
  private JList myList;
//  private ListCellRenderer myRenderer;
  private String myTitle;
//  private ListModel myListModel;
  private java.util.List<KeyStroke> myAdditionalKeystrokes = new ArrayList<KeyStroke>();
  private Runnable myItemChoosenRunnable;

  private MouseListener myMouseListener;
  private KeyListener myKeyListener;
  private JBPopup myPopup;
  private JComponent myContent;

  public String getTitle() {
    return myTitle;
  }

  public ListPopupBuilder setList(JList list) {
    myList = list;
    return this;
  }

  public ListPopupBuilder setTitle(final String title) {
    myTitle = title;
    return this;
  }

  public ListPopupBuilder addAdditionalChooseKeystroke(KeyStroke keyStroke) {
    if (keyStroke != null) {
      myAdditionalKeystrokes.add(keyStroke);
    }
    return this;
  }

  public ListPopupBuilder setItemChoosenCallback(Runnable runnable) {
    myItemChoosenRunnable = runnable;
    return this;
  }

  public JBPopup createPopup() {
    JPanel contentPane = new JPanel(new BorderLayout());
    if (myTitle != null) {
      JLabel label = new JLabel(" " + myTitle + " ");
      label.setHorizontalAlignment(JLabel.CENTER);
      contentPane.add(label, BorderLayout.NORTH);
    }

    myList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    int modelSize = myList.getModel().getSize();
    if (modelSize >= 20) {
      myList.setVisibleRowCount(20);
    }
    else {
      myList.setVisibleRowCount(0);
    }


    ListScrollingUtil.installActions(myList);

    if (myList.getSelectedIndex() == -1) {
      myList.setSelectedIndex(0);
    }

    myMouseListener = new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed()) {
          closePopup(true);
        }
      }
    };

    myList.addMouseListener(myMouseListener);

    myList.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        Point point = e.getPoint();
        int index = myList.locationToIndex(point);
        myList.setSelectedIndex(index);
      }
    });

    myKeyListener = new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyChar()) {
          closePopup(true);
          e.consume();
        }
      }

      public void keyPressed(KeyEvent e) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
        for (KeyStroke keyStroke2 : myAdditionalKeystrokes) {
          if (keyStroke2.equals(keyStroke)) {
            e.consume();
            closePopup(true);
            return;
          }
        }
      }
    };
    myList.addKeyListener(myKeyListener);

    myList.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        closePopup(false);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    if (myContent == null) {
      contentPane.add(createScrollPane(myList), BorderLayout.CENTER);
    }
    else {
      contentPane.add(myContent);
    }

    myPopup = JBPopupFactory.getInstance().createComponentPopup(contentPane, myList, true);
    return myPopup;
  }

  public void closePopup(boolean shouldPerformAction) {
    myPopup.cancel();
    myList.removeMouseListener(myMouseListener);
    myList.removeKeyListener(myKeyListener);

    if (shouldPerformAction) {
      if (myItemChoosenRunnable != null) {
        myItemChoosenRunnable.run();
      }
    }
  }


  public static JScrollPane createScrollPane(final JList list) {
    int modelSize = list.getModel().getSize();
    JScrollPane scrollPane = new JScrollPane(list);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    if (modelSize > 0 && modelSize <= 20) {
      scrollPane.getViewport().setPreferredSize(list.getPreferredSize());
    }
    return scrollPane;
  }

  public ListPopupBuilder setContentPane(JComponent content) {
    myContent = content;
    return this;
  }
}
