package com.jetbrains.python.refactoring.classes.membersManager;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * TODO: Doc
 *
 * @author Ilya.Kazakevich
 */
class TypeSafeMovingStrategy<T extends PyElement> {
  @NotNull private final PyClass myFrom;
  @NotNull private final MembersManager<T> myManager;
  @NotNull private final Collection<PyMemberInfo<T>> myMemberInfoCollection;
  @NotNull private final PyClass[] myTo;

  @SuppressWarnings({"unchecked", "rawtypes"}) //We check types at runtime
  static void moveCheckingTypesAtRunTime(@NotNull final PyClass from,
                   @NotNull final MembersManager<?> manager,
                   @NotNull final Collection<PyMemberInfo<PyElement>> memberInfoCollection,
                   @NotNull final PyClass... to) {
    manager.checkElementTypes((Collection)MembersManager.fetchElements(memberInfoCollection));
    new TypeSafeMovingStrategy(from, manager, memberInfoCollection, to).moveTyped();
  }

  private TypeSafeMovingStrategy(@NotNull final PyClass from,
                                 @NotNull final MembersManager<T> manager,
                                 @NotNull final Collection<PyMemberInfo<T>> memberInfoCollection,
                                 @NotNull final PyClass[] to) {
    myFrom = from;
    myManager = manager;
    myMemberInfoCollection = new ArrayList<PyMemberInfo<T>>(memberInfoCollection);
    myTo = to.clone();
  }


  //TODO: Doc
  private void moveTyped() {
    final Collection<T> elementsCollection = MembersManager.fetchElements(myMemberInfoCollection);
    final Collection<? extends PyElement> references = myManager.getElementsToStoreReferences(elementsCollection);
    for (final PyElement element : references) {
      PyClassRefactoringUtil.rememberNamedReferences(element, PyNames.CANONICAL_SELF); //"self" is not reference we need to move
    }

    final Collection<PyElement> newElements = myManager.moveMembers(myFrom, myMemberInfoCollection, myTo);

    //Store/Restore to add appropriate imports
    for (final PyElement element : newElements) {
      PyClassRefactoringUtil.restoreNamedReferences(element);
    }

    PyClassRefactoringUtil.optimizeImports(myFrom.getContainingFile()); //To remove unneeded imports from source
  }
}
