package com.intellij.uiDesigner.quickFixes;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.LightweightHint;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class QuickFixManager <T extends JComponent>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.quickFixes.QuickFixManager");

  private GuiEditor myEditor;
  /** Component on which hint will be shown */
  protected final T myComponent;
  /**
   * This alarm contains request for showing of hint
   */
  private final Alarm myAlarm;
  /**
   * This request updates visibility of the hint
   */
  private final MyShowHintRequest myShowHintRequest;
  /**
   * My currently visible hint. May be null if there is no visible hint
   */
  private LightweightHint myHint;
  private Icon myIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private Rectangle myLastHintBounds;

  public QuickFixManager(@Nullable final GuiEditor editor, @NotNull final T component, @NotNull final JViewport viewPort) {
    myEditor = editor;
    myComponent = component;
    myAlarm = new Alarm();
    myShowHintRequest = new MyShowHintRequest(this);

    (new VisibilityWatcherImpl(this, component)).install(myComponent);
    myComponent.addFocusListener(new FocusListenerImpl(this));

    // Alt+Enter
    new ShowHintAction(this, component);

    viewPort.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateIntentionHintPosition(viewPort);
      }
    });
  }

  public final GuiEditor getEditor(){
    return myEditor;
  }

  public void setEditor(final GuiEditor editor) {
    myEditor = editor;
  }

  /**
   * @return error info for the current {@link #myComponent} state.
   */
  @NotNull
  protected abstract ErrorInfo[] getErrorInfos();

  /**
   * @return rectangle (in {@link #myComponent} coordinates) that represents
   * area that contains errors. This methods is invoked only if {@link #getErrorInfos()}
   * returned non empty list of error infos. <code>null</code> means that
   * error bounds are not defined.
   */
  @Nullable
  protected abstract Rectangle getErrorBounds();

  public void refreshIntentionHint() {
    if(!myComponent.isShowing() || !IJSwingUtilities.hasFocus(myComponent)){
      hideIntentionHint();
      return;
    }
    if (myHint == null || !myHint.isVisible()) {
      updateIntentionHintVisibility();
    }
    else {
      final ErrorInfo[] errorInfos = getErrorInfos();
      final Rectangle bounds = getErrorBounds();
      if (!haveFixes(errorInfos) || bounds == null || !bounds.equals(myLastHintBounds)) {
        hideIntentionHint();
        updateIntentionHintVisibility();
      }
    }
  }

  /**
   * Adds in timer queue requst for updating visibility of the popup hint
   */
  public final void updateIntentionHintVisibility(){
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myShowHintRequest, 500);
  }

  /**
   * Shows intention hint (light bulb) if it's not visible yet.
   */
  final void showIntentionHint(){
    if(!myComponent.isShowing() || !IJSwingUtilities.hasFocus(myComponent)){
      hideIntentionHint();
      return;
    }

    // 1. Hide previous hint (if any)
    hideIntentionHint();

    // 2. Found error (if any)
    final ErrorInfo[] errorInfos = getErrorInfos();
    if(!haveFixes(errorInfos)) {
      hideIntentionHint();
      return;
    }

    // 3. Determine position where this hint should be shown
    final Rectangle bounds = getErrorBounds();
    if(bounds == null){
      return;
    }

    // 4. Show light bulb to fix this error
    final LightBulbComponentImpl lightBulbComponent;
    final int width;
    try {
      final Robot robot = new Robot();
      final Point locationOnScreen = myComponent.getLocationOnScreen();
      width = myIcon.getIconWidth() + 4;
      final int height = myIcon.getIconHeight() + 4;
      final BufferedImage image = robot.createScreenCapture(
        new Rectangle(
          locationOnScreen.x + bounds.x - width,
          locationOnScreen.y + bounds.y,
          width,
          height
        )
      );
      final Graphics2D g = image.createGraphics();
      try{
        myIcon.paintIcon(myComponent, g, (width - myIcon.getIconWidth())/2, (height - myIcon.getIconHeight())/2);
      }
      finally{
        g.dispose();
      }

      lightBulbComponent = new LightBulbComponentImpl(this, image);
    }
    catch (AWTException ignored) {
      return;
    }

    myHint = new LightweightHint(lightBulbComponent);
    myLastHintBounds = bounds;
    myHint.show(myComponent, bounds.x - myIcon.getIconWidth() - 4, bounds.y, myComponent);
  }

  private void updateIntentionHintPosition(final JViewport viewPort) {
    if (myHint != null && myHint.isVisible()) {
      Rectangle rc = getErrorBounds();
      if (rc != null) {
        myLastHintBounds = rc;
        Rectangle hintRect = new Rectangle(rc.x - myIcon.getIconWidth() - 4, rc.y, myIcon.getIconWidth() + 4, myIcon.getIconHeight() + 4);
        LOG.debug("hintRect=" + hintRect);
        if (getHintClipRect(viewPort).contains(hintRect)) {
          Point location = SwingUtilities.convertPoint(
            myComponent,
            hintRect.getLocation(),
            myComponent.getRootPane().getLayeredPane()
          );
          myHint.setLocation(location.x, location.y);
        }
        else {
          myHint.hide();
        }
      }
    }
  }

  protected Rectangle getHintClipRect(final JViewport viewPort) {
    return viewPort.getViewRect();
  }

  private static boolean haveFixes(final ErrorInfo[] errorInfos) {
    boolean haveFixes = false;
    for(ErrorInfo errorInfo: errorInfos) {
      if (errorInfo.myFixes.length > 0) {
        haveFixes = true;
        break;
      }
    }
    return haveFixes;
  }

  /**
   * Hides currently visible hint (light bulb) .If any.
   */
  public final void hideIntentionHint(){
    myAlarm.cancelAllRequests();
    if(myHint != null && myHint.isVisible()){
      myHint.hide();
      myComponent.paintImmediately(myComponent.getVisibleRect());
    }
  }

  final void showIntentionPopup(){
    LOG.debug("showIntentionPopup()");
    if(myHint == null || !myHint.isVisible()){
      return;
    }
    final ErrorInfo[] errorInfos = getErrorInfos();
    if(!haveFixes(errorInfos)){
      return;
    }

    final ArrayList<ErrorWithFix> fixList = new ArrayList<ErrorWithFix>();
    for(ErrorInfo errorInfo: errorInfos) {
      for (QuickFix fix: errorInfo.myFixes) {
        fixList.add(new ErrorWithFix(errorInfo, fix));
      }
    }

    final ListPopup popup = JBPopupFactory.getInstance().createWizardStep(new QuickFixPopupStep(fixList, true));
    popup.showUnderneathOf(myHint.getComponent());
  }

  private static class ErrorWithFix extends Pair<ErrorInfo, QuickFix> {
    public ErrorWithFix(final ErrorInfo first, final QuickFix second) {
      super(first, second);
    }
  }

  private static class QuickFixPopupStep extends BaseListPopupStep<ErrorWithFix> {
    private final boolean myShowSuppresses;

    public QuickFixPopupStep(final ArrayList<ErrorWithFix> fixList, boolean showSuppresses) {
      super(null, fixList);
      myShowSuppresses = showSuppresses;
    }

    @NotNull
    public String getTextFor(final ErrorWithFix value) {
      return value.second.getName();
    }

    public PopupStep onChosen(final ErrorWithFix selectedValue, final boolean finalChoice) {
      if (finalChoice || !myShowSuppresses) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            selectedValue.second.run();
          }
        });
        return FINAL_CHOICE;
      }
      if (selectedValue.first.getInspectionId() != null && selectedValue.second.getComponent() != null) {
        ArrayList<ErrorWithFix> suppressList = new ArrayList<ErrorWithFix>();
        final SuppressFix suppressFix = new SuppressFix(selectedValue.second.myEditor,
                                                        UIDesignerBundle.message("action.suppress.for.component"),
                                                        selectedValue.first.getInspectionId(), selectedValue.second.myComponent);
        final SuppressFix suppressAllFix = new SuppressFix(selectedValue.second.myEditor,
                                                           UIDesignerBundle.message("action.suppress.for.all.components"),
                                                           selectedValue.first.getInspectionId(), null);
        suppressList.add(new ErrorWithFix(selectedValue.first, suppressFix));
        suppressList.add(new ErrorWithFix(selectedValue.first, suppressAllFix));
        return new QuickFixPopupStep(suppressList, false);
      }
      return FINAL_CHOICE;
    }

    public boolean hasSubstep(final ErrorWithFix selectedValue) {
      return myShowSuppresses && selectedValue.first.getInspectionId() != null && selectedValue.second.getComponent() != null;
    }

    @Override public boolean isAutoSelectionEnabled() {
      return false;
    }
  }

  private static class SuppressFix extends QuickFix {
    private final String myInspectionId;

    public SuppressFix(final GuiEditor editor, final String name, final String inspectionId, final RadComponent component) {
      super(editor, name, component);
      myInspectionId = inspectionId;
    }

    public void run() {
      if (!myEditor.ensureEditable()) return;
      myEditor.getRootContainer().suppressInspection(myInspectionId, myComponent);
      myEditor.refreshAndSave(true);
      DaemonCodeAnalyzer.getInstance(myEditor.getProject()).restart();
    }
  }

  private final class MyShowHintRequest implements Runnable{
    private final QuickFixManager myManager;

    public MyShowHintRequest(final QuickFixManager manager) {
      if(manager == null){
        throw new IllegalArgumentException();
      }
      myManager = manager;
    }

    public void run() {
      myManager.showIntentionHint();
    }
  }
}