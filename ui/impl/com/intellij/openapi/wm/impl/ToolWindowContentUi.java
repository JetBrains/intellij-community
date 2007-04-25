package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener {
  private ContentManager myManager;


  private ArrayList<ContentTab> myTabs = new ArrayList<ContentTab>();
  private Map<Content, ContentTab> myContent2Tabs = new HashMap<Content, ContentTab>();

  private Wrapper myContent = new Wrapper();
  private ToolWindowImpl myWindow;

  private JLabel myIdLabel = new BaseLabel();

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    setOpaque(false);

    myIdLabel.setForeground(Color.white);
    myIdLabel.setOpaque(false);
    myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
  }

  public JComponent getComponent() {
    return myContent;
  }

  public JComponent getTabComponent() {
    return this;
  }

  public void setManager(final ContentManager manager) {
    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        final ContentTab tab = new ContentTab(event.getContent());
        myTabs.add(event.getIndex(), tab);
        myContent2Tabs.put(event.getContent(), tab);
        event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
        rebuild();
      }

      public void contentRemoved(final ContentManagerEvent event) {
        final ContentTab tab = myContent2Tabs.get(event.getContent());
        if (tab != null) {
          myTabs.remove(tab);
          myContent2Tabs.remove(event.getContent());
          event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
          rebuild();
        }
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        myContent.setContent(event.getContent().getComponent());
        repaint();
      }
    });
  }


  private void rebuild() {
    removeAll();

    add(myIdLabel);

    for (ContentTab each : myTabs) {
      add(each);
    }

    revalidate();
    repaint();
  }

  public void doLayout() {
    int eachX = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      final Dimension eachSize = each.getPreferredSize();
      if (eachX + eachSize.width < getWidth()) {
        each.setBounds(eachX, 0, eachSize.width, getHeight());
        eachX += eachSize.width;
      }
      else {
        each.setBounds(eachX, 0, getWidth() - eachX, getHeight());
        eachX = getWidth();
      }
    }
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      size.height = Math.max(each.getPreferredSize().height, size.height);
    }
    return size;
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    if (myTabs.size() == 1) {
      final ContentTab tab = myTabs.get(0);
      tab.setText("");
      final String displayName = tab.myContent.getDisplayName();
      myIdLabel.setText(myWindow.getId() + ((displayName == null || displayName.length() == 0) ? "" : " - " + displayName));
    }
    else {
      myIdLabel.setText(myWindow.getId());
      for (ContentTab each : myTabs) {
        each.update();
      }
    }

    revalidate();
    repaint();
  }

  public boolean isSingleSelection() {
    return true;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  private class BaseLabel extends JLabel {

    private Point myLastPoint;

    public BaseLabel() {
      setBorder(new EmptyBorder(0, 0, 0, 4));

      setForeground(Color.white);
      setOpaque(false);

      addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseDragged(final MouseEvent e) {
          // 1. myLast point can be null due to bugs in Swing.
          // 2. do not allow drag enclosed window if ToolWindow isn't floating

          if (myLastPoint == null) return;

          final Window window = SwingUtilities.windowForComponent(BaseLabel.this);

          if (window instanceof IdeFrame) return;

          final Rectangle oldBounds = window.getBounds();
          final Point newPoint = e.getPoint();
          SwingUtilities.convertPointToScreen(newPoint, BaseLabel.this);
          final Point offset = new Point(newPoint.x - myLastPoint.x, newPoint.y - myLastPoint.y);
          window.setLocation(oldBounds.x + offset.x, oldBounds.y + offset.y);
          myLastPoint = newPoint;
        }
      });

      addMouseListener(new MouseAdapter() {
        public void mousePressed(final MouseEvent e) {
          myLastPoint = e.getPoint();
          SwingUtilities.convertPointToScreen(myLastPoint, BaseLabel.this);
          if (!e.isPopupTrigger()) {
            myWindow.fireActivated();
          }
        }
      });

      addMouseListener(new PopupHandler() {
        public void invokePopup(final Component comp, final int x, final int y) {
          myWindow.invokePopup(comp, x, y);
        }
      });
    }

    public void updateUI() {
      super.updateUI();
      setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    }
  }

  private class ContentTab extends BaseLabel {

    private Content myContent;

    public ContentTab(final Content content) {
      myContent = content;
      update();
    }

    public void update() {
      setText(myContent.getDisplayName());
    }

    protected void paintComponent(final Graphics g) {
      if (myContent.isSelected() && myTabs.size() > 1) {
        g.setColor(UIUtil.getListSelectionBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
      }

      super.paintComponent(g);
    }

  }
}
