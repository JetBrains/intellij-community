// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An utility class that checks local definitions of a given name and can show a conflicts panel.
 * User: dcheryasov
 */
public final class DeclarationConflictChecker {
  private DeclarationConflictChecker() { /* Don't instantiate */ }

  /**
   * For each reference in the collection, finds a definition of name visible from the point of the reference. Returns a list of
   * such definitions.
   * @param name what to look for.
   * @param references references to check.
   * @param ignored if an element defining the name is also listed here, ignore it.
   * @return a list of pairs (referring element, element that defines name).
   */
  @NotNull
  public static List<Pair<PsiElement, PsiElement>> findDefinitions(@NotNull String name,
                                                                   @NotNull Collection<? extends PsiReference> references,
                                                                   @NotNull Set<PsiElement> ignored) {
    final List<Pair<PsiElement, PsiElement>> conflicts = new ArrayList<>();
    for (PsiReference ref : references) {
      final PsiElement refElement = ref.getElement();
      final ScopeOwner owner = ScopeUtil.getScopeOwner(refElement);
      if (owner != null) {
        for (PsiElement element : PyResolveUtil.resolveLocally(owner, name)) {
          if (!ignored.contains(element)) {
            conflicts.add(Pair.create(refElement, element));
          }
        }
      }
    }
    return conflicts;
  }
}

