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
package com.intellij.codeInspection.unused;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertySuppressableInspectionBase;
import com.intellij.lang.properties.RemovePropertyLocalFix;
import com.intellij.lang.properties.findUsages.PropertySearcher;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class UnusedPropertyInspection extends PropertySuppressableInspectionBase {
  @Override
  @NotNull
  public String getDisplayName() {
    return PropertiesBundle.message("unused.property.inspection.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "UnusedProperty";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    final PsiFile file = session.getFile();
    Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return super.buildVisitor(holder, isOnTheFly, session);
    Object[] extensions = Extensions.getExtensions("com.intellij.referencesSearch");
    final PropertySearcher searcher =
      (PropertySearcher)ContainerUtil.find(extensions, new FilteringIterator.InstanceOf<PropertySearcher>(PropertySearcher.class));
    final GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependentsScope(module);
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(file.getProject());
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!(element instanceof Property)) return;
        Property property = (Property)element;

        final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
        if (original != null) {
          if (original.isCanceled()) return;
          original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
        }

        if (ImplicitPropertyUsageProvider.isImplicitlyUsed(property)) return;

        String name = property.getName();
        if (name == null) return;
        if (searcher != null) {
          name = searcher.getKeyToSearch(name, element.getProject());
          if (name == null) return;
        }

        PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(name, searchScope, file, original);
        if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return;

        if (cheapEnough != PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES &&
            ReferencesSearch.search(property, searchScope, false).findFirst() != null) {
          return;
        }

        final ASTNode propertyNode = property.getNode();
        assert propertyNode != null;

        ASTNode[] nodes = propertyNode.getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        String description = PropertiesBundle.message("unused.property.problem.descriptor.name");

        holder.registerProblem(key, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, RemovePropertyLocalFix.INSTANCE);
      }
    };
  }
}
