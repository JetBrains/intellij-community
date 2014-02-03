package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Moves members between classes via its plugins (managers).
 * To move members use {@link #getAllMembersCouldBeMoved(com.jetbrains.python.psi.PyClass)}   and {@link #moveMembers(com.jetbrains.python.psi.PyClass, com.jetbrains.python.psi.PyClass, java.util.Collection)}
 * To add new manager, extend this class and add it to {@link #MANAGERS}
 *
 * @author Ilya.Kazakevich
 */
public abstract class MembersManager implements Function<PyElement, PyMemberInfo> {
  /**
   * List of managers. Class delegates all logic to them.
   */
  private static final Collection<MembersManager> MANAGERS = Arrays.asList(new MethodsManager(), new SuperClassesManager());
  private static final PyMemberExtractor PY_MEMBER_EXTRACTOR = new PyMemberExtractor();

  protected MembersManager() {
  }

  /**
   * Get all members that could be moved out of certain class
   * @param pyClass class to find members
   * @return list of members could be moved
   */
  @NotNull
  public static List<PyMemberInfo> getAllMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    final List<PyMemberInfo> result = new ArrayList<PyMemberInfo>();

    for (final MembersManager manager : MANAGERS) {
      result.addAll(Collections2.transform(manager.getMembersCouldBeMoved(pyClass), manager));
    }
    return result;
  }


  /**
   * Moves members from one class to another
   * @param from source
   * @param to destination
   * @param memberInfos members to move
   */
  public static void moveAllMembers(@NotNull final PyClass from,
                                    @NotNull final PyClass to,
                                    @NotNull final Collection<PyMemberInfo> memberInfos) {
    final Multimap<MembersManager, PyMemberInfo> managerToMember = ArrayListMultimap.create();
    //Collect map (manager)->(list_of_memebers)
    for (final PyMemberInfo memberInfo : memberInfos) {
      managerToMember.put(memberInfo.getMembersManager(), memberInfo);
    }
    //Move members via manager
    for (final MembersManager membersManager : managerToMember.keySet()) {
      membersManager.moveMembers(from, to, Collections2.transform(managerToMember.get(membersManager), PY_MEMBER_EXTRACTOR));
    }
    PyClassRefactoringUtil.insertPassIfNeeded(from);
  }

  /**
   * Finds member in class. It is here only for backward compatibility with some tests.
   */
  //TODO: mark deprecated?
  @TestOnly
  @NotNull
  public static PyMemberInfo findMember(@NotNull final PyClass pyClass, @NotNull final PyElement pyElement) {
    for (final PyMemberInfo pyMemberInfo : getAllMembersCouldBeMoved(pyClass)) {
      if (pyMemberInfo.getMember().equals(pyElement)) {
        return pyMemberInfo;
      }
    }
    throw new IllegalArgumentException(String.format("Element %s not found in class %s or can't be moved", pyElement, pyClass));
  }

  /**
   * Get list of elements certain plugin could move out of the class
   * @param pyClass class with members
   * @return list of members
   */
  @NotNull
  protected abstract List<PyElement> getMembersCouldBeMoved(@NotNull PyClass pyClass);

  /**
   * Moves element from one class to another
   * @param from source
   * @param to destination
   * @param members collection of memebrs to move
   */
  protected abstract void moveMembers(@NotNull PyClass from, @NotNull PyClass to, @NotNull Collection<PyElement> members);

  //TODO: Doc
  @SuppressWarnings("NullableProblems") //IDEA-120100
  @NotNull
  @Override
  public abstract PyMemberInfo apply(@NotNull PyElement input);

  private static class PyMemberExtractor implements Function<PyMemberInfo, PyElement> {
    @SuppressWarnings("NullableProblems") //IDEA-120100
    @Override
    public PyElement apply(@NotNull final PyMemberInfo input) {
      return input.getMember();
    }
  }
}


