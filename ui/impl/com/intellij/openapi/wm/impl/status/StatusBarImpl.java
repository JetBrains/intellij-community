package com.intellij.openapi.wm.impl.status;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.ui.UIBundle;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.AsyncProcessIcon;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class StatusBarImpl extends JPanel implements StatusBarEx {

  protected final TextPanel myInfoPanel = new TextPanel(new String[]{"#"},true);
  protected final PositionPanel myPositionPanel = new PositionPanel();
  protected final ToggleReadOnlyAttributePanel myToggleReadOnlyAttributePanel = new ToggleReadOnlyAttributePanel(this);
  protected final MemoryUsagePanel myMemoryUsagePanel = new MemoryUsagePanel();
  protected final TextPanel myStatusPanel = new TextPanel(new String[]{UIBundle.message("status.bar.insert.status.text"),
    UIBundle.message("status.bar.overwrite.status.text")},false);
  protected final TogglePopupHintsPanel myEditorHighlightingPanel;
  protected final IdeMessagePanel myMessagePanel = new IdeMessagePanel(MessagePool.getInstance());
  private final JPanel myCustomIndicationsPanel = new JPanel(new GridBagLayout());
  protected String myInfo = "";
  private final Icon myLockedIcon = IconLoader.getIcon("/nodes/lockedSingle.png");
  private final Icon myUnlockedIcon = myLockedIcon != null ? new EmptyIcon(myLockedIcon.getIconWidth(), myLockedIcon.getIconHeight()) : null;

  protected final MyUISettingsListener myUISettingsListener;
  protected InfoAndProgressPanel myInfoAndProgressPanel;

  private UISettings myUISettings;
  private AsyncProcessIcon myRefreshIcon;

  public StatusBarImpl(UISettings uiSettings) {
    super();
    myEditorHighlightingPanel = new TogglePopupHintsPanel();
    myUISettings = uiSettings;
    constructUI();

    myUISettingsListener=new MyUISettingsListener();
  }

  protected void constructUI() {
    setLayout(new BorderLayout());
    setOpaque(true);

    final Border lineBorder = new SeparatorBorder.Left();
    final Border emptyBorder = BorderFactory.createEmptyBorder(0, 2, 0, 2);
    final Border separatorLeft = BorderFactory.createCompoundBorder(emptyBorder, lineBorder);

    myRefreshIcon = new AsyncProcessIcon("Refreshing filesystem");
    add(myRefreshIcon, BorderLayout.WEST);
    myRefreshIcon.setVisible(false);

    myInfoPanel.setBorder(emptyBorder);
    myInfoPanel.setOpaque(false);

    myInfoAndProgressPanel = new InfoAndProgressPanel(this);

    add(myInfoAndProgressPanel, BorderLayout.CENTER);

    final GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;

    final JPanel rightPanel = new JPanel(new GridBagLayout());
    rightPanel.setOpaque(false);

    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;

    myPositionPanel.setBorder(separatorLeft);
    myPositionPanel.setOpaque(false);
    rightPanel.add(myPositionPanel, gbConstraints);

    myToggleReadOnlyAttributePanel.setBorder(separatorLeft);
    myToggleReadOnlyAttributePanel.setOpaque(false);
    setWriteStatus(false);
    rightPanel.add(myToggleReadOnlyAttributePanel, gbConstraints);

    myStatusPanel.setBorder(separatorLeft);
    myStatusPanel.setOpaque(false);
    rightPanel.add(myStatusPanel, gbConstraints);

    myEditorHighlightingPanel.setBorder(separatorLeft);
    myEditorHighlightingPanel.setOpaque(false);
    rightPanel.add(myEditorHighlightingPanel, gbConstraints);

    myCustomIndicationsPanel.setVisible(false); // Will become visible when any of indications really adds.
    myCustomIndicationsPanel.setBorder(separatorLeft);
    myCustomIndicationsPanel.setOpaque(false);
    rightPanel.add(myCustomIndicationsPanel, gbConstraints);

    myMessagePanel.setOpaque(false);
    myMemoryUsagePanel.setBorder(separatorLeft);
    rightPanel.add(myMessagePanel, gbConstraints);

    myMemoryUsagePanel.setBorder(separatorLeft);

    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    rightPanel.add(myMemoryUsagePanel, gbConstraints);

    add(rightPanel, BorderLayout.EAST);

    setBorder(new EmptyBorder(2, 0, 1, 0));
  }

  public void add(final ProgressIndicatorEx indicator, TaskInfo info) {
    myInfoAndProgressPanel.addProgress(indicator, info);
  }

  public void setProcessWindowOpen(final boolean open) {
    myInfoAndProgressPanel.setProcessWindowOpen(open);
  }

  public boolean isProcessWindowOpen() {
    return myInfoAndProgressPanel.isProcessWindowOpen();
  }

  protected final void paintComponent(final Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());

    final Color dark = getBackground().darker();

    g.setColor(dark);
    g.drawLine(0, 0, getWidth(), 0);

    final Color lighter = new Color(dark.getRed(), dark.getGreen(), dark.getBlue(), 75);
    g.setColor(lighter);
    g.drawLine(0, 1, getWidth(), 1);

    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
  }

  public final void addNotify() {
    super.addNotify();
    setMemoryIndicatorVisible(myUISettings.SHOW_MEMORY_INDICATOR);
    myUISettings.addUISettingsListener(myUISettingsListener);
  }

  public final void removeNotify() {
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  public final void setInfo(String s) {
    myInfo = s;
    if (s == null){
      s = " ";
    }
    myInfoPanel.setText(s);
  }

  public void fireNotificationPopup(JComponent content, final Color backgroundColor) {
    new NotificationPopup(this, content, backgroundColor);
  }

  public final String getInfo() {
    return myInfo;
  }

  public final void setPosition(String s) {
    if (s == null){
      s = " ";
    }
    myPositionPanel.setText(s);
  }

  public final void setStatus(String s) {
    if (s == null){
      s = " ";
    }
    myStatusPanel.setText(s);
  }

  public final void addCustomIndicationComponent(JComponent c) {
    final GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.insets = new Insets(0, 0, 0, 2);

    myCustomIndicationsPanel.setVisible(true);
    myCustomIndicationsPanel.add(c, gbConstraints);
  }

  public void removeCustomIndicationComponent(final JComponent component) {
    myCustomIndicationsPanel.remove(component);
    if (myCustomIndicationsPanel.getComponentCount() == 0) {
      myCustomIndicationsPanel.setVisible(false);
    }
  }

  public final void setStatusEnabled(final boolean enabled) {
    myStatusPanel.setEnabled(enabled);
  }

  public final void setWriteStatus(final boolean locked) {
    myToggleReadOnlyAttributePanel.setIcon(
      locked ? myLockedIcon : myUnlockedIcon
    );
  }

  /**
   * Clears all sections in status bar
   */
  public final void clear(){
    setStatus(null);
    setStatusEnabled(false);
    setWriteStatus(false);
    setPosition(null);
    updateEditorHighlightingStatus(true);
  }

  public final void updateEditorHighlightingStatus(final boolean isClear) {
    myEditorHighlightingPanel.updateStatus(isClear);
  }

  public void cleanupCustomComponents() {
    myCustomIndicationsPanel.removeAll();
  }

  public final Dimension getMinimumSize() {
    final Dimension p = super.getPreferredSize();
    final Dimension m = super.getMinimumSize();
    return new Dimension(m.width, p.height);
  }

  public final Dimension getMaximumSize() {
    final Dimension p = super.getPreferredSize();
    final Dimension m = super.getMaximumSize();
    return new Dimension(m.width, p.height);
  }

  private void setMemoryIndicatorVisible(final boolean state) {
    if (myMemoryUsagePanel != null) {
      myMemoryUsagePanel.setVisible(state);
    }
  }

  public void disposeListeners() {
    myEditorHighlightingPanel.dispose();
  }

  private final class MyUISettingsListener implements UISettingsListener{
    public void uiSettingsChanged(final UISettings uiSettings) {
      setMemoryIndicatorVisible(uiSettings.SHOW_MEMORY_INDICATOR);
    }
  }

  public static class SeparatorBorder implements Border {

    private boolean myLeft;

    public SeparatorBorder(final boolean left) {
      myLeft = left;
    }

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final Color bg = c.getBackground();
      g.setColor(bg != null ? bg.darker() : Color.darkGray);
      final int inset = 1;
      if (myLeft) {
        g.drawLine(0, inset, 0, c.getHeight() - inset - 1);
      } else {
        g.drawLine(c.getWidth() - 1, inset, c.getWidth() - 1, c.getHeight() - inset - 1);
      }
    }

    public Insets getBorderInsets(final Component c) {
      return new Insets(0, 1, 0, 1);
    }

    public boolean isBorderOpaque() {
      return false;
    }


    public static class Left extends SeparatorBorder {
      public Left() {
        super(true);
      }
    }

    public static class Right extends SeparatorBorder {
      public Right() {
        super(false);
      }
    }
  }

  public void startRefreshIndication(String tooltipText) {
    myRefreshIcon.setToolTipText(tooltipText);
    myRefreshIcon.setVisible(true);
  }

  public void stopRefreshIndication() {
    myRefreshIcon.setVisible(false);
  }
}
