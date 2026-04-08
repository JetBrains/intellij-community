// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.LineBookmark;
import com.intellij.ide.bookmark.providers.LineBookmarkProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;

import java.io.IOException;

public class BookmarkContextTest extends LightPlatformCodeInsightTestCase {
  private static final String BRANCH_CONTEXT_SETTING = "tasks.enable.bookmark.context.on.branch.switch";

  @Override
  protected void tearDown() throws Exception {
    try {
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      if (manager != null) {
        manager.remove();
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private VirtualFile createTestFile(String name, String content) {
    try {
      return WriteAction.compute(() -> {
        VirtualFile file = getSourceRoot().findChild(name);
        if (file == null) {
          file = getSourceRoot().createChildData(this, name);
        }
        VfsUtil.saveText(file, content);
        return file;
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testBookmarks() {
    WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
    contextManager.enableUntil(getTestRootDisposable());
    VirtualFile file = createTestFile("foo.txt", "\n");
    BookmarksManager bookmarksManager = BookmarksManager.getInstance(getProject());
    bookmarksManager.add(LineBookmarkProvider.Util.find(getProject()).createBookmark(file, 0), BookmarkType.DEFAULT);
    assertEquals(1, bookmarksManager.getBookmarks().size());

    Element element = new Element("foo");
    contextManager.saveContext(element);
    contextManager.clearContext();
    PlatformTestUtil.waitForAlarm(50);
    assertEquals(0, bookmarksManager.getBookmarks().size());

    contextManager.loadContext(element);
    PlatformTestUtil.waitForAlarm(50);
    assertEquals(1, bookmarksManager.getBookmarks().size());
  }

  public void testOffOnTransition() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, false);
      
      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      VirtualFile fileA = createTestFile("A.txt", "content A");
      manager.add(provider.createBookmark(fileA, 0), BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      Element element1 = new Element("task1");
      contextManager.saveContext(element1);

      contextManager.clearContext();
      assertEquals(1, manager.getBookmarks().size());

      VirtualFile fileB = createTestFile("B.txt", "content B");
      manager.add(provider.createBookmark(fileB, 0), BookmarkType.DEFAULT);
      assertEquals(2, manager.getBookmarks().size());

      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, true);

      contextManager.clearContext();
      assertEquals(0, manager.getBookmarks().size());

      contextManager.loadContext(element1);
      PlatformTestUtil.waitForAlarm(100);
      assertEquals(1, manager.getBookmarks().size());
      Bookmark restored = manager.getBookmarks().get(0);
      assertEquals("A.txt", ((com.intellij.ide.bookmark.FileBookmark)restored).getFile().getName());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testPerBranchMode_BookmarksClearedOnSwitch() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, true);
      
      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      VirtualFile fileA = createTestFile("A.txt", "content A");
      manager.add(provider.createBookmark(fileA, 0), BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      Element element1 = new Element("branch1");
      contextManager.saveContext(element1);

      contextManager.clearContext();
      assertEquals(0, manager.getBookmarks().size());
      
      contextManager.loadContext(element1);
      assertEquals(1, manager.getBookmarks().size());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testTransition_GlobalToPerBranchMode() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, false);
      
      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      VirtualFile fileA = createTestFile("A.txt", "content A");
      manager.add(provider.createBookmark(fileA, 0), BookmarkType.DEFAULT);
      Element element1 = new Element("branch1");
      contextManager.saveContext(element1);

      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, true);
      
      contextManager.clearContext();
      assertEquals(0, manager.getBookmarks().size());
      
      contextManager.loadContext(element1);
      assertEquals(1, manager.getBookmarks().size());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testPerBranchMode_WithContentChanges() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, true);

      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      // Branch 1: Create file with content and bookmark
      VirtualFile file = createTestFile("test.txt", "line 1\nline 2\nline 3");
      Bookmark bookmark1 = provider.createBookmark(file, 1); // line 2
      manager.add(bookmark1, BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());
      assertTrue(manager.getBookmarks().get(0) instanceof LineBookmark);

      Element branch1Context = new Element("branch1");
      contextManager.saveContext(branch1Context);

      // Switch to Branch 2: clearContext removes bookmarks
      contextManager.clearContext();
      PlatformTestUtil.waitForAlarm(50);
      assertEquals(0, manager.getBookmarks().size());

      // Branch 2: Change file content
      createTestFile("test.txt", "new line 1\nnew line 2\nnew line 3");

      // Branch 2: Create different bookmark
      Bookmark bookmark2 = provider.createBookmark(file, 0); // line 1
      manager.add(bookmark2, BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      Element branch2Context = new Element("branch2");
      contextManager.saveContext(branch2Context);

      // Switch back to Branch 1: clearContext and loadContext
      contextManager.clearContext();
      PlatformTestUtil.waitForAlarm(50);
      assertEquals(0, manager.getBookmarks().size());

      // Restore Branch 1 file content
      createTestFile("test.txt", "line 1\nline 2\nline 3");

      contextManager.loadContext(branch1Context);
      PlatformTestUtil.waitForAlarm(100);
      
      // Should restore Branch 1 bookmark as valid (content matches)
      assertEquals(1, manager.getBookmarks().size());
      Bookmark restored = manager.getBookmarks().get(0);
      assertTrue(restored instanceof LineBookmark);
      assertEquals(1, ((LineBookmark)restored).getLine());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testGlobalMode_KeepsExistingValidBookmarksOnMerge() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, false);

      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      // Create file with content and bookmark
      VirtualFile file = createTestFile("test.txt", "line 1\nline 2\nline 3");
      Bookmark bookmark1 = provider.createBookmark(file, 1);
      manager.add(bookmark1, BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      Element context1 = new Element("context1");
      contextManager.saveContext(context1);

      // In global mode, clearContext does NOT remove bookmarks
      contextManager.clearContext();
      PlatformTestUtil.waitForAlarm(50);
      assertEquals(1, manager.getBookmarks().size());
      
      // Store reference to existing bookmark
      Bookmark existingBookmark = manager.getBookmarks().get(0);

      // Load context: should keep existing valid bookmark (not replace)
      contextManager.loadContext(context1);
      PlatformTestUtil.waitForAlarm(100);

      // In global mode, merge should keep existing valid bookmark
      assertEquals(1, manager.getBookmarks().size());
      Bookmark result = manager.getBookmarks().get(0);
      assertTrue(result instanceof LineBookmark);
      assertEquals(1, ((LineBookmark)result).getLine());
      
      // Verify it's the same instance (kept, not replaced)
      assertSame("Bookmark should be kept in global mode, not replaced", existingBookmark, result);
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testPerBranchMode_ReplacesExistingBookmarksOnMerge() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, true);

      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      // Create file and bookmark at line 1
      VirtualFile file = createTestFile("test.txt", "line 1\nline 2\nline 3");
      Bookmark bookmark1 = provider.createBookmark(file, 1);
      manager.add(bookmark1, BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      // Save context
      Element context1 = new Element("context1");
      contextManager.saveContext(context1);

      // clearContext removes all bookmarks in branch mode
      contextManager.clearContext();
      PlatformTestUtil.waitForAlarm(50);
      assertEquals(0, manager.getBookmarks().size());

      // Add a bookmark at the SAME location (line 1)
      Bookmark bookmark2 = provider.createBookmark(file, 1);
      manager.add(bookmark2, BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());
      
      // Store reference to existing bookmark
      Bookmark existingBookmark = manager.getBookmarks().get(0);

      // Load saved context - should REPLACE existing bookmark with saved one
      contextManager.loadContext(context1);
      PlatformTestUtil.waitForAlarm(100);

      // In branch mode, merge should replace existing valid bookmark
      assertEquals(1, manager.getBookmarks().size());
      Bookmark result = manager.getBookmarks().get(0);
      assertTrue(result instanceof LineBookmark);
      assertEquals(1, ((LineBookmark)result).getLine());
      
      // Verify it's a different instance (replaced, not kept)
      assertNotSame("Bookmark should be replaced, not kept", existingBookmark, result);
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }
}
