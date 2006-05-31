package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.JBPopupImpl;
import gnu.trove.THashMap;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Map;

public class LightweightHint implements Hint, UserDataHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LightweightHint");

  private final JComponent myComponent;
  private JComponent myFocusBackComponent;
  private final Map<Key, Object> myUserMap = new THashMap<Key, Object>(1);
  private final EventListenerList myListenerList = new EventListenerList();
  private MyEscListener myEscListener;
  private JBPopup myPopup;
  private JComponent myParentComponent;
  private boolean myIsRealPopup = false;
  private boolean myForceLightweightPopup = false;
  private boolean mySelectingHint;

  private boolean myForceShowAsPopup = false;
  private String myTitle = null;

  public LightweightHint(final JComponent component) {
    LOG.assertTrue(component != null);
    myComponent = component;
  }

  public void setForceLightweightPopup(final boolean forceLightweightPopup) {
    myForceLightweightPopup = forceLightweightPopup;
  }


  public void setForceShowAsPopup(final boolean forceShowAsPopup) {
    myForceShowAsPopup = forceShowAsPopup;
  }

  public void setTitle(final String title) {
    myTitle = title;
  }

  public boolean isSelectingHint() {
    return mySelectingHint;
  }

  public void setSelectingHint(final boolean selectingHint) {
    mySelectingHint = selectingHint;
  }

  /**
   * Shows the hint in the layered pane. Coordinates <code>x</code> and <code>y</code>
   * are in <code>parentComponent</code> coordinate system. Note that the component
   * appears on 250 layer.
   */
  public void show(final JComponent parentComponent, final int x, final int y, final JComponent focusBackComponent) {
    myParentComponent = parentComponent;
    LOG.assertTrue(myParentComponent != null);

    myFocusBackComponent = focusBackComponent;

    LOG.assertTrue(myParentComponent.isShowing());
    myEscListener = new MyEscListener();
    myComponent.registerKeyboardAction(myEscListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                       JComponent.WHEN_IN_FOCUSED_WINDOW);
    final JLayeredPane layeredPane = parentComponent.getRootPane().getLayeredPane();
    if (!myForceShowAsPopup && (myForceLightweightPopup || fitsLayeredPane(layeredPane, myComponent, new RelativePoint(parentComponent, new Point(x, y))))) {
      final Dimension preferredSize = myComponent.getPreferredSize();
      final Point layeredPanePoint = SwingUtilities.convertPoint(parentComponent, x, y, layeredPane);

      myComponent.setBounds(layeredPanePoint.x, layeredPanePoint.y, preferredSize.width, preferredSize.height);

      layeredPane.add(myComponent, new Integer(250 + layeredPane.getComponentCount()));

      myComponent.validate();
      myComponent.repaint();
    }
    else {
      myIsRealPopup = true;
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myComponent, null)
        .setRequestFocus(false)
        .setResizable(myForceShowAsPopup)
        .setMovable(myForceShowAsPopup)
        .setTitle(myTitle)
        .createPopup();
      myPopup.show(new RelativePoint(myParentComponent, new Point(x, y)));
    }
  }

  private static boolean fitsLayeredPane(JLayeredPane pane, JComponent component, RelativePoint desiredLocation) {
    final Rectangle lpRect = new Rectangle(pane.getLocationOnScreen().x, pane.getLocationOnScreen().y, pane.getWidth(), pane.getHeight());
    Rectangle componentRect = new Rectangle(desiredLocation.getScreenPoint().x,
                                            desiredLocation.getScreenPoint().y,
                                            component.getPreferredSize().width,
                                            component.getPreferredSize().height);
    return lpRect.contains(componentRect);
  }

  private void fireHintHidden() {
    final EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (EventListener listener : listeners) {
      ((HintListener)listener).hintHidden(new EventObject(this));
    }
  }

  /**
   * Sets location of the hint in the layered pane coordinate system.
   */
  public final void setLocation(final int x, final int y) {
    if (myIsRealPopup) {
      if (myPopup == null) return;
      ((JBPopupImpl)myPopup).setLocation(new RelativePoint(myParentComponent, new Point(x, y)));
    }
    else {
      myComponent.setLocation(x, y);
      myComponent.validate();
      myComponent.repaint();
    }
  }

  /**
   * x and y are in layered pane coordinate system
   */
  public final void setBounds(final int x, final int y, final int width, final int height) {
    if (myIsRealPopup) {
      setLocation(x, y);
    }
    else {
      myComponent.setBounds(x, y, width, height);
      myComponent.setLocation(x, y);
      myComponent.validate();
      myComponent.repaint();
    }
  }

  /**
   * @return bounds of hint component in the layered pane.
   */
  public final Rectangle getBounds() {
    return myComponent.getBounds();
  }

  public boolean isVisible() {
    return myIsRealPopup ? myPopup != null : myComponent.isShowing();
  }

  public void hide() {
    if (isVisible()) {
      if (myIsRealPopup) {
        myPopup.cancel();
        myPopup = null;
      }
      else {
        final Rectangle bounds = myComponent.getBounds();
        final JLayeredPane layeredPane = myComponent.getRootPane().getLayeredPane();

        try {
          if(myFocusBackComponent != null){
            LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(myFocusBackComponent);
          }
          layeredPane.remove(myComponent);
        }
        finally {
          LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
        }
        
        layeredPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }
    if (myEscListener != null) {
      myComponent.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    }
    fireHintHidden();
  }

  public final JComponent getComponent() {
    return myComponent;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getUserData(final Key<T> key) {
    return (T)myUserMap.get(key);
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    if (value != null) {
      myUserMap.put(key, value);
    }
    else {
      myUserMap.remove(key);
    }
  }

  public final void addHintListener(final HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  public final void removeHintListener(final HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }

  public String getTitle() {
    return myTitle;
  }

  private final class MyEscListener implements ActionListener {
    public final void actionPerformed(final ActionEvent e) {
      LightweightHint.this.hide();
    }
  }
}
