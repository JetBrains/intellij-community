// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.inSameFile;

/**
 * @author yole
 */
public class PyModuleType implements PyType { // Modules don't descend from object
  @NotNull private final PyFile myModule;
  @Nullable private final PyImportedModule myImportedModule;

  public static final ImmutableSet<String> MODULE_MEMBERS = ImmutableSet.of(
    "__name__", "__file__", "__path__", "__doc__", "__dict__", "__package__");

  public PyModuleType(@NotNull PyFile source) {
    this(source, null);
  }

  public PyModuleType(@NotNull PyFile source, @Nullable PyImportedModule importedModule) {
    myModule = source;
    myImportedModule = importedModule;
  }

  @NotNull
  public PyFile getModule() {
    return myModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return resolveMemberInPackageOrModule(myImportedModule, myModule, name, location, resolveContext);
  }

  @Nullable
  public static List<? extends RatedResolveResult> resolveMemberInPackageOrModule(@Nullable PyImportedModule importedModule,
                                                                                  @NotNull PsiFileSystemItem anchor,
                                                                                  @NotNull String name,
                                                                                  @Nullable PyExpression location,
                                                                                  @NotNull PyResolveContext resolveContext) {


    final PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
    if (module != null) {
      final PsiElement overridingMember = resolveByOverridingMembersProviders(module, name, resolveContext);
      if (overridingMember != null) {
        return ResolveResultList.to(overridingMember);
      }

      final List<RatedResolveResult> attributes = module.multiResolveName(name);
      if (!attributes.isEmpty()) {
        return attributes;
      }
    }


    if (PyUtil.isPackage(anchor, location)) {
      final List<PyImportElement> importElements = new ArrayList<>();
      if (importedModule != null && (location == null || !inSameFile(location, importedModule))) {
        final PyImportElement importElement = importedModule.getImportElement();
        if (importElement != null) {
          importElements.add(importElement);
        }
      }
      else if (location != null) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(location);
        if (owner != null) {
          importElements.addAll(getVisibleImports(owner));
        }

        if (module != null) {
          if (!inSameFile(location, module)) {
            importElements.addAll(module.getImportTargets());
          }
          final List<PyFromImportStatement> imports = module.getFromImports();
          for (PyFromImportStatement anImport : imports) {
            Collections.addAll(importElements, anImport.getImportElements());
          }
        }
      }
      final List<? extends RatedResolveResult> implicitMembers = resolveImplicitPackageMember(anchor, location, name, importElements);
      if (implicitMembers != null) {
        return implicitMembers;
      }
    }

