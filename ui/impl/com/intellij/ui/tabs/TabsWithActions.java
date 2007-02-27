package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.GraphicsConfig;
import com.intellij.ui.CaptionPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.jetbrains.annotations.Nullable;

public class TabsWithActions extends JComponent implements PropertyChangeListener {

  private ActionManager myActionManager;
  private List<TabInfo> myInfos = new ArrayList<TabInfo>();

  private TabInfo mySelectedInfo;
  private Map<TabInfo, JLabel> myInfo2Label = new HashMap<TabInfo, JLabel>();
  private Map<TabInfo, JComponent> myInfo2Toolbar = new HashMap<TabInfo, JComponent>();
  private Dimension myHeaderFitSize;
  private Rectangle mySelectedBounds;

  private static final int INNER = 1;

  public TabsWithActions(ActionManager actionManager) {
    myActionManager = actionManager;
  }

  public TabInfo addTab(JComponent component) {
    final TabInfo info = new TabInfo(component);
    info.getChangeSupport().addPropertyChangeListener(this);
    add(component);
    final JLabel label = new JLabel("???");
    label.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (e.getClickCount() == 1) {
          setSelected(info);
        }
      }
    });
    label.setBorder(new EmptyBorder(2, 8, 2, 8));
    myInfo2Label.put(info, label);
    myInfos.add(info);
    add(label);
    update();
    return info;
  }

  public void setSelected(final TabInfo info) {
    mySelectedInfo = info;
    update();
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    final TabInfo tabInfo = (TabInfo)evt.getSource();
    if (TabInfo.ACTION_GROUP.equals(evt.getPropertyName())) {
      final JComponent old = myInfo2Toolbar.get(tabInfo);
      if (old != null) {
        remove(old);
      }
      final JComponent toolbar = createToolbarComponent(tabInfo);
      if (toolbar != null) {
        myInfo2Toolbar.put(tabInfo, toolbar);
        add(toolbar);
      }
    } else if (TabInfo.TEXT.equals(evt.getPropertyName())) {
      myInfo2Label.get(tabInfo).setText(tabInfo.getText());
    } else if (TabInfo.ICON.equals(evt.getPropertyName())) {
      myInfo2Label.get(tabInfo).setIcon(tabInfo.getIcon());
    }

    update();
  }

  private @Nullable
  TabInfo getSelectedInfo() {
    return mySelectedInfo != null ? mySelectedInfo : (myInfos.size() > 0 ? myInfos.get(0) : null);
  }

  protected JComponent createToolbarComponent(final TabInfo tabInfo) {
    if (tabInfo.getGroup() == null) return null;
    return myActionManager.createActionToolbar(tabInfo.getPlace(), tabInfo.getGroup(), true).getComponent();
  }

  public void doLayout() {
    final TabsWithActions.Max max = computeMaxSize();
    myHeaderFitSize = new Dimension(getSize().width, Math.max(max.myLabel.height, max.myToolbar.height));
    Insets insets = getInsets();
    if (insets == null) {
      insets = new Insets(0, 0, 0, 0);
    }
    int currentX = insets.left + getArcSize();
    final TabInfo selected = getSelectedInfo();
    mySelectedBounds = null;
    for (TabInfo eachInfo : myInfos) {
      final JLabel label = myInfo2Label.get(eachInfo);
      final Dimension eachSize = label.getPreferredSize();
      label.setBounds(currentX, insets.top, eachSize.width, myHeaderFitSize.height);
      currentX += eachSize.width;

      final JComponent comp = eachInfo.getComponent();
      if (selected == eachInfo) {
        comp.setBounds(insets.left + INNER,
                       myHeaderFitSize.height + insets.top,
                       getWidth() - insets.left - insets.right - INNER * 2,
                       getHeight() - insets.top - insets.bottom - myHeaderFitSize.height - 1);
        mySelectedBounds = label.getBounds();
      } else {
        comp.setBounds(0, 0, 0, 0);
      }
      final JComponent eachToolbar = myInfo2Toolbar.get(eachInfo);
      if (eachToolbar != null) {
        eachToolbar.setBounds(0, 0, 0, 0);
      }
    }

    final JComponent selectedToolbar = myInfo2Toolbar.get(selected);
    if (selectedToolbar != null) {
      final int toolbarInset = getArcSize() * 2;
      if (currentX + selectedToolbar.getMinimumSize().width + toolbarInset < getWidth()) {
        selectedToolbar.setBounds(currentX + toolbarInset,
                                  insets.top,
                                  getSize().width - currentX - insets.left - toolbarInset,
                                  myHeaderFitSize.height - 1);
      } else {
        selectedToolbar.setBounds(0, 0, 0, 0);
      }
    }
  }

  private int getArcSize() {
    return 4;
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (mySelectedBounds == null) return;

    final GraphicsConfig config = new GraphicsConfig(g);
    config.setAntialiasing(true);

    Graphics2D g2d = (Graphics2D)g;
    final GeneralPath path = new GeneralPath();
    Insets insets = getInsets();
    if (insets == null) {
      insets = new Insets(0, 0, 0, 0);
    }
    final int bottomY = myHeaderFitSize.height + insets.top - 1;
    final int topY = insets.top;
    int leftX = mySelectedBounds.x;
    int rightX = mySelectedBounds.x + mySelectedBounds.width;
    int arc = getArcSize();

    path.moveTo(insets.left, bottomY);
    path.lineTo(leftX - arc, bottomY);
    path.quadTo(leftX, bottomY, leftX, bottomY - arc);
    path.lineTo(leftX, topY + arc);
    path.quadTo(leftX, topY, leftX + arc, topY);
    path.lineTo(rightX - arc, topY);
    path.quadTo(rightX, topY, rightX, topY + arc);
    path.lineTo(rightX, bottomY - arc);
    path.quadTo(rightX, bottomY, rightX + arc, bottomY);
    path.lineTo(getWidth() - insets.right, bottomY);
    path.closePath();

    g2d.setPaint(new GradientPaint(mySelectedBounds.x, topY, CaptionPanel.BND_ACTIVE_COLOR, mySelectedBounds.x, bottomY,
                                   CaptionPanel.CNT_ACTIVE_COLOR));
    g2d.fill(path);
    g2d.setColor(CaptionPanel.CNT_ACTIVE_COLOR.darker());
    g2d.draw(path);

    g2d.drawRect(insets.left, bottomY, getWidth() - insets.left - insets.right - 1, getHeight() - bottomY - insets.bottom - 1);

    config.restore();
  }

  private Max computeMaxSize() {
    Max max = new Max();
    for (TabInfo eachInfo : myInfos) {
      final JLabel label = myInfo2Label.get(eachInfo);
      max.myLabel.height = Math.max(max.myLabel.height, label.getPreferredSize().height);
      max.myLabel.width = Math.max(max.myLabel.width, label.getPreferredSize().width);
      final JComponent toolbar = myInfo2Toolbar.get(eachInfo);
      if (toolbar != null) {
        max.myToolbar.height = Math.max(max.myToolbar.height, toolbar.getPreferredSize().height);
        max.myToolbar.width = Math.max(max.myToolbar.width, toolbar.getPreferredSize().width);
      }
    }

    max.myToolbar.height++;
    
    return max;
  }

  public int getTabCount() {
    return myInfos.size();
  }

  private class Max {
    Dimension myLabel = new Dimension();
    Dimension myToolbar = new Dimension();
  }

  private void update() {
    revalidate();
    repaint();
  }

  ActionManager getActionManager() {
    return myActionManager;
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final int[] count = new int[1];
    final TabsWithActions tabs = new TabsWithActions(null) {
      protected JComponent createToolbarComponent(final TabInfo tabInfo) {
        final JLabel jLabel = new JLabel("X" + (++count[0]));
        jLabel.setBorder(new LineBorder(Color.red));
        return jLabel;
      }
    };
    frame.getContentPane().add(tabs);

    tabs.addTab(new JTree()).setText("Tree").setActions(new DefaultActionGroup(), null).setIcon(IconLoader.getIcon("/debugger/frame.png"));
    tabs.addTab(new JTree()).setText("Tree2");
    tabs.addTab(new JTable()).setText("Table").setActions(new DefaultActionGroup(), null);

    //tabs.setBorder(new EmptyBorder(6, 6, 6, 6));

    frame.setBounds(200, 200, 300, 200);
    frame.show();
  }
}
