/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An utility class that checks local definitions of a given name and can show a conflicts panel.
 * User: dcheryasov
 * Date: Oct 11, 2009 5:59:12 AM
 */
public class DeclarationConflictChecker {
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
                                                                   @NotNull Collection<PsiReference> references,
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

  /**
   * Shows a panel with name redefinition conflicts, if needed.
   * @param project
   * @param conflicts what {@link #findDefinitions} would return
   * @param obscured name or its topmost qualifier that is obscured, used at top of pane.
   * @param name full name (maybe qualified) to show as obscured and display as qualifier in "would be" chunks.
   * @return true iff conflicts is not empty and the panel is shown.
   */
  public static boolean showConflicts(Project project, List<Pair<PsiElement, PsiElement>> conflicts, String obscured, @Nullable String name) {
    if (conflicts.size() > 0) {
      Usage[] usages = new Usage[conflicts.size()];
      int i = 0;
      for (Pair<PsiElement, PsiElement> pair : conflicts) {
        usages[i] = new NameUsage(pair.getFirst(), pair.getSecond(), name != null? name : obscured, name != null);
        i += 1;
      }
      UsageViewPresentation prsnt = new UsageViewPresentation();
      prsnt.setTabText(PyBundle.message("CONFLICT.name.$0.obscured", obscured));
      prsnt.setCodeUsagesString(PyBundle.message("CONFLICT.name.$0.obscured.cannot.convert", obscured));
      prsnt.setUsagesWord(PyBundle.message("CONFLICT.occurrence.sing"));
      prsnt.setUsagesString(PyBundle.message("CONFLICT.occurrence.pl"));
      UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, usages, prsnt);
      return true;
    }
    return false;
  }
}

