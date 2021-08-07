// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Moves members checking types at runtime.
 *
 * @author Ilya.Kazakevich
 */
final class TypeSafeMovingStrategy<T extends PyElement> {
  @NotNull private final PyClass myFrom;
  @NotNull private final MembersManager<T> myManager;
  @NotNull private final Collection<PyMemberInfo<T>> myMemberInfoCollection;
  private final PyClass @NotNull [] myTo;

  /**
   * Move members.
   * @param from source
   * @param manager manager to be used
   * @param memberInfoCollection what to move
   * @param to where
   */
  @SuppressWarnings({"unchecked", "rawtypes"}) //We check types at runtime
  static void moveCheckingTypesAtRunTime(@NotNull final PyClass from,
                   @NotNull final MembersManager<?> manager,
                   @NotNull final Collection<? extends PyMemberInfo<PyElement>> memberInfoCollection,
                   final PyClass @NotNull ... to) {
    manager.checkElementTypes((Collection)MembersManager.fetchElements(memberInfoCollection));
    new TypeSafeMovingStrategy(from, manager, memberInfoCollection, to).moveTyped();
  }

  private TypeSafeMovingStrategy(@NotNull final PyClass from,
                                 @NotNull final MembersManager<T> manager,
                                 @NotNull final Collection<PyMemberInfo<T>> memberInfoCollection,
                                 final PyClass @NotNull [] to) {
    myFrom = from;
    myManager = manager;
    myMemberInfoCollection = new ArrayList<>(memberInfoCollection);
    myTo = to.clone();
  }


  /**
   * While types are already checked at runtime, this method could move everything in type-safe manner.
   */
  private void moveTyped() {
    final Collection<T> elementsCollection = MembersManager.fetchElements(myMemberInfoCollection);
    final Collection<? extends PyElement> references = myManager.getElementsToStoreReferences(elementsCollection);

    // Store references to add required imports
    for (final PyElement element : references) {
      PyClassRefactoringUtil.rememberNamedReferences(element, PyNames.CANONICAL_SELF); //"self" is not reference we need to move
    }

    // Move
    final Collection<PyElement> newElements = myManager.moveMembers(myFrom, myMemberInfoCollection, myTo);

    // Restore references to add appropriate imports
    for (final PyElement element : newElements) {
      PyClassRefactoringUtil.restoreNamedReferences(element, null, references.toArray(PsiElement.EMPTY_ARRAY));
    }
  }
}
