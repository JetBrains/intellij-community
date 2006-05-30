package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.HeavyweightHint;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class QuickFixManager <T extends JComponent>{
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
  private HeavyweightHint myHint;
  private Icon myIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private Rectangle myLastHintBounds;

  public QuickFixManager(@Nullable final GuiEditor editor, @NotNull final T component) {
    myEditor = editor;
    myComponent = component;
    myAlarm = new Alarm();
    myShowHintRequest = new MyShowHintRequest(this);

    (new VisibilityWatcherImpl(this, component)).install(myComponent);
    myComponent.addFocusListener(new FocusListenerImpl(this));

    // Alt+Enter
    new ShowHintAction(this, component);
  }

  public final GuiEditor getEditor(){
    return myEditor;
  }

  public void setEditor(final GuiEditor editor) {
    myEditor = editor;
  }

  final void setHint(@NotNull HeavyweightHint hint){
    myHint = hint;
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

    final HeavyweightHint hint = new HeavyweightHint(lightBulbComponent, false);
    setHint(hint);
    myLastHintBounds = bounds;
    hint.show(myComponent, bounds.x - width, bounds.y, myComponent);
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
    if(myHint == null || !myHint.isVisible()){
      return;
    }
    final ErrorInfo[] errorInfos = getErrorInfos();
    if(!haveFixes(errorInfos)){
      return;
    }

    final ArrayList<QuickFix> fixList = new ArrayList<QuickFix>();
    for(ErrorInfo errorInfo: errorInfos) {
      for (QuickFix fix: errorInfo.myFixes) {
        fixList.add(fix);
      }
    }

    final ListPopup popup = JBPopupFactory.getInstance().createWizardStep(new QuickFixPopupStep(fixList));
    popup.showUnderneathOf(myHint.getComponent());
  }

  private static class QuickFixPopupStep extends BaseListPopupStep<QuickFix> {
    public QuickFixPopupStep(final ArrayList<QuickFix> fixList) {
      super(null, fixList);
    }

    @NotNull
    public String getTextFor(final QuickFix value) {
      return value.getName();
    }

    public PopupStep onChosen(final QuickFix selectedValue, final boolean finalChoice) {
      selectedValue.run();
      return FINAL_CHOICE;
    }

    public boolean hasSubstep(final QuickFix selectedValue) {
      return false;
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