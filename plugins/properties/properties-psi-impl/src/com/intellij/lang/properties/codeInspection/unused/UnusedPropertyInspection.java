// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.codeInspection.unused;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.RegexValidator;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class UnusedPropertyInspection extends PropertiesInspectionBase {
  private static final ExtensionPointName<ImplicitPropertyUsageProvider>
    EP_NAME = new ExtensionPointName<>("com.intellij.properties.implicitPropertyUsageProvider");

  public static final String SHORT_NAME = "UnusedProperty";

  @RegExp public @NotNull String fileNameMask = ".*";

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.string("fileNameMask", PropertiesBundle.message("label.analyze.only.property.files.whose.name.matches"),
                     30, new RegexValidator())
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onValueSet("fileNameMask", value -> {
      if ("".equals(value)) fileNameMask = ".*";
    });
  }

  private static @Nullable GlobalSearchScope getWidestUseScope(@Nullable String key, @NotNull Project project, @NotNull Module ownModule) {
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

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                                 final boolean isOnTheFly,
                                                 final @NotNull LocalInspectionToolSession session) {
    final PsiFile file = session.getFile();
    if (!fileNameMask.isEmpty()) {
      try {
        Pattern p = Pattern.compile(fileNameMask);
        if (!p.matcher(file.getName()).matches()) {
          return PsiElementVisitor.EMPTY_VISITOR;
        }
      }
      catch (PatternSyntaxException ignored) {
      }
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return PsiElementVisitor.EMPTY_VISITOR;

    if (InjectedLanguageManager.getInstance(module.getProject()).isInjectedFragment(holder.getFile())
        || holder.getFile().getUserData(FileContextUtil.INJECTED_IN_ELEMENT) != null) {
      // Properties inside injected fragments cannot be normally referenced
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    VirtualFile virtualFile = holder.getFile().getVirtualFile();
    if (virtualFile == null ||
        !ProjectFileIndex.getInstance(module.getProject()).isInSource(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    UnusedPropertiesSearchHelper helper = new UnusedPropertiesSearchHelper(module, holder.getFile());

    final Set<PsiElement> propertiesBeingCommitted = getBeingCommittedProperties(file);

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof Property property)) return;
        if (propertiesBeingCommitted != null && !propertiesBeingCommitted.contains(property)) return;

        if (isPropertyUsed(property, helper, isOnTheFly)) return;

        final ASTNode propertyNode = property.getNode();
        assert propertyNode != null;

        ASTNode[] nodes = propertyNode.getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        LocalQuickFix fix = PropertiesQuickFixFactory.getInstance().createRemovePropertyLocalFix(property);
        holder.registerProblem(key, isOnTheFly ? PropertiesBundle.message("unused.property.problem.descriptor.name")
                                               : PropertiesBundle
                                      .message("unused.property.problem.descriptor.name.offline", property.getUnescapedKey()), fix);
      }
    };
  }

  /**
   * Extract the properties that are being committed. If no commit is in progress, return null.
   * The {@link com.intellij.openapi.vcs.checkin.CodeAnalysisBeforeCheckinHandler#getBeforeCheckinConfigurationPanel} method puts
   * into the {@link PsiFile}'s user data a closure that accepts a class and returns all the {@link PsiElement}s being committed.
   *
   * @param file the properties file that is supposed to contain the closure to extract properties that are being committed
   * @return a {@link Set} of properties that are being committed or null if no commit is in progress.
   */
  private static @Nullable Set<PsiElement> getBeingCommittedProperties(@NotNull PsiFile file) {
    final Map<Class<? extends PsiElement>, Set<PsiElement>> data = file.getUserData(InspectionProfileWrapper.PSI_ELEMENTS_BEING_COMMITTED);
    if (data == null) return null;

    return data.get(Property.class);
  }

  private static boolean isImplicitlyUsed(@NotNull Property property) {
    for (ImplicitPropertyUsageProvider provider : EP_NAME.getExtensionList()) {
      if (provider.isUsed(property)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isPropertyUsed(@NotNull Property property, @NotNull UnusedPropertiesSearchHelper helper, boolean isOnTheFly) {
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    if (original != null) {
      if (original.isCanceled()) return true;
      original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
    }

    if (isImplicitlyUsed(property)) {
      return true;
    }

    String name = property.getName();
    if (name == null) return true;

    PsiSearchHelper searchHelper = helper.getSearchHelper();
    if (mayHaveUsages(property, name, searchHelper, helper.getOwnUseScope(), isOnTheFly)) return true;

    final GlobalSearchScope widerScope = isOnTheFly ? getWidestUseScope(property.getKey(), property.getProject(), helper.getModule())
                                                    : GlobalSearchScope.projectScope(property.getProject());
    if (widerScope != null && mayHaveUsages(property, name, searchHelper, widerScope, isOnTheFly)) return true;
    return false;
  }

  private static boolean mayHaveUsages(@NotNull PsiElement property,
                                       @NotNull String name,
                                       @NotNull PsiSearchHelper psiSearchHelper,
                                       @NotNull GlobalSearchScope searchScope,
                                       boolean onTheFly) {
    GlobalSearchScope exceptPropertyFiles = createExceptPropertyFilesScope(searchScope);
    GlobalSearchScope newScope = searchScope.intersectWith(exceptPropertyFiles);

    if (onTheFly) {
      PsiSearchHelper.SearchCostResult cheapEnough = psiSearchHelper.isCheapEnoughToSearch(name, newScope, null);
      if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) return false;
      if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return true;
    }

    return ReferencesSearch.search(property, newScope, false).findFirst() != null;
  }

  private static @NotNull GlobalSearchScope createExceptPropertyFilesScope(@NotNull GlobalSearchScope origin) {
    return new DelegatingGlobalSearchScope(origin) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return super.contains(file) && !FileTypeRegistry.getInstance().isFileOfType(file, PropertiesFileType.INSTANCE);
      }
    };
  }

  private static final ExtensionPointName<ExtendedUseScopeProvider> SCOPE_EP_NAME
    = ExtensionPointName.create("com.intellij.properties.extendedUseScopeProvider");

  public static class UnusedPropertiesSearchHelper {
    private final GlobalSearchScope myOwnUseScope;
    private final Module myModule;
    private final PsiSearchHelper mySearchHelper;

    public UnusedPropertiesSearchHelper(Module module, @Nullable PsiFile psiFile) {
      myOwnUseScope = expandedDependentsScope(module, psiFile);
      myModule = module;
      mySearchHelper = PsiSearchHelper.getInstance(module.getProject());
    }

    private static @NotNull GlobalSearchScope expandedDependentsScope(Module module, @Nullable PsiFile psiFile) {
      // include the entire network of modules to the modules that also depend on the correspondingly named bundle class
      if (psiFile == null) return module.getModuleWithDependentsScope();

      GlobalSearchScope scope = module.getModuleWithDependentsScope();
      for (ExtendedUseScopeProvider provider : SCOPE_EP_NAME.getExtensionList()) {
        GlobalSearchScope extendedUseScope = provider.getExtendedUseScope(psiFile);
        if (extendedUseScope != null) {
          scope = scope.union(extendedUseScope);
        }
      }

      return scope;
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
