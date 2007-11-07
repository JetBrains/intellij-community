package com.intellij.util.ui;

import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
*/
public class EmptyClipboardOwner implements ClipboardOwner {
  public static EmptyClipboardOwner INSTANCE = new EmptyClipboardOwner();

  private EmptyClipboardOwner() {
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
  }
}