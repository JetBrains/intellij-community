// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.providers.LineBookmarkProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;

public class BookmarkContextTest extends LightPlatformCodeInsightTestCase {

  public void testBookmarks() {
    Document document = configureFromFileText("foo.txt", "\n");
    BookmarksManager bookmarksManager = BookmarksManager.getInstance(getProject());
    bookmarksManager.add(LineBookmarkProvider.Util.find(getProject()).createBookmark(getVFile(), 0), BookmarkType.DEFAULT);
    assertEquals(1, bookmarksManager.getBookmarks().size());

    Element element = new Element("foo");
    WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
    contextManager.saveContext(element);
    contextManager.clearContext();
    PlatformTestUtil.waitForAlarm(50);
    assertEquals(0, bookmarksManager.getBookmarks().size());

    contextManager.loadContext(element);
    PlatformTestUtil.waitForAlarm(50);
    assertEquals(1, bookmarksManager.getBookmarks().size());
  }
}
