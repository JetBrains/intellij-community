package com.intellij.uiDesigner.quickFixes;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.HeavyweightHint;
import com.intellij.ui.ListPopup;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class QuickFixManager <T extends JComponent>{
  private final GuiEditor myEditor;
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

  public QuickFixManager(final GuiEditor editor, final T component) {
    if (editor == null) {
      throw new IllegalArgumentException("editor cannot be null");
    }
    if (component == null) {
      throw new IllegalArgumentException("component cannot be null");
    }
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

  final void setHint(final HeavyweightHint hint){
    if(hint == null){
      throw new IllegalArgumentException();
    }
    myHint = hint;
  }

  /**
   * @return error info for the current {@link #myComponent} state.
   */
  protected abstract ErrorInfo getErrorInfo();

  /**
   * @return rectangle (in {@link #myComponent} coordinates) that represents
   * area that contains errors. This methods is invoked only if {@link #getErrorInfo()}
   * returned non empty list of error infos. <code>null</code> means that
   * error bounds are not defined.
   */
  protected abstract Rectangle getErrorBounds();

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
    final ErrorInfo errorInfo = getErrorInfo();
    if(errorInfo == null || errorInfo.myFixes.length == 0){
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
      final Icon icon = IconLoader.getIcon("/actions/intentionBulb.png");
      final Point locationOnScreen = myComponent.getLocationOnScreen();
      width = icon.getIconWidth() + 4;
      final int height = icon.getIconHeight() + 4;
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
        icon.paintIcon(myComponent, g, (width - icon.getIconWidth())/2, (height - icon.getIconHeight())/2);
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
    hint.show(myComponent, bounds.x - width, bounds.y, myComponent);
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
    final ErrorInfo errorInfo = getErrorInfo();
    if(errorInfo == null || errorInfo.myFixes.length == 0){
      return;
    }

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for(int i = 0; i < errorInfo.myFixes.length; i++){
      actionGroup.add(new QuickFixActionImpl(myEditor, errorInfo.myFixes[i]));
    }

    final ListPopup popup = ActionListPopup.createListPopup(
      null,
      actionGroup,
      DataManager.getInstance().getDataContext(myComponent),
      false,
      true
    );
    final Point locationOnScreen = myHint.getLocationOnScreen();
    popup.show(locationOnScreen.x, locationOnScreen.y + myHint.getPreferredSize().height);
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