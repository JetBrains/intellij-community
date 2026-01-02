// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyMoveModuleMembersEventsTest extends PyTestCase {
  private final List<String> myEvents = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MessageBusConnection connection = myFixture.getProject().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, new RefactoringEventListener() {
      @Override
      public void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData) {
        myEvents.add("started: " + refactoringId);
      }

      @Override
      public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
        myEvents.add("done: " + refactoringId);
      }

      @Override
      public void conflictsDetected(@NotNull String refactoringId, @NotNull RefactoringEventData conflictsData) {
        myEvents.add("conflicts: " + refactoringId);
      }

      @Override
      public void undoRefactoring(@NotNull String refactoringId) {
        myEvents.add("undo: " + refactoringId);
      }
    });
  }

  public void testMoveEvents() {
    myFixture.configureByText("a.py", "class C:\n    pass\n");
    PsiFile fileA = myFixture.getFile();
    myFixture.configureByText("b.py", "");
    PyClass pyClass = PsiTreeUtil.findChildOfType(fileA, PyClass.class);
    assertNotNull(pyClass);
    String destination = myFixture.getFile().getVirtualFile().getParent().getPath() + "/b.py";
    
    new PyMoveModuleMembersProcessor(new PsiNamedElement[]{pyClass}, destination).run();
    
    assertContainsElements(myEvents, "started: refactoring.python.move.module.members", "done: refactoring.python.move.module.members");
  }
}
