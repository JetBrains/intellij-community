/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.text.MessageFormat;

/**
 * @author dsl
 */
public class TipUIUtil {
  private static final String SHORTCUT_ENTITY = "&shortcut:";

  public static void openTipInBrowser(String tipPath, JEditorPane browser) {
    String fileURL = "/tips/" + tipPath;

    /* TODO: detect that file is not present
    if (!file.exists()) {
      browser.read(new StringReader("Tips for '" + feature.getDisplayName() + "' not found.  Make sure you installed IntelliJ IDEA correctly."), null);
      return;
    }
    */
    try {
      URL url = TipUIUtil.class.getResource(fileURL);
      if (url == null) {
        setCantReadText(browser);
        return;
      }

      final InputStream inputStream = url.openStream();
      final InputStreamReader reader = new InputStreamReader(inputStream);
      StringBuffer text = new StringBuffer();
      char[] buf = new char[5000];
      while (reader.ready()) {
        final int length = reader.read(buf);
        if (length == -1) break;
        text.append(buf, 0, length);
      }
      reader.close();
      updateShortcuts(text);
      browser.read(new StringReader(text.toString()), null);
      ((HTMLDocument)browser.getDocument()).setBase(url);
    }
    catch (IOException e) {
      setCantReadText(browser);
    }
  }

  private static void setCantReadText(JEditorPane browser) {
    try {
      browser.read(new StringReader("<html><body>Unable to read Tip Of The Day. Make sure IntelliJ IDEA is installed properly.</body></html>"), null);
    }
    catch (IOException e1) {
      // Can't be
    }
  }

  public static final String FONT = SystemInfo.isMac ? "Courier" : "Verdana";
  public static final String COLOR = "#993300";
  public static final String SIZE = SystemInfo.isMac ? "4" : "3";
  public static final String SHORTCUT_HTML_TEMPLATE = "<font  style=\"font-family: " + FONT +
                                                      "; font-weight:bold;\" size=\"" + SIZE +
                                                      "\"  color=\"" + COLOR + "\">{0}</font>";

  private static void updateShortcuts(StringBuffer text) {
    int lastIndex = 0;
    while(true) {
      lastIndex = text.indexOf(SHORTCUT_ENTITY, lastIndex);
      if (lastIndex < 0) return;
      final int actionIdStart = lastIndex + SHORTCUT_ENTITY.length();
      int actionIdEnd = text.indexOf(";", actionIdStart);
      if (actionIdEnd < 0) {
        return;
      }
      final String actionId = text.substring(actionIdStart, actionIdEnd);
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
      String shortcutText = "";
      for (int i = 0; i < shortcuts.length; i++) {
        final Shortcut shortcut = shortcuts[i];
        if (shortcut instanceof KeyboardShortcut) {
          shortcutText = KeymapUtil.getShortcutText(shortcut);
        }
      }
      final String replacement = MessageFormat.format(SHORTCUT_HTML_TEMPLATE, new Object[]{shortcutText});
      text.replace(lastIndex, actionIdEnd + 1, replacement);
      lastIndex += replacement.length();
    }
  }
}
