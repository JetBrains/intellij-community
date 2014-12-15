/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.findUsages.PropertySearcher;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

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

  @Nullable
  private static GlobalSearchScope getWidestUseScope(@Nullable String key, @NotNull Project project, @NotNull Module ownModule) {
    if (key == null) return null;

    Set<Module> modules = ContainerUtil.newLinkedHashSet();
    for (IProperty property : PropertiesImplUtil.findPropertiesByKey(project, key)) {
      Module module = ModuleUtilCore.findModuleForPsiElement(property.getPsiElement());
      if (module == null) {
        return GlobalSearchScope.allScope(project);
      }
      if (module != ownModule) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) return null;

    List<Module> list = ContainerUtil.newArrayList(modules);
    GlobalSearchScope result = GlobalSearchScope.moduleWithDependentsScope(list.get(0));
    for (int i = 1; i < list.size(); i++) {
      result = result.uniteWith(GlobalSearchScope.moduleWithDependentsScope(list.get(i)));
    }
    return result;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    final PsiFile file = session.getFile();
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return super.buildVisitor(holder, isOnTheFly, session);

    final GlobalSearchScope ownUseScope = GlobalSearchScope.moduleWithDependentsScope(module);

    Object[] extensions = Extensions.getExtensions("com.intellij.referencesSearch");
    final PropertySearcher searcher =
      (PropertySearcher)ContainerUtil.find(extensions, new FilteringIterator.InstanceOf<PropertySearcher>(PropertySearcher.class));
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

        if (mayHaveUsages(property, original, name, ownUseScope, isOnTheFly)) return;

        final GlobalSearchScope widerScope = getWidestUseScope(property.getKey(), element.getProject(), module);
        if (widerScope != null && mayHaveUsages(property, original, name, widerScope, isOnTheFly)) return;

        final ASTNode propertyNode = property.getNode();
        assert propertyNode != null;

        ASTNode[] nodes = propertyNode.getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        String description = PropertiesBundle.message("unused.property.problem.descriptor.name");

        LocalQuickFix fix = PropertiesQuickFixFactory.getInstance().createRemovePropertyLocalFix();
        holder.registerProblem(key, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
      }

      private boolean mayHaveUsages(Property property, ProgressIndicator original, String name, GlobalSearchScope searchScope, boolean onTheFly) {
        PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(name, searchScope, file, original);
        if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) return false;
        if (onTheFly && cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return true;

        return ReferencesSearch.search(property, searchScope, false).findFirst() != null;
      }
    };
  }
}
