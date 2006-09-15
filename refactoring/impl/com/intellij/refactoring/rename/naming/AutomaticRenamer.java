package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author dsl
 */
public abstract class AutomaticRenamer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.naming.AutomaticRenamer");
  protected final LinkedHashMap<PsiNamedElement, String> myRenames = new LinkedHashMap<PsiNamedElement, String>();
  protected final List<PsiNamedElement> myElements;

  protected AutomaticRenamer() {
    myElements = new ArrayList<PsiNamedElement>();
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
    for (Iterator<PsiNamedElement> iterator = myElements.iterator(); iterator.hasNext();) {
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
    final String newName = getNewName(element);
    if (newName != null) {
      final UsageInfo[] usages = RenameUtil.findUsages(element, newName, searchInStringsAndComments, searchInNonJavaFiles, myRenames);
      for (int i = 0; i < usages.length; i++) {
        final UsageInfo usage = usages[i];
        if (usage instanceof UnresolvableCollisionUsageInfo) return false;
      }
      result.addAll(Arrays.asList(usages));
    }
    return true;
  }

  public List<PsiNamedElement> getElements() {
    return Collections.unmodifiableList(myElements);
  }

  public String getNewName(PsiNamedElement namedElement) {
    return myRenames.get(namedElement);
  }

  public Map<PsiNamedElement,String> getRenames() {
    return Collections.unmodifiableMap(myRenames);
  }

  public void setRename(PsiNamedElement element, String replacement) {
    LOG.assertTrue(myRenames.put(element, replacement) != null);
  }

  public void doNotRename(PsiNamedElement element) {
    LOG.assertTrue(myRenames.remove(element) != null);
  }

  protected void suggestAllNames(final String oldClassName, String newClassName) {
    final NameSuggester suggester = new NameSuggester(oldClassName, newClassName);
    for (int varIndex = myElements.size() - 1; varIndex >= 0; varIndex--) {
      final PsiNamedElement element = myElements.get(varIndex);
      final String name = element.getName();
      if (!myRenames.containsKey(element)) {
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
          myRenames.put(element, newName);
        }
        else {
          myRenames.put(element, null);
        }
      }
      if (myRenames.get(element) == null) {
        myElements.remove(varIndex);
      }
    }
  }

  protected @NonNls String canonicalNameToName(@NonNls String canonicalName, PsiNamedElement element) {
    return canonicalName;
  }

  protected String nameToCanonicalName(@NonNls String name, PsiNamedElement element) {
    return name;
  }

  public boolean isSelectedByDefault() {
    return false;
  }

  public abstract String getDialogTitle();

  public abstract String getDialogDescription();

  public abstract String entityName();
}
