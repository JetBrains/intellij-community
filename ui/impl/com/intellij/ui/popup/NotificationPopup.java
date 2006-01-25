/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.CaptionPanel;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author max
 */
public class NotificationPopup {
  private JComponent myContent;
  private static final Dimension myPreferredContentSize = new Dimension(300, 100);
  private JBPopup myPopup;
  private int myTimerTick;
  private Color myBackgroud;
  private final static int FADE_IN_TICKS = 60;
  private final static int SHOW_TIME_TICKS = FADE_IN_TICKS + 300;
  private final static int FADE_OUT_TICKS = SHOW_TIME_TICKS + 60;

  private final Timer myFadeInTimer = new Timer(10, new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      Window popupWindow = SwingUtilities.windowForComponent(myContent);
      if (popupWindow != null) {
        myTimerTick++;
        if (myTimerTick < FADE_IN_TICKS) {
          popupWindow.setLocation(popupWindow.getLocation().x,  popupWindow.getLocation().y - 2);
        }
        else if (myTimerTick > FADE_OUT_TICKS) {
          myPopup.cancel();
          myFadeInTimer.stop();
        }
        else if (myTimerTick > SHOW_TIME_TICKS) {
          popupWindow.setLocation(popupWindow.getLocation().x,  popupWindow.getLocation().y + 2);
        }
      }
    }
  });

  public NotificationPopup(final JComponent owner, final JComponent content, Color backgroud) {
    myBackgroud = backgroud;
    myContent = new ContentComponent(content);
    myPopup = JBPopupFactory.getInstance().createHeavyweightComponentPopup(myContent, null, false);
    final Point p = RelativePoint.getSouthEastOf(owner).getScreenPoint();
    Rectangle screen = ScreenUtil.getScreenRectangle(p.x, p.y);

    final Point initial = new Point(screen.x + screen.width - myContent.getPreferredSize().width - 50,
                                    screen.y + screen.height - 5);

    ((JBPopupImpl)myPopup).doNotFitToScreen();

    myPopup.showInScreenCoordinates(owner, initial);

    myFadeInTimer.setRepeats(true);
    myFadeInTimer.start();
  }

  private class ContentComponent extends JPanel {
    private MouseAdapter myEntranceListener;
    private CaptionPanel myCaption;
    private Point myLastClickedOffset;

    public ContentComponent(JComponent content) {
      super(new BorderLayout());
      myCaption = new CaptionPanel();
      add(myCaption, BorderLayout.NORTH);
      add(content, BorderLayout.CENTER);
      setBackground(myBackgroud);

      myEntranceListener = new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
          if (myFadeInTimer.isRunning()) {
            myFadeInTimer.stop();
            myCaption.setActive(true);
          }
        }

        public void mouseExited(MouseEvent e) {
          if (!myFadeInTimer.isRunning()) {
            myFadeInTimer.start();
            myCaption.setActive(false);
          }
        }
      };

      myCaption.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          final Point titleOffset = RelativePoint.getNorthWestOf(myCaption).getScreenPoint();
          myLastClickedOffset = new RelativePoint(e).getScreenPoint();
          myLastClickedOffset.x -= titleOffset.x;
          myLastClickedOffset.y -= titleOffset.y;
        }
      });

      myCaption.addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseDragged(MouseEvent e) {
          final Point draggedTo = new RelativePoint(e).getScreenPoint();
          draggedTo.x -= myLastClickedOffset.x;
          draggedTo.y -= myLastClickedOffset.y;

          final Window wnd = SwingUtilities.getWindowAncestor(myCaption);
          wnd.setLocation(draggedTo);
        }
      });
    }

    @Override
    public void addNotify() {
      super.addNotify();
      addMouseListenerToHierarchy(this, myEntranceListener);
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      removeMouseListenerFromHierarchy(this, myEntranceListener);
    }

    public Dimension getPreferredSize() {
      return myPreferredContentSize;
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);
    }
  }

  private static void addMouseListenerToHierarchy(Component c, MouseListener l) {
    c.addMouseListener(l);
    if (c instanceof Container) {
      final Container container = (Container)c;
      Component[] children = container.getComponents();
      for (Component child : children) {
        addMouseListenerToHierarchy(child, l);
      }
    }
  }

  private static void removeMouseListenerFromHierarchy(Component c, MouseListener l) {
    c.removeMouseListener(l);
    if (c instanceof Container) {
      final Container container = (Container)c;
      Component[] children = container.getComponents();
      for (Component child : children) {
        removeMouseListenerFromHierarchy(child, l);
      }
    }
  }
}
