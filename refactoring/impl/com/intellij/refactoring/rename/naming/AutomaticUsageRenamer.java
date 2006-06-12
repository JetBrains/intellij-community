package com.intellij.refactoring.rename.naming;

import com.intellij.usages.Usage;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class AutomaticUsageRenamer<T> {
  private static final Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance("#com.intellij.refactoring.rename.naming.AutomaticRenamer");
  private String myOldName;
  private String myNewName;
  private final Map<T, String> myRenames = new LinkedHashMap<T, String>();
  private final List<T> myElements;

  protected AutomaticUsageRenamer(List<? extends T> elements, String oldName, String newName) {
    myOldName = oldName;
    myNewName = newName;
    myElements = new ArrayList<T>(elements);
    Collections.sort(myElements, new Comparator<T>() {
      private int compareNullable(@Nullable String s1, @Nullable String s2) {
        if (s1 != null) {
          return s2 == null ? 1 : s1.compareTo(s2);
        }
        return s2 == null ? 0 : -1;
      }

      public int compare(T o1, T o2) {
        int i = compareNullable(getSourceName(o1), getSourceName(o2));
        if (i != 0) return i;
        return getName(o1).compareTo(getName(o2));
      }
    });
    suggestAllNames();
  }

  public boolean hasAnythingToRename() {
    final Collection<String> strings = myRenames.values();
    for (final String s : strings) {
      if (s != null) return true;
    }
    return false;
  }

  public boolean isEmpty() {
    return myRenames.isEmpty();
  }

  public void findUsages(List<Usage> result, final boolean searchInStringsAndComments, final boolean searchInNonJavaFiles) {
    for (Iterator<? extends T> iterator = myElements.iterator(); iterator.hasNext();) {
      final T variable = iterator.next();
      final boolean success = findUsagesForElement(variable, result, searchInStringsAndComments, searchInNonJavaFiles);
      if (!success) {
        iterator.remove();
      }
    }
  }

  protected String getOldName() {
    return myOldName;
  }

  public String getNewName() {
    return myNewName;
  }

  protected boolean isChecked(T element) {
    return myRenames.containsKey(element);
  }

  protected boolean isCheckedInitially(T element) {
    return false;
  }

  public boolean findUsagesForElement(T element, List<Usage> result,
                                                  final boolean searchInStringsAndComments,
                                                  final boolean searchInNonJavaFiles) {
    return true;
  }

  protected boolean isNameAlreadySuggested(String newName) {
    return myRenames.values().contains(newName);
  }

  public List<? extends T> getElements() {
    return myElements;
  }

  @Nullable
  public String getSourceName(T element) {
    return null;
  }

  public String getNewElementName(T element) {
    return myRenames.get(element);
  }

  public Map<? extends T,String> getRenames() {
    return myRenames;
  }

  public void setRename(T element, String replacement) {
    LOG.assertTrue(replacement != null);
    myRenames.put(element, replacement);
  }

  public void doNotRename(T element) {
    myRenames.remove(element);
  }

  @Nullable
  public String getErrorText(T element) {
    return null;
  }

  private void suggestAllNames() {
    for (Iterator<T> iterator = myElements.iterator(); iterator.hasNext();) {
      T element = iterator.next();
      if (!myRenames.containsKey(element)) {
        String suggestedNewName = suggestName(element);
        if (!getName(element).equals(suggestedNewName)) {
          setRename(element, suggestedNewName);
        }
        else {
          doNotRename(element);
        }
      }
      if (myRenames.get(element) == null) {
        iterator.remove();
      }
    }
  }

  protected abstract String suggestName(T element);

  protected abstract String getName(T element);

  public abstract String getDialogTitle();

  public abstract String getDialogDescription();

  public abstract String getEntityName();
}

