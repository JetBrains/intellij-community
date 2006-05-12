package com.intellij.openapi.wm.impl.status;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.EdgeBorder;
import com.intellij.ui.UIBundle;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;

// Made non-final for Fabrique
public class StatusBarImpl extends JPanel implements StatusBarEx {

// Made protected for Fabrique
  protected final TextPanel myInfoPanel = new TextPanel(new String[]{"#"},true);
  protected final PositionPanel myPositionPanel = new PositionPanel();
  protected final ToggleReadOnlyAttributePanel myToggleReadOnlyAttributePanel = new ToggleReadOnlyAttributePanel();
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
  protected JPanel myInfoAndButtonPanel;
  protected TextPanel percentageLabel;
  protected boolean progressAdded;
  protected int lastPercentage;
  protected JButton cancelButton;
  private UISettings myUISettings;

  public StatusBarImpl(UISettings uiSettings) {
    super(new GridBagLayout());
    myEditorHighlightingPanel = new TogglePopupHintsPanel();
    myUISettings = uiSettings;
    constructUI();

    myUISettingsListener=new MyUISettingsListener();

  }

// Made protected for Fabrique
  protected void constructUI() {
    final Border lineBorder = new EdgeBorder(EdgeBorder.EDGE_RIGHT);
    final Border emptyBorder = BorderFactory.createEmptyBorder(3, 2, 2, 2);
    final Border compoundBorder = BorderFactory.createCompoundBorder(emptyBorder, lineBorder);

    final GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    myInfoPanel.setBorder(emptyBorder);
    myInfoPanel.setOpaque(false);
    percentageLabel = new TextPanel(new String[]{"###"},false);
    percentageLabel.setBorder(emptyBorder);

    myInfoAndButtonPanel = new JPanel(new BorderLayout());
    myInfoAndButtonPanel.setBorder(compoundBorder);
    myInfoAndButtonPanel.setOpaque(false);
    myInfoAndButtonPanel.add(myInfoPanel, BorderLayout.CENTER);

    add(myInfoAndButtonPanel, gbConstraints);

    final JPanel rightPanel = new JPanel(new GridBagLayout());
    rightPanel.setOpaque(false);
    gbConstraints.weightx = 0;
    add(rightPanel, gbConstraints);

    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;

    myPositionPanel.setBorder(compoundBorder);
    myPositionPanel.setOpaque(false);
    rightPanel.add(myPositionPanel, gbConstraints);

    myToggleReadOnlyAttributePanel.setBorder(compoundBorder);
    myToggleReadOnlyAttributePanel.setOpaque(false);
    setWriteStatus(false);
    rightPanel.add(myToggleReadOnlyAttributePanel, gbConstraints);

    myStatusPanel.setBorder(compoundBorder);
    myStatusPanel.setOpaque(false);
    rightPanel.add(myStatusPanel, gbConstraints);

    myEditorHighlightingPanel.setBorder(compoundBorder);
    myEditorHighlightingPanel.setOpaque(false);
    rightPanel.add(myEditorHighlightingPanel, gbConstraints);

    myCustomIndicationsPanel.setVisible(false); // Will become visible when any of indications really adds.
    myCustomIndicationsPanel.setBorder(compoundBorder);
    myCustomIndicationsPanel.setOpaque(false);
    rightPanel.add(myCustomIndicationsPanel, gbConstraints);

    myMessagePanel.setOpaque(false);
    rightPanel.add(myMessagePanel, gbConstraints);

    //  myMemoryUsagePanel.setOpaque(false);
    myMemoryUsagePanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    rightPanel.add(myMemoryUsagePanel, gbConstraints);
  }

  public final void addProgress() {
    lastPercentage = -1;
    progressAdded = true;
    myInfoAndButtonPanel.add(percentageLabel, BorderLayout.WEST);

    myInfoAndButtonPanel.validate();
    repaint();
  }

  public final void setProgressValue(final int progress) {
    if (lastPercentage!=progress) {
      if (progressAdded) {
        percentageLabel.setText(progress+"%");
      }
      lastPercentage=progress;
    }
  }

  public final void hideProgress() {
    myInfoAndButtonPanel.removeAll();
    myInfoAndButtonPanel.add(myInfoPanel, BorderLayout.CENTER);
    myInfoAndButtonPanel.validate();
    repaint();

    progressAdded = false;
  }

  protected final void paintComponent(final Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());

    GradientPaint paint = new GradientPaint(getWidth()/2, 0, getBackground().darker(), getWidth()/2, getHeight()/10, getBackground());
    final Graphics2D g2 = (Graphics2D) g;
    g2.setPaint(paint);
    g.fillRect(0, 0, getWidth(), getHeight()/10);

    paint = new GradientPaint(getWidth()/2, getHeight() - getHeight()/7, getBackground(), getWidth()/2, getHeight() - 1, getBackground().darker());
    g2.setPaint(paint);
    g.fillRect(0, getHeight() - getHeight()/7, getWidth(), getHeight());
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

    myCustomIndicationsPanel.setVisible(true);
    myCustomIndicationsPanel.add(c, gbConstraints);
  }

  public final void setStatusEnabled(final boolean enabled) {
    myStatusPanel.setEnabled(enabled);
  }

  public final void showCancelButton(@NotNull final Icon icon, @NotNull final ActionListener listener, final String tooltopText) {
    cancelButton = new JButton(icon);
    cancelButton.addActionListener(listener);
    cancelButton.setFocusable(false);

    if (tooltopText != null) {
      cancelButton.setToolTipText(tooltopText);
    }

    myInfoAndButtonPanel.add(cancelButton, BorderLayout.EAST);

    myInfoAndButtonPanel.validate();
    repaint();
  }

  public final void hideCancelButton() {
    hideProgress();
    cancelButton=null;
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
}
