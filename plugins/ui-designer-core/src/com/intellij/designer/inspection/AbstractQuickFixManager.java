/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.inspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.ErrorInfo;
import com.intellij.designer.model.QuickFix;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.impl.VisibilityWatcher;
import com.intellij.ui.ClickListener;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RowIcon;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class AbstractQuickFixManager {
  protected DesignerEditorPanel myDesigner;
  protected final JComponent myComponent;
  private final JViewport myViewPort;
  private final Alarm myAlarm = new Alarm();
  private final Runnable myShowHintRequest;
  private LightweightHint myHint;
  private Rectangle myLastHintBounds;

  public AbstractQuickFixManager(@Nullable DesignerEditorPanel designer, JComponent component, JViewport viewPort) {
    myDesigner = designer;
    myComponent = component;
    myViewPort = viewPort;

    myShowHintRequest = new Runnable() {
      @Override
      public void run() {
        showHint();
      }
    };

    new VisibilityWatcher() {
      @Override
      public void visibilityChanged() {
        if (myComponent.isShowing()) {
          updateHintVisibility();
        }
        else {
          hideHint();
        }
      }
    }.install(component);

    component.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (!e.isTemporary()) {
          updateHintVisibility();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (!(e.isTemporary())) {
          hideHint();
        }
      }
    });

    AnAction showHintAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myDesigner != null) {
          showHint();
          showPopup();
        }
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
      }
    };
    showHintAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(), component);

    viewPort.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateHintPosition();
      }
    });
  }

  public void setDesigner(@Nullable DesignerEditorPanel designer) {
    myDesigner = designer;
  }

  public void update() {
    if (!myComponent.isShowing() || !IJSwingUtilities.hasFocus(myComponent)) {
      hideHint();
    }
    else if (myHint == null || !myHint.isVisible()) {
      updateHintVisibility();
    }
    else {
      Rectangle bounds = getErrorBounds();
      if (!ErrorInfo.haveFixes(getErrorInfos()) || bounds == null || !bounds.equals(myLastHintBounds)) {
        hideHint();
        updateHintVisibility();
      }
    }
  }

  private void showHint() {
    if (!myComponent.isShowing() || !IJSwingUtilities.hasFocus(myComponent)) {
      hideHint();
      return;
    }

    // 1. Hide previous hint (if any)
    hideHint();

    // 2. Found error (if any)
    List<ErrorInfo> infos = getErrorInfos();
    if (!ErrorInfo.haveFixes(infos)) {
      hideHint();
      return;
    }

    boolean error = false;
    for (ErrorInfo errorInfo : infos) {
      if (errorInfo.getLevel() == HighlightDisplayLevel.ERROR) {
        error = true;
        break;
      }
    }

    // 3. Determine position where this hint should be shown
    Rectangle bounds = getErrorBounds();
    if (bounds == null) {
      return;
    }

    // 4. Show light bulb to fix this error
    myHint = new LightweightHint(new InspectionHint(error ? AllIcons.Actions.QuickfixBulb : AllIcons.Actions.IntentionBulb));
    myLastHintBounds = bounds;
    myHint.show(myComponent, bounds.x - AllIcons.Actions.IntentionBulb.getIconWidth() - 4, bounds.y, myComponent, new HintHint(myComponent, bounds.getLocation()));
  }

  private void showPopup() {
    if (myHint == null || !myHint.isVisible()) {
      return;
    }

    List<ErrorInfo> errorInfos = getErrorInfos();
    if (!ErrorInfo.haveFixes(errorInfos)) {
      return;
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new FirstStep(errorInfos));
    popup.showUnderneathOf(myHint.getComponent());
  }

  public final void hideHint() {
    myAlarm.cancelAllRequests();
    if (myHint != null && myHint.isVisible()) {
      myHint.hide();
      myHint = null;
      myComponent.paintImmediately(myComponent.getVisibleRect());
    }
  }

  protected final void updateHintVisibility() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myShowHintRequest, 500);
  }

  private void updateHintPosition() {
    if (myHint != null && myHint.isVisible()) {
      Rectangle rc = getErrorBounds();
      if (rc != null) {
        myLastHintBounds = rc;
        Rectangle hintRect = new Rectangle(rc.x - AllIcons.Actions.IntentionBulb.getIconWidth() - 4, rc.y, AllIcons.Actions.IntentionBulb
                                                                                                             .getIconWidth() + 4, AllIcons.Actions.IntentionBulb
                                                                                                                                    .getIconHeight() + 4);
        if (getHintClipRect().contains(hintRect)) {
          myHint.updateLocation(hintRect.x, hintRect.y);
        }
        else {
          myHint.hide();
        }
      }
    }
  }

  protected Rectangle getHintClipRect() {
    return myViewPort.getViewRect();
  }

  /**
   * @return error info for the current {@link #myComponent} state.
   */
  @NotNull
  protected abstract List<ErrorInfo> getErrorInfos();

  /**
   * @return rectangle (in {@link #myComponent} coordinates) that represents
   *         area that contains errors. This methods is invoked only if {@link #getErrorInfos()}
   *         returned non empty list of error infos. <code>null</code> means that
   *         error bounds are not defined.
   */
  @Nullable
  protected abstract Rectangle getErrorBounds();

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private class FirstStep extends BaseListPopupStep<ErrorInfo> {
    public FirstStep(List<ErrorInfo> errorInfos) {
      super(null, errorInfos);
    }

    @Override
    public Icon getIconFor(ErrorInfo value) {
      return AllIcons.Actions.RealIntentionBulb;
    }

    @NotNull
    @Override
    public String getTextFor(ErrorInfo value) {
      return value.getName();
    }

    @Override
    public PopupStep onChosen(ErrorInfo value, boolean finalChoice) {
      List<QuickFix> quickFixes = value.getQuickFixes();
      if (finalChoice) {
        return doFinalStep(getQuickFixRunnable(quickFixes.get(0)));
      }
      return new SecondStep(quickFixes);
    }

    @Override
    public boolean hasSubstep(ErrorInfo selectedValue) {
      return true;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return false;
    }
  }

  private class SecondStep extends BaseListPopupStep<QuickFix> {
    public SecondStep(List<QuickFix> fixList) {
      super(null, fixList);
    }

    @Override
    public Icon getIconFor(QuickFix value) {
      return value.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(QuickFix value) {
      return value.getName();
    }

    @Override
    public PopupStep onChosen(QuickFix value, boolean finalChoice) {
      return doFinalStep(getQuickFixRunnable(value));
    }
  }

  private Runnable getQuickFixRunnable(final QuickFix value) {
    return new Runnable() {
      @Override
      public void run() {
        myDesigner.getToolProvider().executeWithReparse(new ThrowableRunnable<Exception>() {
          @Override
          public void run() throws Exception {
            ApplicationManager.getApplication().runWriteAction(value);
          }
        }, "Run '" + value.getName() + "' QuickFix");
      }
    };
  }

  private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(4, 4, 4, 4);
  private static final Border ACTIVE_BORDER =
    BorderFactory
      .createCompoundBorder(BorderFactory.createLineBorder(Color.orange, 2), BorderFactory.createEmptyBorder(2, 2, 2, 2));

  private static final Icon INACTIVE_ARROW_ICON = new EmptyIcon(
    AllIcons.General.ArrowDown.getIconWidth(), AllIcons.General.ArrowDown.getIconHeight());

  private class InspectionHint extends JLabel {
    private final RowIcon myInactiveIcon;
    private final RowIcon myActiveIcon;

    private InspectionHint(Icon icon) {
      setOpaque(false);
      setBorder(INACTIVE_BORDER);

      myActiveIcon = new RowIcon(2);
      myActiveIcon.setIcon(icon, 0);
      myActiveIcon.setIcon(AllIcons.General.ArrowDown, 1);

      myInactiveIcon = new RowIcon(2);
      myInactiveIcon.setIcon(icon, 0);
      myInactiveIcon.setIcon(INACTIVE_ARROW_ICON, 1);

      setIcon(myInactiveIcon);

      String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
        ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
      if (acceleratorsText.length() > 0) {
        setToolTipText(DesignerBundle.message("tooltip.press.accelerator", acceleratorsText));
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          setIcon(myActiveIcon);
          setBorder(ACTIVE_BORDER);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setIcon(myInactiveIcon);
          setBorder(INACTIVE_BORDER);
        }
      });

      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          showPopup();
          return true;
        }
      }.installOn(this);
    }
  }
}
