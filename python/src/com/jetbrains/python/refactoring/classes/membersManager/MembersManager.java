/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * Moves members between classes via its plugins (managers).
 * To move members use {@link #getAllMembersCouldBeMoved(com.jetbrains.python.psi.PyClass)}   and {@link #moveAllMembers(java.util.Collection, com.jetbrains.python.psi.PyClass, com.jetbrains.python.psi.PyClass...)}
 * To add new manager, extend this class and add it to {@link #MANAGERS}
 *
 * @author Ilya.Kazakevich
 */
public abstract class MembersManager<T extends PyElement> implements Function<T, PyMemberInfo<T>> {
  /**
   * List of managers. Class delegates all logic to them.
   */
  private static final Collection<? extends MembersManager<? extends PyElement>> MANAGERS =
    Arrays.asList(new MethodsManager(), new SuperClassesManager(), new ClassFieldsManager(), new InstanceFieldsManager());

  @NotNull
  private final Class<T> myExpectedClass;

  protected MembersManager(@NotNull final Class<T> expectedClass) {
    myExpectedClass = expectedClass;
  }

  /**
   * Get all members that could be moved out of certain class
   *
   * @param pyClass class to find members
   * @return list of members could be moved
   */
  @NotNull
  public static List<PyMemberInfo<PyElement>> getAllMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    final List<PyMemberInfo<PyElement>> result = new ArrayList<PyMemberInfo<PyElement>>();

    for (final MembersManager<? extends PyElement> manager : MANAGERS) {
      result.addAll(transformSafely(pyClass, manager));
    }
    return result;
  }


  /**
   * Transforms elements, manager says it could move to appropriate {@link com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo}.
   * Types are checked at runtime.
   * @param pyClass class whose members we want to move
   * @param manager manager that should check class and report list of memebers
   * @return member infos
   */
  //TODO: Move to  TypeSafeMovingStrategy
  @NotNull
  @SuppressWarnings({"unchecked", "rawtypes"}) //We check type at runtime
  private static Collection<PyMemberInfo<PyElement>> transformSafely(@NotNull final PyClass pyClass, @NotNull final MembersManager<?> manager) {
    final List<PyElement> membersCouldBeMoved = manager.getMembersCouldBeMoved(pyClass);
    manager.checkElementTypes((Collection)membersCouldBeMoved);
    return (Collection<PyMemberInfo<PyElement>>)Collections2.transform(membersCouldBeMoved, (Function)manager);
  }


  /**
   * Moves members from one class to another
   *
   * @param memberInfos members to move
   * @param from        source
   * @param to          destination
   */
  public static void moveAllMembers(
    @NotNull final Collection<PyMemberInfo<PyElement>> memberInfos,
    @NotNull final PyClass from,
    @NotNull final PyClass... to
  ) {
    final Multimap<MembersManager<PyElement>, PyMemberInfo<PyElement>> managerToMember = ArrayListMultimap.create();
    //Collect map (manager)->(list_of_memebers)
    for (final PyMemberInfo<PyElement> memberInfo : memberInfos) {
      managerToMember.put(memberInfo.getMembersManager(), memberInfo);
    }
    //Move members via manager
    for (final MembersManager<PyElement> membersManager : managerToMember.keySet()) {
      final Collection<PyMemberInfo<PyElement>> members = managerToMember.get(membersManager);
      TypeSafeMovingStrategy.moveCheckingTypesAtRunTime(from, membersManager, members, to);
    }
    PyClassRefactoringUtil.insertPassIfNeeded(from);
  }



  /**
   * Checks that all elements has allowed type for manager
   *
   * @param members elements to check against manager
   */
  void checkElementTypes(@NotNull final Collection<T> elements) {
    for (final PyElement pyElement : elements) {
      Preconditions.checkArgument(myExpectedClass.isAssignableFrom(pyElement.getClass()),
                                  String.format("Manager %s expected %s but got %s", this, myExpectedClass, pyElement));
    }
  }

  /**
   * Finds member by predicate
   *
   * @param members   where to find
   * @param predicate what to find
   * @return member or null if not found
   */
  @Nullable
  public static PyMemberInfo<PyElement> findMember(@NotNull final Collection<PyMemberInfo<PyElement>> members,
                                                   @NotNull final Predicate<PyMemberInfo<PyElement>> predicate) {
    for (final PyMemberInfo<PyElement> pyMemberInfo : members) {
      if (predicate.apply(pyMemberInfo)) {
        return pyMemberInfo;
      }
    }
    return null;
  }

  /**
   * Finds member of class by predicate
   *
   * @param predicate what to find
   * @param pyClass   class to find members
   * @return member or null if not found
   */
  @Nullable
  public static PyMemberInfo<PyElement> findMember(@NotNull final PyClass pyClass,
                                                   @NotNull final Predicate<PyMemberInfo<PyElement>> predicate) {
    return findMember(getAllMembersCouldBeMoved(pyClass), predicate);
  }

  /**
   * Finds member in class. It is here only for backward compatibility with some tests.
   */
  //TODO: mark deprecated?
  @TestOnly
  @NotNull
  public static PyMemberInfo<PyElement> findMember(@NotNull final PyClass pyClass, @NotNull final PyElement pyElement) {
    final PyMemberInfo<PyElement> result = findMember(pyClass, new FindByElement(pyElement));
    if (result != null) {
      return result;
    }
    throw new IllegalArgumentException(String.format("Element %s not found in class %s or can't be moved", pyElement, pyClass));
  }

  /**
   * Get list of elements certain plugin could move out of the class
   *
   * @param pyClass class with members
   * @return list of members
   */
  @NotNull
  protected abstract List<PyElement> getMembersCouldBeMoved(@NotNull PyClass pyClass);


  /**
   * Filters out named elements (ones that subclasses {@link com.intellij.psi.PsiNamedElement}) and {@link com.jetbrains.python.psi.PyElement})
   * that are null or has null name.
   * You need it sometimes when code has errors (i.e. bad formatted code with annotation may treat annotation as method with null name.
   * note: we should probably throw exceptions in such cases and display "refactoring not available" window in handler)
   *
   * @param elementsToFilter collection of elements to filter
   * @param <T>              element type
   * @return collection of T with out of nulls and elemens whos {@link com.intellij.psi.PsiNamedElement#getName()}  returns null
   */
  @NotNull
  protected static <T extends PsiNamedElement & PyElement> Collection<T> filterNameless(@NotNull final Collection<T> elementsToFilter) {
    return Collections2.filter(elementsToFilter, new NamelessFilter<T>());
  }

  /**
   * Returns list of elements that may require reference storing aid from {@link com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil#rememberNamedReferences(com.intellij.psi.PsiElement, String...)}
   *
   * @param elements members chosen by user. In most cases members their selves could be stored, but different managers may support other strategies
   * @return elements to store
   * @see #moveAllMembers(java.util.Collection, com.jetbrains.python.psi.PyClass, com.jetbrains.python.psi.PyClass...)
   */
  protected Collection<? extends PyElement> getElementsToStoreReferences(@NotNull final Collection<T> elements) {
    return elements;
  }

  /**
   * Moves element from one class to another. Returns members that may require reference restoring aid from
   * ({@link com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil#restoreNamedReferences(com.intellij.psi.PsiElement)})
   *
   * @see #getElementsToStoreReferences(java.util.Collection)
   */
  protected abstract Collection<PyElement> moveMembers(
    @NotNull PyClass from,
    @NotNull Collection<PyMemberInfo<T>> members,
    @NotNull PyClass... to);


  /**
   * Creates {@link com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo} from {@link com.jetbrains.python.psi.PyElement}
   * This process is plugin-specific and should be implemented in each plugin
   *
   * @param input element
   * @return member info
   */
  @SuppressWarnings("NullableProblems") //IDEA-120100
  @NotNull
  @Override
  public abstract PyMemberInfo<T> apply(@NotNull T input);

  /**
   * Deletes all elements
   *
   * @param pyElementsToDelete elements to delete
   */
  protected static void deleteElements(@NotNull final Collection<? extends PsiElement> pyElementsToDelete) {
    for (final PsiElement element : pyElementsToDelete) {
      element.delete();
    }
  }

  /**
   * Fetches elements from member info.
   * @param memberInfos member info to fetch elements from
   * @param <T> type of element
   * @return list of elements
   */
  @NotNull
  protected static <T extends PyElement> Collection<T> fetchElements(@NotNull final Collection<PyMemberInfo<T>> memberInfos) {
    return Collections2.transform(memberInfos, new PyMemberExtractor<T>());
  }

  private static class PyMemberExtractor<T extends PyElement> implements Function<PyMemberInfo<T>, T> {
    @SuppressWarnings("NullableProblems") //IDEA-120100
    @Override
    public T apply(@NotNull final PyMemberInfo<T> input) {
      return input.getMember();
    }
  }

  private static class NamelessFilter<T extends PyElement & PsiNamedElement> extends NotNullPredicate<T> {
    @Override
    public boolean applyNotNull(@NotNull final T input) {
      return input.getName() != null;
    }
  }

  private static class FindByElement extends NotNullPredicate<PyMemberInfo<PyElement>> {
    private final PyElement myPyElement;

    private FindByElement(final PyElement pyElement) {
      myPyElement = pyElement;
    }

    @Override
    public boolean applyNotNull(@NotNull final PyMemberInfo<PyElement> input) {
      return input.getMember().equals(myPyElement);
    }
  }
}


