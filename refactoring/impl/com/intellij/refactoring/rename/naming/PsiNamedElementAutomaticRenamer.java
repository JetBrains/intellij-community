/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.RenameableUsage;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.util.IncorrectOperationException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public abstract class PsiNamedElementAutomaticRenamer<T extends PsiNamedElement> extends AutomaticUsageRenamer<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.naming.PsiNamedElementAutomaticRenamer");

  protected PsiNamedElementAutomaticRenamer(List<? extends T> elements, String oldName, String newName) {
    super(elements, oldName, newName);
  }

  protected String getName(T element) {
    return element.getName();
  }

  public boolean findUsagesForElement(T element,
                                      List<RenameableUsage> result,
                                      final boolean searchInStringsAndComments,
                                      final boolean searchInNonJavaFiles) {
    final String newName = getNewElementName(element);
    if (newName != null) {
      final UsageInfo[] usages = RenameUtil.findUsages(element, newName, searchInStringsAndComments, searchInNonJavaFiles, getRenames());
      for (final UsageInfo usage : usages) {
        if (usage instanceof UnresolvableCollisionUsageInfo) return false;
      }
      final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(element);
      result.addAll((Collection)Arrays.asList(UsageInfoToUsageConverter.convert(descriptor, usages)));

    }
    return true;
  }

  protected void doRenameElement(final T t) throws IncorrectOperationException {
    t.setName(getNewElementName(t));
  }

  protected String suggestName(T element) {
    String elementName = getName(element);
    final NameSuggester suggester = new NameSuggester(getOldName(), getNewName());
    String canonicalName = nameToCanonicalName(elementName, element);
    final String newCanonicalName = suggester.suggestName(canonicalName);
    if (newCanonicalName.length() == 0) {
      LOG.assertTrue(false,
          "oldName = " + getOldName() +
              ", newName = " + getNewName() +
              ", name = " + elementName +
              ", canonicalName = " +  canonicalName +
              ", newCanonicalName = " + newCanonicalName
      );
    }
    return canonicalNameToName(newCanonicalName, element);
  }

  protected String canonicalNameToName(String canonicalName, T element) {
    return canonicalName;
  }

  protected String nameToCanonicalName(String name, T element) {
    return name;
  }

}
