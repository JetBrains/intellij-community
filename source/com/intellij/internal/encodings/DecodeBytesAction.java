package com.intellij.internal.encodings;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class DecodeBytesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    new EncodingViewer().show();
  }
}
