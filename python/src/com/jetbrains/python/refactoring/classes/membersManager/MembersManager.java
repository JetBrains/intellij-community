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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Moves members between classes via its plugins (managers).
 * To move members use {@link #getAllMembersCouldBeMoved(com.jetbrains.python.psi.PyClass)}   and {@link #moveAllMembers(java.util.Collection, com.jetbrains.python.psi.PyClass, com.jetbrains.python.psi.PyClass...)}
 * To add new manager, extend this class and add it to {@link #MANAGERS}
 *
 * @author Ilya.Kazakevich
 */
public abstract class MembersManager<T extends PyElement> implements Function<T, PyMemberInfo> {
  /**
   * List of managers. Class delegates all logic to them.
   */
  private static final Collection<? extends MembersManager<?>> MANAGERS =
    Arrays.asList(new MethodsManager(), new SuperClassesManager(), new ClassFieldsManager(), new InstanceFieldsManager());
  private static final PyMemberExtractor PY_MEMBER_EXTRACTOR = new PyMemberExtractor();

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
  public static List<PyMemberInfo> getAllMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    final List<PyMemberInfo> result = new ArrayList<PyMemberInfo>();

    for (final MembersManager<?> manager : MANAGERS) {
      result.addAll(transformSafely(pyClass, manager));
    }
    return result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) //We check type at runtime
  @NotNull
  private static Collection<PyMemberInfo> transformSafely(@NotNull final PyClass pyClass, @NotNull final MembersManager<?> manager) {
    final List<PyElement> membersCouldBeMoved = manager.getMembersCouldBeMoved(pyClass);
    manager.checkElementTypes(membersCouldBeMoved);
    return (Collection<PyMemberInfo>)Collections2.transform(membersCouldBeMoved, (Function)manager);
  }


  /**
   * Moves members from one class to another
   *
   * @param memberInfos members to move
   * @param from        source
   * @param to          destination
   */
  public static void moveAllMembers(
    @NotNull final Collection<PyMemberInfo> memberInfos,
    @NotNull final PyClass from,
    @NotNull final PyClass... to
  ) {
    final Multimap<MembersManager<?>, PyMemberInfo> managerToMember = ArrayListMultimap.create();
    //Collect map (manager)->(list_of_memebers)
    for (final PyMemberInfo memberInfo : memberInfos) {
      managerToMember.put(memberInfo.getMembersManager(), memberInfo);
    }
    //Move members via manager
    for (final MembersManager<?> membersManager : managerToMember.keySet()) {
      final Collection<PyElement> elementsToMove = Collections2.transform(managerToMember.get(membersManager), PY_MEMBER_EXTRACTOR);
      moveSafely(from, membersManager, elementsToMove, to);
    }
    PyClassRefactoringUtil.insertPassIfNeeded(from);
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) //We check classes at runtime
  private static void moveSafely(
    @NotNull final PyClass from,
    @NotNull final MembersManager manager,
    @NotNull final Collection<PyElement> elementsToMove,
    @NotNull final PyClass... to) {
    manager.checkElementTypes(elementsToMove);

    for (final Object element : manager.getElementsToStoreReferences(elementsToMove)) {
      PyClassRefactoringUtil.rememberNamedReferences((PyElement)element, PyNames.CANONICAL_SELF); //"self" is not reference we need to move
    }

    final Collection<PyElement> newElements = manager.moveMembers(from, (Collection)elementsToMove, to);

    //Store/Restore to add appropriate imports
    for (final PyElement element : newElements) {
      PyClassRefactoringUtil.restoreNamedReferences(element);
    }

    PyClassRefactoringUtil.optimizeImports(from.getContainingFile()); //To remove unneeded imports from source
  }


  /**
   * Checks that all elements has allowed type for manager
   *
   * @param elements elements to check against manager
   */
  private void checkElementTypes(@NotNull final Collection<PyElement> elements) {
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
  public static PyMemberInfo findMember(@NotNull final Collection<PyMemberInfo> members, @NotNull final Predicate<PyMemberInfo> predicate) {
    for (final PyMemberInfo pyMemberInfo : members) {
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
  public static PyMemberInfo findMember(@NotNull final PyClass pyClass, @NotNull final Predicate<PyMemberInfo> predicate) {
    return findMember(getAllMembersCouldBeMoved(pyClass), predicate);
  }

  /**
   * Finds member in class. It is here only for backward compatibility with some tests.
   */
  //TODO: mark deprecated?
  @TestOnly
  @NotNull
  public static PyMemberInfo findMember(@NotNull final PyClass pyClass, @NotNull final PyElement pyElement) {
    final PyMemberInfo result = findMember(pyClass, new FindByElement(pyElement));
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
    @NotNull Collection<T> members,
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
  public abstract PyMemberInfo apply(@NotNull T input);

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

  private static class PyMemberExtractor implements Function<PyMemberInfo, PyElement> {
    @SuppressWarnings("NullableProblems") //IDEA-120100
    @Override
    public PyElement apply(@NotNull final PyMemberInfo input) {
      return input.getMember();
    }
  }

  private static class NamelessFilter<T extends PyElement & PsiNamedElement> extends NotNullPredicate<T> {
    @Override
    public boolean applyNotNull(@NotNull final T input) {
      return input.getName() != null;
    }
  }

  private static class FindByElement extends NotNullPredicate<PyMemberInfo> {
    private final PyElement myPyElement;

    private FindByElement(final PyElement pyElement) {
      myPyElement = pyElement;
    }

    @Override
    public boolean applyNotNull(@NotNull final PyMemberInfo input) {
      return input.getMember().equals(myPyElement);
    }
  }
}


