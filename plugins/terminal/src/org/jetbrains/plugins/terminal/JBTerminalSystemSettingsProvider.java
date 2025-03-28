// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.TerminalUiSettingsListener;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.model.TerminalTypeAheadSettings;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.TerminalNewTabAction;
import org.jetbrains.plugins.terminal.settings.TerminalOsSpecificOptions;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public final class JBTerminalSystemSettingsProvider extends JBTerminalSystemSettingsProviderBase {

  private final @NotNull CopyOnWriteArrayList<TerminalUiSettingsListener> myListeners = new CopyOnWriteArrayList<>();

  private @Nullable Float fontSize;

  public JBTerminalSystemSettingsProvider() {
    var connection = ApplicationManager.getApplication().getMessageBus().connect(getDisposable());
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> {
      resetTerminalFontSize(); // presentation mode, Zoom IDE...
    });
  }

  @Override
  public @NotNull TerminalActionPresentation getSelectAllActionPresentation() {
    // We cannot use keyboard shortcuts of default "Select All" action ($SelectAll), because
    // Ctrl+A should move the cursor to the start of a line in Bash/Zsh/Fish. Unfortunately, the behavior
    // cannot be restricted by Linux only, because these shells can be installed on Windows via WSL/GitBash.
    // Luckily, macOS's default "Select all" keyboard shortcut can be used here (Cmd+A).
    //
    // Let's use "Terminal.SelectAll" action with default keyboard shortcut on macOS (Cmd+A). It allows users
    // to configure custom keyboard shortcuts and avoid conflicts with shell key-binding actions.
    return getSelectAllActionPresentation(false);
  }

  @Override
  public void addUiSettingsListener(@NotNull TerminalUiSettingsListener listener) {
    // A bit complicated:
    // we delegate the cursor part to the parent
    // but do the font size part here.
    var cursorListener = new TerminalUiSettingsListener() {
      @Override
      public void cursorChanged() {
        listener.cursorChanged();
      }

      @Override
      public void fontChanged() { }

      @Override
      public void dispose() { }
    };
    Disposer.register(listener, cursorListener); // unsubscribe the cursor listener when the provided one is disposed
    super.addUiSettingsListener(cursorListener); // subscribe to cursor changes
    myListeners.add(listener); // subscribe to font changes
    Disposer.register(listener, () -> myListeners.remove(listener));
  }

  @Override
  public Font getTerminalFont() {
    return new Font(getFontFamily(), super.getTerminalFont().getStyle(), Math.round(getTerminalFontSize()));
  }

  private static @NotNull String getFontFamily() {
    return TerminalFontOptions.getInstance().getSettings().getFontFamily();
  }

  @Override
  public float getTerminalFontSize() {
    return Math.round(getTerminalFontSize2D());
  }

  @Override
  public float getTerminalFontSize2D() {
    if (fontSize == null) {
      fontSize = getScaledFontSize();
    }
    return fontSize;
  }

  @Override
  public void setTerminalFontSize(float fontSize) {
    var oldFontSize = this.fontSize;
    this.fontSize = fontSize;
    if (oldFontSize == null || !TerminalFontOptionsKt.sameFontSizes(oldFontSize, fontSize)) {
      for (var listener : myListeners) {
        listener.fontChanged();
      }
    }
  }

  @Override
  public void resetTerminalFontSize() {
    setTerminalFontSize(getScaledFontSize());
  }

  private static float getScaledFontSize() {
    return UISettingsUtils.getInstance().scaleFontSize(TerminalFontOptions.getInstance().getSettings().getFontSize());
  }

  @Override
  public float getLineSpacing() {
    return TerminalFontOptions.getInstance().getSettings().getLineSpacing();
  }

  @Override
  public float getColumnSpacing() {
    return TerminalFontOptions.getInstance().getSettings().getColumnSpacing();
  }

  @Override
  public boolean audibleBell() {
    return TerminalOptionsProvider.getInstance().getAudibleBell();
  }

  @Override
  public boolean enableMouseReporting() {
    return TerminalOptionsProvider.getInstance().getMouseReporting();
  }

  @Override
  public boolean copyOnSelect() {
    return TerminalOsSpecificOptions.getInstance().getCopyOnSelection();
  }

  @Override
  public boolean pasteOnMiddleMouseClick() {
    return TerminalOptionsProvider.getInstance().getPasteOnMiddleMouseButton();
  }

  @Override
  public boolean forceActionOnMouseReporting() {
    return true;
  }

  @Override
  public boolean overrideIdeShortcuts() {
    return TerminalOptionsProvider.getInstance().getOverrideIdeShortcuts();
  }

  @Override
  public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
    return TerminalOptionsProvider.getInstance().getHighlightHyperlinks()
           ? HyperlinkStyle.HighlightMode.ALWAYS
           : HyperlinkStyle.HighlightMode.HOVER;
  }

  @Override
  public boolean altSendsEscape() {
    return !SystemInfo.isMac || TerminalOptionsProvider.getInstance().getUseOptionAsMetaKey();
  }

  @Override
  public @NotNull TerminalTypeAheadSettings getTypeAheadSettings() {
    return new TerminalTypeAheadSettings(
      AdvancedSettings.getBoolean("terminal.type.ahead"),
      TimeUnit.MILLISECONDS.toNanos(AdvancedSettings.getInt("terminal.type.ahead.latency.threshold")),
      TerminalTypeAheadSettings.DEFAULT.getTypeAheadStyle()
    );
  }

  public @NotNull TerminalActionPresentation getNewTabActionPresentation() {
    return new TerminalActionPresentation(TerminalBundle.message("action.Terminal.NewTab.text"),
                                          getKeyStrokesByActionId(TerminalNewTabAction.ACTION_ID));
  }

  public @NotNull TerminalActionPresentation getCloseTabActionPresentation() {
    return new TerminalActionPresentation(TerminalBundle.message("action.Terminal.CloseTab.text"),
                                          ContainerUtil.concat(getKeyStrokesByActionId("Terminal.CloseTab"),
                                                               getKeyStrokesByActionId("CloseActiveTab")));
  }
}
