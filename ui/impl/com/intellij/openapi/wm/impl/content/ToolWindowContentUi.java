package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.TitlePanel;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
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


  private BaseLabel myIdLabel = new BaseLabel(this, false);
  private Rectangle myMoreRect;

  private MoreIcon myMoreIcon = new MoreIcon();

  private ArrayList<ContentTabLabel> myToDrop;
  private JPopupMenu myPopup;
  private PopupMenuListener myPopupListener;

  private static final int MORE_ICON_BORDER = 6;

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(true);
    setOpaque(false);

    setBorder(new EmptyBorder(0, 0, 0, 2));

    myPopupListener = new MyPopupListener();

    new BaseButtonBehavior(this) {
      protected void execute(final MouseEvent e) {
        if (myMoreRect != null && myMoreRect.contains(e.getPoint())) {
          showPopup();
        }
      }
    };
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

    if (myTabs.size() == 0 && myWindow.isToHideOnEmptyContent()) {
      myWindow.hide(null);
    }
  }

  public void doLayout() {
    int eachX = 0;
    int eachY = TitlePanel.STRUT;

    myIdLabel.setBounds(eachX, eachY, myIdLabel.getPreferredSize().width, getHeight());
    eachX += myIdLabel.getPreferredSize().width;
    int tabsStart = eachX;

    if (myManager.getContentCount() == 0) return;

    Content selected = myManager.getSelectedContent();
    if (selected == null) {
      selected = myManager.getContents()[0];
    }

    int requiredWidth = 0;
    ArrayList<ContentTabLabel> toLayout = new ArrayList<ContentTabLabel>();
    myToDrop = new ArrayList<ContentTabLabel>();
    for (ContentTabLabel eachTab : myTabs) {
      final Dimension eachSize = eachTab.getPreferredSize();
      requiredWidth += eachSize.width;
      requiredWidth++;
      toLayout.add(eachTab);
    }


    final int moreRectWidth = myMoreIcon.getIconWidth() + MORE_ICON_BORDER * 2;
    int toFitWidth = getSize().width - eachX;

    final ContentTabLabel selectedTab = myContent2Tabs.get(selected);
    while (true) {
      if (requiredWidth <= toFitWidth) break;
      if (toLayout.size() <= 1) break;

      if (toLayout.get(0) != selectedTab) {
        final ContentTabLabel toDropLabel = toLayout.remove(0);
        requiredWidth -= (toDropLabel.getPreferredSize().width + 1);
        myToDrop.add(toDropLabel);
        if (myToDrop.size() == 1) {
          toFitWidth -= moreRectWidth;
        }
      } else if (toLayout.get(toLayout.size() - 1) != selectedTab) {
        final ContentTabLabel toDropLabel = toLayout.remove(toLayout.size() - 1);
        requiredWidth -= (toDropLabel.getPreferredSize().width + 1);
        myToDrop.add(toDropLabel);
        if (myToDrop.size() == 1) {
          toFitWidth -= moreRectWidth;
        }
      } else {
        break;
      }
    }


    boolean reachedBounds = false;
    myMoreRect = null;
    for (ContentTabLabel each : toLayout) {
      if (isToDrawTabs()) {
        eachY = 0;
      } else {
        eachY = TitlePanel.STRUT;
      }
      final Dimension eachSize = each.getPreferredSize();
        if (eachX + eachSize.width < toFitWidth + tabsStart) {
          each.setBounds(eachX, eachY, eachSize.width, getHeight() - eachY);
          eachX += eachSize.width;
          eachX++;
        } else {
          if (!reachedBounds) {
            final int width = getWidth() - eachX - moreRectWidth;
            each.setBounds(eachX, eachY, width, getHeight() - eachY);
            eachX += width;
            eachX ++;
          } else {
            each.setBounds(0, 0, 0, 0);
          }
          reachedBounds = true;
        }
    }

    for (ContentTabLabel each : myToDrop) {
      each.setBounds(0, 0, 0, 0);
    }

    if (myToDrop.size() > 0) {
      myMoreRect = new Rectangle(eachX + MORE_ICON_BORDER, TitlePanel.STRUT, myMoreIcon.getIconWidth(), getHeight() - TitlePanel.STRUT);
      final int selectedIndex = myManager.getIndexOfContent(myManager.getSelectedContent());
      if (selectedIndex == 0) {
        myMoreIcon.setPaintedIcons(false, true);
      } else if (selectedIndex == myManager.getContentCount() - 1) {
        myMoreIcon.setPaintedIcons(true, false);
      } else {
        myMoreIcon.setPaintedIcons(true, true);
      }
    } else {
      myMoreRect = null;
    }
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (!isToDrawTabs()) return;

    final Graphics2D g2d = (Graphics2D)g;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);


    for (ContentTabLabel each : myTabs) {
      final Shape shape = getShapeFor(each);
      final Rectangle bounds = each.getBounds();
      if (myWindow.isActive()) {
        Color from;
        Color to;
        if (each.isSelected()) {
          from = new Color(90, 133, 215);
          to = new Color(33, 87, 138);
          g2d.setPaint(new GradientPaint(bounds.x, bounds.y, from, bounds.x, (float)bounds.getMaxY(), to));
        }
        else {
          from = new Color(129, 147, 219);
          to = new Color(84, 130, 171);
          g2d.setPaint(new GradientPaint(bounds.x, bounds.y, from, bounds.x, (float)bounds.getMaxY(), to));
        }
      }
      else {
        g2d.setPaint(
          new GradientPaint(bounds.x, bounds.y, new Color(152, 143, 134), bounds.x, (float)bounds.getMaxY(), new Color(165, 157, 149)));
      }

      g2d.fill(shape);
    }

    c.restore();
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);
    if (!isToDrawTabs()) return;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    final Graphics2D g2d = (Graphics2D)g;

    final Color edges = myWindow.isActive() ? new Color(38, 63, 106) : new Color(130, 120, 111);
    g2d.setColor(edges);
    for (int i = 0; i < myTabs.size(); i++) {
      ContentTabLabel each = myTabs.get(i);
      final Shape shape = getShapeFor(each);
      g2d.draw(shape);
    }

    c.restore();

    if (myMoreRect != null) {
      myMoreIcon.paintIcon(this, g, myMoreRect.x, myMoreRect.y);
    }
  }

  private Shape getShapeFor(ContentTabLabel label) {
    final Rectangle bounds = label.getBounds();

    if (bounds.width <= 0 || bounds.height <= 0) return new GeneralPath();

    if (!label.isSelected()) {
      bounds.y += 3;
    }

    bounds.width += 1;

    int arc = 2;

    final GeneralPath path = new GeneralPath();
    path.moveTo(bounds.x, bounds.y + bounds.height);
    path.lineTo(bounds.x, bounds.y + arc);
    path.quadTo(bounds.x, bounds.y, bounds.x + arc, bounds.y);
    path.lineTo(bounds.x + bounds.width - arc, bounds.y);
    path.quadTo(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + arc);
    path.lineTo(bounds.x + bounds.width, bounds.y + bounds.height);
    path.closePath();

    return path;
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
    for (ContentTabLabel each : myTabs) {
      each.update();
    }

    myIdLabel.setText(myWindow.getId());
    myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 8));

    if (myTabs.size() == 1) {
      final String text = myTabs.get(0).getText();
      if (text != null && text.trim().length() > 0) {
        myIdLabel.setText(myIdLabel.getText() + " ");
        myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
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
        myManager.removeContent(tab.myContent, true);
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

  public boolean isToDrawTabs() {
    return myTabs.size() > 1;
  }

  private void showPopup() {
    myPopup = new JPopupMenu();
    myPopup.addPopupMenuListener(myPopupListener);
    for (final ContentTabLabel each : myTabs) {
      final JCheckBoxMenuItem item = new JCheckBoxMenuItem(each.getText());
      if (myManager.isSelected(each.myContent)) {
        item.setSelected(true);
      }
      item.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myManager.setSelectedContent(each.myContent, true);
        }
      });
      myPopup.add(item);
    }
    myPopup.show(ToolWindowContentUi.this, myMoreRect.x, myMoreRect.y);
  }

  public void dispose() {
  }


  private class MyPopupListener implements PopupMenuListener {
    public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
    }

    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
      if (myPopup != null) {
        myPopup.removePopupMenuListener(this);
      }
      myPopup = null;
    }

    public void popupMenuCanceled(final PopupMenuEvent e) {
    }
  }

  private class MoreIcon implements Icon {
    private Icon myLeft = IconLoader.getIcon("/general/comboArrowLeft.png");
    private Icon myRight = IconLoader.getIcon("/general/comboArrowRight.png");

    private int myGap = 2;
    private boolean myLeftPainted;
    private boolean myRightPainted;

    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
      int iconY = myMoreRect.height / 2 - myRight.getIconHeight() / 2;

      if (myLeftPainted && myRightPainted) {
        myLeft.paintIcon(c, g, myMoreRect.x, iconY);
        myRight.paintIcon(c, g, myMoreRect.x + myLeft.getIconWidth() + myGap, iconY);
      } else {
        Icon toPaint = myLeftPainted ? myLeft : (myRightPainted ? myRight : null);
        if (toPaint != null) {
          toPaint.paintIcon(c, g, myMoreRect.x + getIconWidth() / 2 - myGap - 1, iconY);
        }
      }
    }

    private void setPaintedIcons(boolean left, boolean right) {
      myLeftPainted = left;
      myRightPainted = right;
    }

    public int getIconWidth() {
      return myLeft.getIconWidth() + myRight.getIconWidth() + myGap;
    }

    public int getIconHeight() {
      return myLeft.getIconHeight();
    }
  }
}
