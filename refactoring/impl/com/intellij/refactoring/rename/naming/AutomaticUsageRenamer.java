package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.usages.RenameableUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class AutomaticUsageRenamer<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.naming.AutomaticRenamer");
  private String myOldName;
  private String myNewName;
  private final Map<T, String> myRenames = new LinkedHashMap<T, String>();
  private final List<T> myElements = new ArrayList<T>();
  private final Map<T, List<RenameableUsage>> myReferences = new HashMap<T, List<RenameableUsage>>();

  protected AutomaticUsageRenamer(List<? extends T> renamedElements, String oldName, String newName) {
    myOldName = oldName;
    myNewName = newName;
    List<T> elements = new ArrayList<T>(renamedElements);
    Collections.sort(elements, new Comparator<T>() {
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
    for (T element : elements) {
      String suggestedNewName = suggestName(element);
      if (!getName(element).equals(suggestedNewName)) {
        myElements.add(element);
        setRename(element, suggestedNewName);
      }
    }
  }

  public boolean hasAnythingToRename() {
    for (final String s : myRenames.values()) {
      if (s != null) return true;
    }
    return false;
  }

  public boolean isEmpty() {
    return myRenames.isEmpty();
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

  protected boolean isNameAlreadySuggested(String newName) {
    return myRenames.values().contains(newName);
  }

  public List<? extends T> getElements() {
    return myElements;
  }

  @Nullable
  /**
   * Element source, path. For example, package. Taken into account while sorting.
   */
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

  public final void doRename() throws IncorrectOperationException {
    for (final Map.Entry<T, List<RenameableUsage>> entry : myReferences.entrySet()) {
      final T element = entry.getKey();
      final String newName = getNewElementName(element);
      doRenameElement(element);
      for (final RenameableUsage usage : entry.getValue()) {
        usage.rename(newName);
      }
    }
  }

  protected abstract void doRenameElement(T element) throws IncorrectOperationException;

  protected abstract String suggestName(T element);

  protected abstract String getName(T element);

  public abstract String getDialogTitle();

  public abstract String getDialogDescription();

  public abstract String getEntityName();
}

