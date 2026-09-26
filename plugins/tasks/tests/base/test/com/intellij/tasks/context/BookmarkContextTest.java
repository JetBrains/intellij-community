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
      assertTrue(manager.getBookmarks().getFirst() instanceof LineBookmark);

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
      Bookmark restored = manager.getBookmarks().getFirst();
      assertTrue(restored instanceof LineBookmark);
      assertEquals(1, ((LineBookmark)restored).getLine());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testGlobalMode_ClearIsNoOpAndLoadMerges() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, false);

      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      VirtualFile file = createTestFile("test.txt", "line 0\nline 1");
      manager.add(provider.createBookmark(file, 0), BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      Element context = new Element("context");
      contextManager.saveContext(context);

      // In global mode, clearContext is a no-op — bookmark must survive
      contextManager.clearContext();
      PlatformTestUtil.waitForAlarm(50);
      assertEquals("global mode: clearContext must not remove bookmarks", 1, manager.getBookmarks().size());

      // Loading the same context must not duplicate the already-present bookmark
      contextManager.loadContext(context);
      PlatformTestUtil.waitForAlarm(50);
      assertEquals("global mode: loadContext must not duplicate existing bookmarks", 1, manager.getBookmarks().size());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

  public void testGlobalMode_DeletedBookmarkNotRestoredByLoadContext() {
    boolean originalSetting = AdvancedSettings.getBoolean(BRANCH_CONTEXT_SETTING);
    try {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, false);

      WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
      contextManager.enableUntil(getTestRootDisposable());
      BookmarksManager manager = BookmarksManager.getInstance(getProject());
      LineBookmarkProvider provider = LineBookmarkProvider.Util.find(getProject());

      VirtualFile file = createTestFile("test.txt", "line 0\nline 1");
      manager.add(provider.createBookmark(file, 0), BookmarkType.DEFAULT);
      assertEquals(1, manager.getBookmarks().size());

      Element context = new Element("context");
      contextManager.saveContext(context);

      manager.remove(manager.getBookmarks().getFirst());
      assertEquals(0, manager.getBookmarks().size());

      contextManager.loadContext(context);
      PlatformTestUtil.waitForAlarm(50);
      assertEquals("global mode: loadContext must not restore deleted bookmarks", 0, manager.getBookmarks().size());
    }
    finally {
      AdvancedSettings.setBoolean(BRANCH_CONTEXT_SETTING, originalSetting);
    }
  }

}
