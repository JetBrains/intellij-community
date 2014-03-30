package com.jetbrains.python.refactoring.classes;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Test only {@link com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo} representation.
 * @author Ilya.Kazakevich
 */
public class PyPresenterTestMemberEntry {
  @NonNls @NotNull
  private final String myName;
  private final boolean myEnabled;
  private final boolean myStaticEntry;
  private final boolean myMayBeAbstract;

  /**
   * @param name name of the member
   * @param enabled is member enabled or not
   * @param staticEntry is member static entry
   * @param mayBeAbstract if element has "abstract" checkbox or not
   */
  public PyPresenterTestMemberEntry(@NotNull final String name, final boolean enabled, final boolean staticEntry, final boolean mayBeAbstract) {
    myName = name;
    myEnabled = enabled;
    myStaticEntry = staticEntry;
    myMayBeAbstract = mayBeAbstract;
  }

  @Override
  public String toString() {
    return "PyPresenterTestMemberEntry{" +
           "myName='" + myName + '\'' +
           ", myEnabled=" + myEnabled +
           ", myStaticEntry=" + myStaticEntry +
           ", myMayBeAbstract=" + myMayBeAbstract +
           '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof PyPresenterTestMemberEntry)) return false;

    final PyPresenterTestMemberEntry entry = (PyPresenterTestMemberEntry)o;

    if (myEnabled != entry.myEnabled) return false;
    if (myMayBeAbstract != entry.myMayBeAbstract) return false;
    if (myStaticEntry != entry.myStaticEntry) return false;
    if (!myName.equals(entry.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myEnabled ? 1 : 0);
    result = 31 * result + (myStaticEntry ? 1 : 0);
    result = 31 * result + (myMayBeAbstract ? 1 : 0);
    return result;
  }
}
