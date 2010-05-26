/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.concurrency.JobUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
 */
public class UnusedPropertyInspection extends PropertySuppressableInspectionBase {
  @NotNull
  public String getDisplayName() {
    return PropertiesBundle.message("unused.property.inspection.display.name");
  }

  @NotNull
  public String getShortName() {
    return "UnusedProperty";
  }

  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    final List<Property> properties = ((PropertiesFile)file).getProperties();
    Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return null;
    final List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();

    final GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependentsScope(module);
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    if (!JobUtil.invokeConcurrentlyUnderMyProgress(properties, new Processor<Property>() {
      public boolean process(final Property property) {
        if (original != null) {
          if (original.isCanceled()) return false;
          original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
        }

        String name = property.getName();
        if (name == null) return true;
        PsiSearchHelper.SearchCostResult cheapEnough = file.getManager().getSearchHelper().isCheapEnoughToSearch(name, searchScope, file,
                                                                                                                 original);
        if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return true;

        final PsiReference usage = cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES ? null :
                                   ReferencesSearch.search(property, searchScope, false).findFirst();
        if (usage != null) {
          return true;
        }
        final ASTNode propertyNode = property.getNode();
        assert propertyNode != null;

        ASTNode[] nodes = propertyNode.getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        String description = PropertiesBundle.message("unused.property.problem.descriptor.name");
        ProblemDescriptor descriptor = manager.createProblemDescriptor(key, description, RemovePropertyLocalFix.INSTANCE, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                                       isOnTheFly);
        synchronized (descriptors) {
          descriptors.add(descriptor);
        }

        return true;
      }
    }, isOnTheFly)) throw new ProcessCanceledException();

    synchronized (descriptors) {
      return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }
  }
}
