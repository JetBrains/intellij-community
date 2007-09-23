/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author max
 */
public class NotificationPopup {
  private JComponent myContent;
  private static final Dimension myPreferredContentSize = new Dimension(300, 100);
  private JBPopup myPopup;
  private int myTimerTick;
  private Color myBackgroud;
  private final boolean myUseDefaultPreferredSize;
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
    this(owner, content, backgroud, true);
  }

  public NotificationPopup(final JComponent owner, final JComponent content, Color backgroud, boolean useDefaultPreferredSize) {
    myBackgroud = backgroud;
    myUseDefaultPreferredSize = useDefaultPreferredSize;
    myContent = new ContentComponent(content);
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null)
      .setForceHeavyweight(true)
      .setRequestFocus(false)
      .setResizable(false)
      .setMovable(true)
      .setLocateWithinScreenBounds(false)
      .setAlpha(0.2f)
      .createPopup();
    final Point p = RelativePoint.getSouthEastOf(owner).getScreenPoint();
    Rectangle screen = ScreenUtil.getScreenRectangle(p.x, p.y);

    final Point initial = new Point(screen.x + screen.width - myContent.getPreferredSize().width - 50,
                                    screen.y + screen.height - 5);

    myPopup.showInScreenCoordinates(owner, initial);

    myFadeInTimer.setRepeats(true);
    myFadeInTimer.start();
  }

  public JBPopup getPopup() {
    return myPopup;
  }

  private class ContentComponent extends JPanel {
    private MouseAdapter myEntranceListener;

    public ContentComponent(JComponent content) {
      super(new BorderLayout());
      add(content, BorderLayout.CENTER);
      setBackground(myBackgroud);

      myEntranceListener = new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
          if (myFadeInTimer.isRunning()) {
            myFadeInTimer.stop();
          }
        }

        public void mouseExited(MouseEvent e) {
          if (!myFadeInTimer.isRunning()) {
            myFadeInTimer.start();
          }
        }
      };
    }

    @Override
    public void addNotify() {
      super.addNotify();
      ListenerUtil.addMouseListener(this, myEntranceListener);
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      ListenerUtil.removeMouseListener(this, myEntranceListener);
    }

    public Dimension getPreferredSize() {
      if (myUseDefaultPreferredSize) {
        return myPreferredContentSize;
      }
      return super.getPreferredSize();
    }   
  }
}
