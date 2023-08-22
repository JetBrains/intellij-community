// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspector;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.DictionaryLevel;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collections;

@SuppressWarnings("SpellCheckingInspection")
public class AcceptWordAsCorrectTest extends BasePlatformTestCase {

  public static final String TYPPO = "typpppo";
  public static final String TEST_TXT = "test.txt";

  private void doTest(String word, VirtualFile file) {
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(getProject());
    try {
      assertTrue(manager.hasProblem(word));
      CommandProcessor.getInstance().executeCommand(getProject(), () -> manager
        .acceptWordAsCorrect$intellij_spellchecker(word, file, getProject(), DictionaryLevel.PROJECT), getName(), null);
      assertFalse(manager.hasProblem(word));
    }
    finally {
      manager.updateUserDictionary(Collections.emptyList());
    }
  }

  private void doTest(String word) {
    doTest(word, null);
  }

  public void testGeneral() {
    doTest("wooord");
  }

  public void testCamelCase() {
    doTest("Tyyyyypo");
  }

  public void testNotNullFile() {
    doTest(TYPPO, myFixture.configureByText(TEST_TXT, TYPPO).getVirtualFile());
  }

  public void testNotUndoableNullFile() {
    final VirtualFile file = myFixture.configureByText(TEST_TXT, TYPPO).getVirtualFile();
    final FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(file);
    final UndoManager instance = UndoManager.getInstance(getProject());
    ((UndoManagerImpl)instance).dropHistoryInTests(); // to make sure it's empty

    doTest(TYPPO);

    assertFalse(instance.isUndoAvailable(editor));
  }

  public void testUndoableNotNullFile() {
    final VirtualFile file = myFixture.configureByText(TEST_TXT, TYPPO).getVirtualFile();
    final FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(file);
    final UndoManager instance = UndoManager.getInstance(getProject());
    ((UndoManagerImpl)instance).dropHistoryInTests(); // to make sure it's empty

    doTest(TYPPO, file);

    assertTrue(instance.isUndoAvailable(editor));
  }

  public void testUndoRedo() {
    final VirtualFile file = myFixture.configureByText(TEST_TXT, TYPPO).getVirtualFile();
    final UndoManager instance = UndoManager.getInstance(getProject());
    ((UndoManagerImpl)instance).dropHistoryInTests(); // to make sure it's empty
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(getProject());

    assertTrue(manager.hasProblem(TYPPO));
    CommandProcessor.getInstance().executeCommand(getProject(), () -> manager
      .acceptWordAsCorrect$intellij_spellchecker(TYPPO, file, getProject(), DictionaryLevel.PROJECT), getName(), null);
    assertFalse(manager.hasProblem(TYPPO));

    instance.undo(FileEditorManager.getInstance(getProject()).getSelectedEditor(file));

    assertTrue(manager.hasProblem(TYPPO));

    instance.redo(FileEditorManager.getInstance(getProject()).getSelectedEditor(file));
    assertFalse(manager.hasProblem(TYPPO));
  }
}
