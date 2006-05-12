package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.StatusBar;

import javax.swing.*;
import java.awt.event.ActionListener;

public interface StatusBarEx extends StatusBar{
  String getInfo();

  void setPosition(String s);

  void setStatus(String s);

  void setStatusEnabled(boolean enabled);

  void showCancelButton(Icon icon, ActionListener listener, String tooltopText);

  void hideCancelButton();

  void addProgress();

  void setProgressValue(int progress);

  void hideProgress();

  void setWriteStatus(boolean locked);

  void clear();

  void updateEditorHighlightingStatus(final boolean isClear);

  void cleanupCustomComponents();
}
