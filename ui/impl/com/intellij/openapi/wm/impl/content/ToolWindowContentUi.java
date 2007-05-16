package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.TitlePanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.CaptionPanel;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.GraphicsConfig;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener, DataProvider {

  private ContentManager myManager;

  ArrayList<ContentTabLabel> myTabs = new ArrayList<ContentTabLabel>();
  private Map<Content, ContentTabLabel> myContent2Tabs = new HashMap<Content, ContentTabLabel>();

  private JPanel myContent = new JPanel(new BorderLayout());
  ToolWindowImpl myWindow;

  TabbedContentAction.CloseAllAction myCloseAllAction;
  TabbedContentAction.MyNextTabAction myNextTabAction;
  TabbedContentAction.MyPreviousTabAction myPreviousTabAction;


  private BaseLabel myIdLabel = new BaseLabel(this);

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(true);
    setOpaque(false);

    myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 8));
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
        final ContentTabLabel tab = new ContentTabLabel(event.getContent(), ToolWindowContentUi.this);
        myTabs.add(event.getIndex(), tab);
        myContent2Tabs.put(event.getContent(), tab);
        event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
        rebuild();
      }

      public void contentRemoved(final ContentManagerEvent event) {
        final ContentTabLabel tab = myContent2Tabs.get(event.getContent());
        if (tab != null) {
          myTabs.remove(tab);
          myContent2Tabs.remove(event.getContent());
          event.getContent().removePropertyChangeListener(ToolWindowContentUi.this);

          ensureSelectedContentVisible();

          rebuild();
        }
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();

        myContent.revalidate();
        myContent.repaint();
      }
    });

    initMouseListeners(this, ToolWindowContentUi.this);
    update();


    myCloseAllAction = new TabbedContentAction.CloseAllAction(myManager);
    myNextTabAction = new TabbedContentAction.MyNextTabAction(myManager);
    myPreviousTabAction = new TabbedContentAction.MyPreviousTabAction(myManager);
  }

  private void ensureSelectedContentVisible() {
    final Content selected = myManager.getSelectedContent();
    if (selected == null) {
      myContent.removeAll();
      return;
    }

    if (myContent.getComponentCount() == 1) {
      final Component visible = myContent.getComponent(0);
      if (visible == selected.getComponent()) return;
    }

    myContent.removeAll();
    myContent.add(selected.getComponent(), BorderLayout.CENTER);

    myContent.revalidate();
    myContent.repaint();
  }


  private void rebuild() {
    removeAll();

    add(myIdLabel);
    initMouseListeners(myIdLabel, this);

    for (ContentTabLabel each : myTabs) {
      add(each);
      initMouseListeners(each, this);
    }

    update();

    revalidate();
    repaint();
  }

  public void doLayout() {
    int eachX = 0;
    int eachY = 0;

    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);

      ContentTabLabel eachTab = null;
      if (each instanceof ContentTabLabel) {
        eachTab = (ContentTabLabel)each;
      }

      final Dimension eachSize = each.getPreferredSize();
      if (eachX + eachSize.width < getWidth()) {
        each.setBounds(eachX, eachY, eachSize.width, getHeight());
        eachX += eachSize.width;
        if (eachTab != null) {
          eachX++;
        }
      }
      else {
        each.setBounds(eachX, eachY, getWidth() - eachX, getHeight());
        eachX = getWidth();
      }
    }
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (myTabs.size() == 0) return;
    if (myTabs.size() == 1) {
      if (myTabs.get(0).getText() == null || myTabs.get(0).getText().trim().length() == 0) return;
    }

    final Graphics2D g2d = (Graphics2D)g;

    for (ContentTabLabel each : myTabs) {
      final Shape shape = getShapeFor(each);
      final Rectangle bounds = each.getBounds();
      boolean fill = true;
      if (myWindow.isActive()) {
        Color from;
        Color to;
        if (each.isSelected()) {
          from = new Color(90, 133, 215);
          to = new Color(33, 87, 138);
          g2d.setPaint(new GradientPaint(bounds.x, bounds.y, from, bounds.x, (float)bounds.getMaxY(), to));
        } else {
          from = new Color(90, 133, 215);
          to = new Color(33, 87, 138);
          fill = false;
          g2d.setPaint(new GradientPaint(bounds.x, bounds.y, from, bounds.x, (float)bounds.getMaxY(), to));
        }
      } else {
        g2d.setPaint(
          new GradientPaint(bounds.x, bounds.y, new Color(152, 143, 134), bounds.x, (float)bounds.getMaxY(), new Color(165, 157, 149)));
      }

      if (fill) {
        g2d.fill(shape);
      }
    }
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);
    if (myTabs.size() == 0) return;
    if (myTabs.size() == 1) {
      if (myTabs.get(0).getText() == null || myTabs.get(0).getText().trim().length() == 0) return;
    }

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    final Graphics2D g2d = (Graphics2D)g;

    for (ContentTabLabel each : myTabs) {
      if (!myManager.isSelected(each.myContent)) {
        final Shape shape = getShapeFor(each);
        g.setColor(new Color(250, 250, 250, 55));
        g2d.fill(shape);
      }
    }

    final Color edges = myWindow.isActive() ? new Color(38, 63, 106) : new Color(130, 120, 111);
    g2d.setColor(edges);
    for (int i = 0; i < myTabs.size(); i++) {
      ContentTabLabel each = myTabs.get(i);
      final boolean first = i == 0;
      final boolean last = i == myTabs.size() - 1;

      if (first) {
        final GeneralPath open = new GeneralPath();
        final Rectangle b = each.getBounds();
        b.x--;
        drawOpenEndge(b, open);
        g2d.draw(open);
      }
      if (last) {
        final GeneralPath close = new GeneralPath();
        final Rectangle b = each.getBounds();
        drawCloseEdge(b, close);
        g2d.draw(close);
      }
      else if (!last) {
        final Rectangle b = each.getBounds();
        g2d.drawLine((int)b.getMaxX(), b.y, (int)b.getMaxX(), (int)b.getMaxY());
      }
    }

    c.restore();
  }

  private Shape getShapeFor(ContentTabLabel label) {
    final Rectangle bounds = label.getBounds();

    final int index = myTabs.indexOf(label);
    boolean first = index == 0;
    boolean last = index == myTabs.size() - 1;

    final GeneralPath shape = new GeneralPath();

    if (first) {
      drawOpenEndge(bounds, shape);
    }
    else {
      shape.moveTo(bounds.x, bounds.y);
      shape.lineTo(bounds.x, (float)bounds.getMaxY());
    }

    if (last) {
      shape.lineTo((float)bounds.getMaxX(), (float)bounds.getMaxY());
      drawCloseEdge(bounds, shape);
    }
    else {
      shape.lineTo((float)bounds.getMaxX(), (float)bounds.getMaxY());
      shape.lineTo((float)bounds.getMaxX(), bounds.y);
    }

    shape.closePath();

    return shape;
  }

  private int getArc() {
    return 3;
  }

  private void drawCloseEdge(final Rectangle bounds, final GeneralPath shape) {
    final double startX = bounds.getMaxX();
    final double startY = bounds.getMaxY();
    final Point2D current = shape.getCurrentPoint();
    if (current == null || (current.getX() != startX && current.getY() != startY)) {
      shape.moveTo((float)startX, (float)startY);
    }
    shape.lineTo((float)bounds.getMaxX(), (float)bounds.getMaxY() - getArc());
    shape.lineTo((float)bounds.getMaxX(), (float)bounds.y + getArc());
    shape.lineTo((float)bounds.getMaxX() - getArc(), (float)bounds.getY());
  }

  private void drawOpenEndge(final Rectangle bounds, final GeneralPath shape) {
    shape.moveTo((float)bounds.getX() + getArc(), (float)bounds.getY());
    shape.lineTo((float)bounds.getX(), (float)bounds.getY() + getArc());
    //shape.lineTo((float)bounds.getX(), (float)(bounds.getMaxY() - getArc()));
    //shape.lineTo((float)bounds.getX() + getArc(), (float)bounds.getMaxY());
    shape.lineTo((float)bounds.getX(), (float)bounds.getMaxY());
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
    update();
  }

  private void update() {
    myIdLabel.setText(myWindow.getId());
    for (ContentTabLabel each : myTabs) {
      each.update();
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

  static void initMouseListeners(final JComponent c, final ToolWindowContentUi ui) {
    if (c.getClientProperty(ui) != null) return;


    final Point[] myLastPoint = new Point[1];

    c.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        if (myLastPoint[0] == null) return;

        final Window window = SwingUtilities.windowForComponent(c);

        if (window instanceof IdeFrame) return;

        final Rectangle oldBounds = window.getBounds();
        final Point newPoint = e.getPoint();
        SwingUtilities.convertPointToScreen(newPoint, c);
        final Point offset = new Point(newPoint.x - myLastPoint[0].x, newPoint.y - myLastPoint[0].y);
        window.setLocation(oldBounds.x + offset.x, oldBounds.y + offset.y);
        myLastPoint[0] = newPoint;
      }
    });

    c.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        myLastPoint[0] = e.getPoint();
        SwingUtilities.convertPointToScreen(myLastPoint[0], c);
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e)) {
            ui.processHide(e);
          }
          else {
            ui.myWindow.fireActivated();
          }
        }
      }
    });


    final DefaultActionGroup contentGroup = new DefaultActionGroup();
    if (c instanceof ContentTabLabel) {
      final Content content = ((ContentTabLabel)c).myContent;
      contentGroup.add(new TabbedContentAction.CloseAction(content));
      contentGroup.add(ui.myCloseAllAction);
      contentGroup.add(new TabbedContentAction.CloseAllButThisAction(content));
      contentGroup.addSeparator();
      if (content.isPinnable()) {
        contentGroup.add(new TabbedContentAction.MyPinTabAction(content));
        contentGroup.addSeparator();
      }

      contentGroup.add(ui.myNextTabAction);
      contentGroup.add(ui.myPreviousTabAction);
      contentGroup.addSeparator();
    }

    c.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(contentGroup);

        final ActionGroup windowPopup = ui.myWindow.getPopupGroup();
        if (windowPopup != null) {
          group.addAll(windowPopup);
        }

        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    c.putClientProperty(ui, Boolean.TRUE);
  }

  private void processHide(final MouseEvent e) {
    final Component c = e.getComponent();
    if (c instanceof ContentTabLabel) {
      final ContentTabLabel tab = (ContentTabLabel)c;
      if (myManager.canCloseContents() && tab.myContent.isCloseable()) {
        myManager.removeContent(tab.myContent);
      }
    }
    else {
      if (e.isControlDown()) {
        myWindow.fireHiddenSide();
      }
      else {
        myWindow.fireHidden();
      }
    }
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(DataKeys.TOOL_WINDOW.getName())) return myWindow;

    return null;
  }

}
