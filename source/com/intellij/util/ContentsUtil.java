package com.intellij.util;

import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.Content;

public class ContentsUtil {
  public static void addOrReplaceContent(ContentManager manager, Content content, boolean select) {
    final String contentName = content.getDisplayName();
    Content contentWithTheSameName = manager.findContent(contentName);
    while (contentWithTheSameName != null) {
      manager.removeContent(contentWithTheSameName);
      contentWithTheSameName = manager.findContent(contentName);
    }
    manager.addContent(content);
    if (select) {
      manager.setSelectedContent(content);
    }
  }
}
