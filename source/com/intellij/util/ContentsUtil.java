package com.intellij.util;

import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.Content;

public class ContentsUtil {
  private ContentsUtil() {
  }

  public static void addOrReplaceContent(ContentManager manager, Content content, boolean select) {
    final String contentName = content.getDisplayName();

    Content[] contents = manager.getContents();
    for(Content oldContent: contents) {
      if (!oldContent.isPinned() && oldContent.getDisplayName().equals(contentName)) {
        manager.removeContent(oldContent);
      }
    }
    
    manager.addContent(content);
    if (select) {
      manager.setSelectedContent(content);
    }
  }
}
