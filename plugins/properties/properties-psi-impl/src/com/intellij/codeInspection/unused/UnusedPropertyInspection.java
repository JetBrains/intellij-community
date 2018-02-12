/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorInspection;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.findUsages.PropertySearcher;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * @author cdr
 */
public class UnusedPropertyInspection extends PropertiesInspectionBase implements ResourceBundleEditorInspection {
  private static final Logger LOG = Logger.getInstance(UnusedPropertyInspection.class);

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

    return GlobalSearchScope.union(modules.stream().map(Module::getModuleWithDependentsScope).toArray(GlobalSearchScope[]::new));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    final PsiFile file = session.getFile();
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return super.buildVisitor(holder, isOnTheFly, session);

    final UnusedPropertiesSearchHelper helper = new UnusedPropertiesSearchHelper(module);
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!(element instanceof Property)) return;
        Property property = (Property)element;

        if (isPropertyUsed(property, helper, isOnTheFly)) return;

        final ASTNode propertyNode = property.getNode();
        assert propertyNode != null;

        ASTNode[] nodes = propertyNode.getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        LocalQuickFix fix = PropertiesQuickFixFactory.getInstance().createRemovePropertyLocalFix();
        holder.registerProblem(key, PropertiesBundle.message("unused.property.problem.descriptor.name"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
      }
    };
  }

  @NotNull
  @Override
  public Function<IProperty[], ResourceBundleEditorProblemDescriptor[]> buildPropertyGroupVisitor(@NotNull ResourceBundle resourceBundle) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(resourceBundle.getDefaultPropertiesFile().getContainingFile());
    if (module == null) return x -> null;
    final UnusedPropertiesSearchHelper helper = new UnusedPropertiesSearchHelper(module);

    return properties -> !isPropertyUsed((Property)properties[0], helper, true) ? new ResourceBundleEditorProblemDescriptor[]{
      new ResourceBundleEditorProblemDescriptor(ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                                PropertiesBundle.message("unused.property.problem.descriptor.name"),
                                                new RemovePropertiesFromAllLocalesFix((Property)properties[0]))} : null;
  }

  private static boolean isPropertyUsed(@NotNull Property property, @NotNull UnusedPropertiesSearchHelper helper, boolean isOnTheFly) {
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    if (original != null) {
      if (original.isCanceled()) return true;
      original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
    }

    if (ImplicitPropertyUsageProvider.isImplicitlyUsed(property)) return true;

    String name = property.getName();
    if (name == null) return true;
    if (helper.getSearcher() != null) {
      name = helper.getSearcher().getKeyToSearch(name, property.getProject());
      if (name == null) return true;
    }

    PsiSearchHelper searchHelper = helper.getSearchHelper();
    if (mayHaveUsages(property, name, searchHelper, helper.getOwnUseScope(), isOnTheFly, original)) return true;

    final GlobalSearchScope widerScope = getWidestUseScope(property.getKey(), property.getProject(), helper.getModule());
    if (widerScope != null && mayHaveUsages(property, name, searchHelper, widerScope, isOnTheFly, original)) return true;
    return false;
  }

  private static boolean mayHaveUsages(@NotNull PsiElement property,
                                       @NotNull String name,
                                       @NotNull PsiSearchHelper psiSearchHelper,
                                       @NotNull GlobalSearchScope searchScope,
                                       boolean onTheFly,
                                       @Nullable ProgressIndicator indicator) {
    PsiSearchHelper.SearchCostResult cheapEnough = psiSearchHelper.isCheapEnoughToSearch(name, searchScope, null, indicator);
    if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) return false;
    if (onTheFly && cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return true;

    return ReferencesSearch.search(property, searchScope, false).findFirst() != null;
  }

  private static class UnusedPropertiesSearchHelper {
    private final GlobalSearchScope myOwnUseScope;
    private final Module myModule;
    private final PropertySearcher mySearcher;
    private final PsiSearchHelper mySearchHelper;

    UnusedPropertiesSearchHelper(Module module) {
      myOwnUseScope = GlobalSearchScope.moduleWithDependentsScope(module);
      myModule = module;
      mySearcher = (PropertySearcher)ContainerUtil.find(Extensions.getExtensions("com.intellij.referencesSearch"),
                                                        new FilteringIterator.InstanceOf<>(PropertySearcher.class));
      mySearchHelper = PsiSearchHelper.SERVICE.getInstance(module.getProject());
    }

    public Module getModule() {
      return myModule;
    }

    GlobalSearchScope getOwnUseScope() {
      return myOwnUseScope;
    }

    public PropertySearcher getSearcher() {
      return mySearcher;
    }

    PsiSearchHelper getSearchHelper() {
      return mySearchHelper;
    }
  }

  private static class RemovePropertiesFromAllLocalesFix implements QuickFix<ResourceBundleEditorProblemDescriptor> {
    private final SmartPsiElementPointer<Property> myRepresentativePointer;

    private RemovePropertiesFromAllLocalesFix(Property property) {
      myRepresentativePointer = SmartPointerManager.getInstance(property.getProject()).createSmartPsiElementPointer(property);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return PropertiesBundle.message("remove.property.intention.text");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ResourceBundleEditorProblemDescriptor descriptor) {
      final Property element = myRepresentativePointer.getElement();
      if (element == null) return;
      final String key = element.getKey();
      if (key == null) return;
      final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(myRepresentativePointer.getContainingFile());
      LOG.assertTrue(file != null);
      file.getResourceBundle()
        .getPropertiesFiles()
        .stream()
        .flatMap(f -> f.findPropertiesByKey(key).stream())
        .filter(Objects::nonNull)
        .map(IProperty::getPsiElement)
        .filter(FileModificationService.getInstance()::preparePsiElementForWrite)
        .forEach(PsiElement::delete);
    }
  }
}