    if (module != null) {
      final PsiElement member = resolveByMembersProviders(module, name, resolveContext);
      if (member != null) {
        return ResolveResultList.to(member);
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveByMembersProviders(PyFile module, String name, @NotNull PyResolveContext resolveContext) {
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      if (!(provider instanceof PyOverridingModuleMembersProvider)) {
        final PsiElement element = provider.resolveMember(module, name, resolveContext);
        if (element != null) {
          return element;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveByOverridingMembersProviders(@NotNull PyFile module,
                                                                @NotNull String name,
                                                                @NotNull PyResolveContext resolveContext) {
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      if (provider instanceof PyOverridingModuleMembersProvider) {
        final PsiElement element = provider.resolveMember(module, name, resolveContext);
        if (element != null) {
          return element;
        }
      }
    }
    return null;
  }

  @Nullable
  private static List<? extends RatedResolveResult> resolveImplicitPackageMember(@NotNull PsiFileSystemItem moduleOrPackage,
                                                                                 @Nullable PsiElement location,
                                                                                 @NotNull String name,
                                                                                 @NotNull List<PyImportElement> importElements) {
    final VirtualFile moduleFile = moduleOrPackage.getVirtualFile();
    final PsiElement footHold = location != null ? location.getContainingFile() : moduleOrPackage;
    if (moduleFile != null) {
      for (QualifiedName packageQName : QualifiedNameFinder.findImportableQNames(footHold, moduleOrPackage.getVirtualFile())) {
        final QualifiedName resolvingQName = packageQName.append(name);
        for (PyImportElement importElement : importElements) {
          for (QualifiedName qName : getImportedQNames(importElement)) {
            if (qName.matchesPrefix(resolvingQName)) {
              final List<RatedResolveResult> submodules =
                ResolveImportUtil
                  .resolveChildren(moduleOrPackage, name, PyUtil.as(footHold, PyFile.class), false, true, false, false);
              if (!submodules.isEmpty()) {
                return ResolveResultList.asImportedResults(submodules, importElement);
              }
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<QualifiedName> getImportedQNames(@NotNull PyImportElement element) {
    final List<QualifiedName> importedQNames = new ArrayList<>();
    final PyStatement stmt = element.getContainingImportStatement();
    if (stmt instanceof PyFromImportStatement) {
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)stmt;
      final QualifiedName importedQName = fromImportStatement.getImportSourceQName();
      final String visibleName = element.getVisibleName();
      if (importedQName != null) {
        importedQNames.add(importedQName);
        final QualifiedName implicitSubModuleQName = importedQName.append(visibleName);
        if (implicitSubModuleQName != null) {
          importedQNames.add(implicitSubModuleQName);
        }
      }
      else {
        final List<PsiElement> elements =
          ResolveImportUtil.resolveFromImportStatementSource(fromImportStatement, element.getImportedQName());
        for (PsiElement psiElement : elements) {
          if (psiElement instanceof PsiFile) {
            final VirtualFile virtualFile = ((PsiFile)psiElement).getVirtualFile();
            final QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(element, virtualFile);
            if (qName != null) {
              importedQNames.add(qName);
            }
          }
        }
      }
    }
    else if (stmt instanceof PyImportStatement) {
      final QualifiedName importedQName = element.getImportedQName();
      if (importedQName != null) {
        importedQNames.add(importedQName);
      }
    }
    if (!ResolveImportUtil.isAbsoluteImportEnabledFor(element)) {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        file = file.getOriginalFile();
      }
      final QualifiedName absoluteQName = QualifiedNameFinder.findShortestImportableQName(file);
      if (file != null && absoluteQName != null) {
        final QualifiedName prefixQName = PyUtil.isPackage(file) ? absoluteQName : absoluteQName.removeLastComponent();
        if (prefixQName.getComponentCount() > 0) {
          final List<QualifiedName> results = new ArrayList<>(importedQNames);
          for (QualifiedName qName : importedQNames) {
            final List<String> components = new ArrayList<>();
            components.addAll(prefixQName.getComponents());
            components.addAll(qName.getComponents());
            results.add(QualifiedName.fromComponents(components));
          }
          return results;
        }
      }
    }
    return importedQNames;
  }

  @NotNull
  public static List<PyImportElement> getVisibleImports(@NotNull ScopeOwner owner) {
    final List<PyImportElement> visibleImports = new ArrayList<>();
    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PyImportElement) {
          visibleImports.add((PyImportElement)element);
        }
        return true;
      }
    }, owner, null, null);
    return visibleImports;
  }

  /**
   * @param directory the module directory
   * @return a list of submodules of the specified module directory, either files or dirs, for easier naming; may contain file names
   * not suitable for import.
   */
  @NotNull
  private static List<PsiFileSystemItem> getSubmodulesList(@Nullable PsiDirectory directory, @Nullable PsiElement anchor) {
    final List<PsiFileSystemItem> result = new ArrayList<>();

    if (directory != null) { // just in case
      // file modules
      for (PsiFile f : directory.getFiles()) {
        final String filename = f.getName();
        // if we have a binary module, we'll most likely also have a stub for it in site-packages
        if (!isExcluded(f) && (f instanceof PyFile && !filename.equals(PyNames.INIT_DOT_PY)) || isBinaryModule(filename)) {
          result.add(f);
        }
      }
      // dir modules
      for (PsiDirectory dir : directory.getSubdirectories()) {
        if (!isExcluded(dir) && PyUtil.isPackage(dir, anchor)) {
          result.add(dir);
        }
      }
    }
    return result;
  }

  private static boolean isExcluded(@NotNull PsiFileSystemItem file) {
    return FileIndexFacade.getInstance(file.getProject()).isExcludedFile(file.getVirtualFile());
  }

  private static boolean isBinaryModule(String filename) {
    final String ext = FileUtilRt.getExtension(filename);
    if (SystemInfo.isWindows) {
      return "pyd".equalsIgnoreCase(ext);
    }
    else {
      return "so".equals(ext);
    }
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
    final List<LookupElement> result = getCompletionVariantsAsLookupElements(location, context, false, false, typeEvalContext);
    return result.toArray();
  }

  @NotNull
  public List<LookupElement> getCompletionVariantsAsLookupElements(@NotNull PsiElement location,
                                                                   @NotNull ProcessingContext context,
                                                                   boolean wantAllSubmodules,
                                                                   boolean suppressParentheses,
                                                                   @NotNull TypeEvalContext typeEvalContext) {
    final List<LookupElement> result = new ArrayList<>();

    final Set<String> namesAlready = context.get(CTX_NAMES);
    final PointInImport point = ResolveImportUtil.getPointInImport(location);
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext);

    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      for (PyCustomMember member : provider.getMembers(myModule, point, typeEvalContext)) {
        final String name = member.getName();
        if (namesAlready != null) {
          namesAlready.add(name);
        }
        if (PyUtil.isClassPrivateName(name)) {
          continue;
        }
        final CompletionVariantsProcessor processor = createCompletionVariantsProcessor(location, suppressParentheses, point);
        final PsiElement resolved = member.resolve(location, resolveContext);
        if (resolved != null) {
          processor.execute(resolved, ResolveState.initial());
          final List<LookupElement> lookupList = processor.getResultList();
          if (!lookupList.isEmpty()) {
            final LookupElement element = lookupList.get(0);
            if (name.equals(element.getLookupString())) {
              result.add(element);
              continue;
            }
          }
        }
        result.add(LookupElementBuilder.create(name).withIcon(member.getIcon()).withTypeText(member.getShortType()));
      }
    }
    if (point == PointInImport.NONE || point == PointInImport.AS_NAME) { // when not imported from, add regular attributes
      final CompletionVariantsProcessor processor = createCompletionVariantsProcessor(location, suppressParentheses, point);
      myModule.processDeclarations(processor, ResolveState.initial(), null, location);
      if (namesAlready != null) {
        for (LookupElement le : processor.getResultList()) {
          final String name = le.getLookupString();
          if (!namesAlready.contains(name)) {
            result.add(le);
            namesAlready.add(name);
          }
        }
      }
      else {
        result.addAll(processor.getResultList());
      }
    }
    if (PyUtil.isPackage(myModule)) { // our module is a dir, not a single file
      if (point == PointInImport.AS_MODULE ||
          point == PointInImport.AS_NAME ||
          wantAllSubmodules) { // when imported from somehow, add submodules
        result.addAll(getSubModuleVariants(myModule.getContainingDirectory(), location, namesAlready));
      }
      else {
        result.addAll(collectImportedSubmodulesAsLookupElements(myModule, location, namesAlready));
      }
    }
    return result;
  }

  @NotNull
  private static CompletionVariantsProcessor createCompletionVariantsProcessor(PsiElement location,
                                                                               boolean suppressParentheses,
                                                                               PointInImport point) {
    final Condition<PsiElement> nodeFilter =
      psiElement -> !(psiElement instanceof PyImportElement) ||
                    PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class) instanceof PyFromImportStatement;

    return new CompletionVariantsProcessor(location,
                                           nodeFilter,
                                           null,
                                           point == PointInImport.AS_NAME, // no parens after imported function names
                                           suppressParentheses);
  }

  @NotNull
  public static List<LookupElement> collectImportedSubmodulesAsLookupElements(@NotNull PsiFileSystemItem pyPackage,
                                                                              @NotNull PsiElement location,
                                                                              @Nullable final Set<String> existingNames) {

    final List<PsiElement> elements = collectImportedSubmodules(pyPackage, location);
    return elements != null ? ContainerUtil.mapNotNull(elements,
                                                       (Function<PsiElement, LookupElement>)element -> {
                                                         if (element instanceof PsiFileSystemItem) {
                                                           return buildFileLookupElement((PsiFileSystemItem)element, existingNames);
                                                         }
                                                         else if (element instanceof PsiNamedElement) {
                                                           return LookupElementBuilder.createWithIcon((PsiNamedElement)element);
                                                         }
                                                         return null;
                                                       }) : Collections.emptyList();
  }

  /*TODO: extract duplicate iteration code from this method and 'resolveImplicitPackageMember' */
  @Nullable
  private static List<PsiElement> collectImportedSubmodules(@NotNull PsiFileSystemItem pyPackage, @NotNull PsiElement location) {

    if (!PyUtil.isPackage(pyPackage, location)) {
      return null;
    }
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(location);
    if (scopeOwner == null) {
      return Collections.emptyList();
    }

    final List<QualifiedName> myQnames = QualifiedNameFinder.findImportableQNames(location, pyPackage.getVirtualFile());
    final List<PsiElement> result = new ArrayList<>();
    final Set<String> seen = Sets.newHashSet();
    for (PyImportElement importElement : getVisibleImports(scopeOwner)) {
      for (QualifiedName packageQName : myQnames) {
        for (QualifiedName importedQname : getImportedQNames(importElement)) {
          if (importedQname.matchesPrefix(packageQName) && importedQname.getComponentCount() > packageQName.getComponentCount()) {
            final String directChild = importedQname.removeHead(packageQName.getComponentCount()).getFirstComponent();
            if (directChild != null && seen.add(directChild)) {
              final List<RatedResolveResult> results =
                ResolveImportUtil.resolveChildren(pyPackage, directChild, location.getContainingFile(), true, true, false, false);
              result.addAll(ResolveResultList.getElements(results));
            }
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public static List<LookupElement> getSubModuleVariants(@Nullable PsiDirectory directory,
                                                         @NotNull PsiElement location,
                                                         @Nullable Set<String> namesAlready) {
    final List<LookupElement> result = new ArrayList<>();
    for (PsiFileSystemItem item : getSubmodulesList(directory, location)) {
      if (item != location.getContainingFile().getOriginalFile()) {
        final LookupElement lookupElement = buildFileLookupElement(item, namesAlready);
        if (lookupElement != null) {
          result.add(lookupElement);
        }
      }
    }
    return result;
  }

  @Nullable
  public static LookupElementBuilder buildFileLookupElement(PsiFileSystemItem item, @Nullable Set<String> existingNames) {
    final String s = FileUtil.getNameWithoutExtension(item.getName());
    if (!PyNames.isIdentifier(s)) return null;
    if (existingNames != null) {
      if (existingNames.contains(s)) {
        return null;
      }
      else {
        existingNames.add(s);
      }
    }
    return LookupElementBuilder.create(item, s)
                               .withTypeText(getPresentablePath((PsiDirectory)item.getParent()))
                               .withPresentableText(s)
                               .withIcon(item.getIcon(0));
  }

  private static String getPresentablePath(PsiDirectory directory) {
    if (directory == null) {
      return "";
    }
    final String path = directory.getVirtualFile().getPath();
    if (path.contains(PythonSdkType.SKELETON_DIR_NAME)) {
      return "<built-in>";
    }
    return FileUtil.toSystemDependentName(path);
  }

  @Override
  public String getName() {
    return myModule.getName();
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }

  @Override
  public void assertValid(String message) {
    if (!myModule.isValid()) {
      throw new PsiInvalidElementAccessException(myModule, myModule.getClass().toString() + ": " + message);
    }
  }

  @NotNull
  public static Set<String> getPossibleInstanceMembers() {
    return MODULE_MEMBERS;
  }

}
