// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;

public class BookmarkContextTest extends LightPlatformCodeInsightTestCase {

  public void testBookmarks() {
    Document document = configureFromFileText("foo.txt", "\n");
    BookmarkManager bookmarkManager = BookmarkManager.getInstance(getProject());
    Bookmark bookmark = bookmarkManager.addTextBookmark(getVFile(), 0, "hey");
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    bookmark.createHighlighter(markup);
    assertEquals(1, bookmarkManager.getValidBookmarks().size());

    Element element = new Element("foo");
    WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
    contextManager.saveContext(element);
    contextManager.clearContext();
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(0, bookmarkManager.getValidBookmarks().size());

    contextManager.loadContext(element);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(1, bookmarkManager.getValidBookmarks().size());
  }
}
