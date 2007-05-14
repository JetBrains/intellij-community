package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.GraphicsConfig;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.GeneralPath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener, DataProvider {

  private ContentManager myManager;

  private ArrayList<ContentTab> myTabs = new ArrayList<ContentTab>();
  private Map<Content, ContentTab> myContent2Tabs = new HashMap<Content, ContentTab>();

  private JPanel myContent = new JPanel(new BorderLayout());
  private ToolWindowImpl myWindow;

  private TabbedContentAction.CloseAllAction myCloseAllAction;
  private TabbedContentAction.MyNextTabAction myNextTabAction;
  private TabbedContentAction.MyPreviousTabAction myPreviousTabAction;


  private JLabel myIdLabel = new BaseLabel() {
    {
      initMouseListeners(this);
    }

    protected void paintComponent(final Graphics g) {
      final GraphicsConfig config = new GraphicsConfig(g);

      config.setAntialiasing(true);

      final GeneralPath shape = new GeneralPath();
      shape.moveTo(0, 0);
      shape.lineTo(getWidth() - 6, 0);
      shape.lineTo(getWidth(), getHeight());
      shape.lineTo(0, getHeight());
      shape.closePath();


      config.getG().setPaint(new GradientPaint(0, 0, Color.white, 0, getHeight(), myWindow.isActive() ? TitlePanel.ACTIVE_SIDE_BUTTON_BG : TitlePanel.INACTIVE_SIDE_BUTTON_BG));
      config.getG().fill(shape);

      config.restore();

      setForeground(myWindow.isActive() ? Color.black : Color.gray);

      super.paintComponent(g);
    }
  };

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(true);
    setOpaque(false);

    myIdLabel.setOpaque(false);
    myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 6));
    myIdLabel.setFont(UIManager.getFont("Label.font"));    

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

    initMouseListeners(this);
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

    for (ContentTab each : myTabs) {
      add(each);
    }

    update();

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
    update();
  }

  private void update() {
    myIdLabel.setText(myWindow.getId());
    for (ContentTab each : myTabs) {
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

  private void initMouseListeners(final JComponent c) {
    final Point[] myLastPoint = new Point[1];

    c.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        // 1. myLast point can be null due to bugs in Swing.
        // 2. do not allow drag enclosed window if ToolWindow isn't floating

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
            processHide(e);
          } else {
            myWindow.fireActivated();
          }
        }
      }
    });


    final DefaultActionGroup contentGroup = new DefaultActionGroup();
    if (c instanceof ContentTab) {
      final Content content = ((ContentTab)c).myContent;
      contentGroup.add(new TabbedContentAction.CloseAction(content));
      contentGroup.add(myCloseAllAction);
      contentGroup.add(new TabbedContentAction.CloseAllButThisAction(content));
      contentGroup.addSeparator();
      if (content.isPinnable()) {
        contentGroup.add(new TabbedContentAction.MyPinTabAction(content));
        contentGroup.addSeparator();
      }

      contentGroup.add(myNextTabAction);
      contentGroup.add(myPreviousTabAction);
      contentGroup.addSeparator();
    }

    c.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(contentGroup);

        final ActionGroup windowPopup = myWindow.getPopupGroup();
        if (windowPopup != null) {
          group.addAll(windowPopup);
        }

        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    });
  }

  private void processHide(final MouseEvent e) {
    final Component c = e.getComponent();
    if (c instanceof ContentTab) {
      final ContentTab tab = (ContentTab)c;
      if (myManager.canCloseContents() && tab.myContent.isCloseable()) {
        myManager.removeContent(tab.myContent);
      }
    } else {
      if (e.isControlDown()) {
        myWindow.fireHiddenSide();
      } else {
        myWindow.fireHidden();
      }
    }
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(DataKeys.TOOL_WINDOW.getName())) return myWindow;

    return null;
  }

  private class BaseLabel extends JLabel {

    public BaseLabel() {
      setForeground(Color.white);
      setOpaque(false);
    }


    public void updateUI() {
      super.updateUI();
      setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    }
  }

  private class ContentTab extends BaseLabel {

    private Content myContent;
    private BaseButtonBehavior myBehavior;

    private Font myFont;
    private Font myBoldFont;

    private Dimension myPrefSize;

    public ContentTab(final Content content) {
      myContent = content;
      setBorder(new EmptyBorder(0, 8, 0, 8));
      update();

      myBehavior = new BaseButtonBehavior(this) {
        protected void execute() {
          myWindow.getContentManager().setSelectedContent(myContent, true);
        }

        public void setHovered(final boolean hovered) {
          setCursor(hovered && myTabs.size() > 1 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }
      };

      initMouseListeners(this);
    }

    public void updateUI() {
      super.updateUI();
      myFont = null;
      myBoldFont = null;
      myPrefSize = null;
    }

    public Dimension getPreferredSize() {
      if (myPrefSize == null) {
        Font old = getFont();
        setFont(myBoldFont != null ? myBoldFont : old);
        myPrefSize = super.getPreferredSize();
        setFont(old);
      }

      return myPrefSize;
    }

    public void update() {
      setText(myContent.getDisplayName());

      if (myFont == null || myBoldFont == null) {
        myFont = UIManager.getFont("Label.font");
        myBoldFont = myFont.deriveFont(Font.BOLD);
      }


      myPrefSize = null;

      setFont(isSelected() ? myBoldFont : myFont);

      final boolean show = Boolean.TRUE.equals(myContent.getUserData(ToolWindow.SHOW_CONTENT_ICON));
      if (show) {
        final Icon icon = myContent.getIcon();
        setIcon(isSelected() ? icon : IconLoader.getDisabledIcon(icon));
      } else {
        setIcon(null);
      }
    }

    protected void paintComponent(final Graphics g) {
      if (myBehavior.isPressedByMouse() && !isSelected()) {
        g.translate(1, 1);
      }

      if (myWindow.isActive()) {
        setForeground(isSelected() ? Color.white : Color.lightGray);
      } else {
        setForeground(Color.white);        
      }

      super.paintComponent(g);

      g.translate(-1, -1);
    }

    private boolean isSelected() {
      return myWindow.getContentManager().isSelected(myContent);
    }

  }
}
