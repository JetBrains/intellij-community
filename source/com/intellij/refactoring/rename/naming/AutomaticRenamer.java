package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author dsl
 */
public abstract class AutomaticRenamer <T extends PsiNamedElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.naming.AutomaticRenamer");
  protected final Map<String, String> myRenames = new HashMap<String, String>();
  protected final List<T> myElements;

  protected AutomaticRenamer() {
    myElements = new ArrayList<T>();
  }

  public boolean hasAnythingToRename() {
    final Collection<String> strings = myRenames.values();
    for (Iterator<String> iterator = strings.iterator(); iterator.hasNext();) {
      final String s = iterator.next();
      if (s != null) return true;
    }
    return false;
  }

  public void findUsages(List<UsageInfo> result, final boolean searchInStringsAndComments, final boolean searchInNonJavaFiles) {
    for (Iterator<T> iterator = myElements.iterator(); iterator.hasNext();) {
      final PsiNamedElement variable = iterator.next();
      final boolean success = findUsagesForElement(variable, result, searchInStringsAndComments, searchInNonJavaFiles);
      if (!success) {
        iterator.remove();
      }
    }
  }

  private boolean findUsagesForElement(PsiNamedElement element,
                                       List<UsageInfo> result,
                                       final boolean searchInStringsAndComments,
                                       final boolean searchInNonJavaFiles) {
    final UsageInfo[] usages = RenameUtil.findUsages(element, myRenames.get(element.getName()), searchInStringsAndComments, searchInNonJavaFiles);
    for (int i = 0; i < usages.length; i++) {
      final UsageInfo usage = usages[i];
      if (usage instanceof UnresolvableCollisionUsageInfo) return false;
    }
    result.addAll(Arrays.asList(usages));
    return true;
  }

  public List<? extends PsiNamedElement> getElements() {
    return Collections.unmodifiableList(myElements);
  }

  public String getNewName(PsiNamedElement var) {
    return myRenames.get(var.getName());
  }

  public Map<String,String> getRenames() {
    return Collections.unmodifiableMap(myRenames);
  }

  public void setRename(String name, String replacement) {
    LOG.assertTrue(myRenames.containsKey(name));
    myRenames.put(name, replacement);
  }

  public void doNotRename(String name) {
    LOG.assertTrue(myRenames.containsKey(name));
    myRenames.remove(name);
    for (int i = myElements.size() - 1; i >= 0; i--) {
      final PsiNamedElement element = myElements.get(i);
      if (name.equals(element.getName())) {
        myElements.remove(i);
      }
    }
  }

  protected void suggestAllNames(final String oldClassName, String newClassName) {
    final NameSuggester suggester = new NameSuggester(oldClassName, newClassName);
    for (int varIndex = myElements.size() - 1; varIndex >= 0; varIndex--) {
      final T element = myElements.get(varIndex);
      final String name = element.getName();
      if (!myRenames.containsKey(name)) {
        String canonicalName = nameToCanonicalName(name, element);
        final String newCanonicalName = suggester.suggestName(canonicalName);
        if (newCanonicalName.length() == 0) {
          LOG.assertTrue(false,
                         "oldClassName = " + oldClassName +
                         ", newClassName = " + newClassName +
                         ", name = " + name +
                         ", canonicalName = " +  canonicalName +
                         ", newCanonicalName = " + newCanonicalName
                         );
        }
        String newName = canonicalNameToName(newCanonicalName, element);
        if (!newName.equals(name)) {
          myRenames.put(name, newName);
        }
        else {
          myRenames.put(name, null);
        }
      }
      if (myRenames.get(name) == null) {
        myElements.remove(varIndex);
      }
    }
  }

  protected String canonicalNameToName(String canonicalName, T element) {
    return canonicalName;
  }

  protected String nameToCanonicalName(String name, T element) {
    return name;
  }

  public abstract String getDialogTitle();

  public abstract String getDialogDescription();

  public abstract String entityName();
}
