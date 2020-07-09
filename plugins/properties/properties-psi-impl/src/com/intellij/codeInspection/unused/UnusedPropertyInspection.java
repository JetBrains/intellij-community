// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unused;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public class UnusedPropertyInspection extends PropertiesInspectionBase {
  public static final String SHORT_NAME = "UnusedProperty";
  private static final Logger LOG = Logger.getInstance(UnusedPropertyInspection.class);

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  private static GlobalSearchScope getWidestUseScope(@Nullable String key, @NotNull Project project, @NotNull Module ownModule) {
    if (key == null) return null;

    Set<Module> modules = new LinkedHashSet<>();
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
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof Property)) return;
        Property property = (Property)element;

        if (isPropertyUsed(property, helper, isOnTheFly)) return;

        final ASTNode propertyNode = property.getNode();
        assert propertyNode != null;

        ASTNode[] nodes = propertyNode.getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        LocalQuickFix fix = PropertiesQuickFixFactory.getInstance().createRemovePropertyLocalFix();
        holder.registerProblem(key, isOnTheFly ? PropertiesBundle.message("unused.property.problem.descriptor.name") 
                                               : PropertiesBundle.message("unused.property.problem.descriptor.name.offline", property.getUnescapedKey()),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
      }
    };
  }

  public static boolean isPropertyUsed(@NotNull Property property, @NotNull UnusedPropertiesSearchHelper helper, boolean isOnTheFly) {
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    if (original != null) {
      if (original.isCanceled()) return true;
      original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
    }

    if (ImplicitPropertyUsageProvider.isImplicitlyUsed(property)) return true;

    String name = property.getName();
    if (name == null) return true;

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
    GlobalSearchScope exceptPropertyFiles = createExceptPropertyFilesScope(searchScope);
    GlobalSearchScope newScope = searchScope.intersectWith(exceptPropertyFiles);
    PsiSearchHelper.SearchCostResult cheapEnough = psiSearchHelper.isCheapEnoughToSearch(name, newScope, null, indicator);
    if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) return false;
    if (onTheFly && cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return true;

    return ReferencesSearch.search(property, newScope, false).findFirst() != null;
  }

  @NotNull
  private static GlobalSearchScope createExceptPropertyFilesScope(@NotNull GlobalSearchScope origin) {
    return new DelegatingGlobalSearchScope(origin) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return super.contains(file) && !FileTypeRegistry.getInstance().isFileOfType(file, PropertiesFileType.INSTANCE);
      }
    };
  }

  public static class UnusedPropertiesSearchHelper {
    private final GlobalSearchScope myOwnUseScope;
    private final Module myModule;
    private final PsiSearchHelper mySearchHelper;

    public UnusedPropertiesSearchHelper(Module module) {
      myOwnUseScope = GlobalSearchScope.moduleWithDependentsScope(module);
      myModule = module;
      mySearchHelper = PsiSearchHelper.getInstance(module.getProject());
    }

    public Module getModule() {
      return myModule;
    }

    GlobalSearchScope getOwnUseScope() {
      return myOwnUseScope;
    }

    PsiSearchHelper getSearchHelper() {
      return mySearchHelper;
    }
  }
}
