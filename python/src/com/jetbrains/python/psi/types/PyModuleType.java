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
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.jetbrains.python.psi.PyUtil.inSameFile;

/**
 * @author yole
 */
public class PyModuleType implements PyType { // Modules don't descend from object
  @NotNull private final PyFile myModule;

  public static final ImmutableSet<String> MODULE_MEMBERS = ImmutableSet.of(
    "__name__", "__file__", "__path__", "__doc__", "__dict__", "__package__");

  public PyModuleType(@NotNull PyFile source) {
    myModule = source;
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
    return resolveMemberInPackageOrModule(null, myModule, name, location, resolveContext);
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
      final ResolveResultList implicitMembers = new ResolveResultList();
      processImplicitPackageMembers(anchor, location, importedModule, n -> name.endsWith(n), results -> {
        implicitMembers.addAll(convertDirsToInit(results));
        return implicitMembers.isEmpty();
      });
      if (!implicitMembers.isEmpty()) {
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

  private static void processImplicitPackageMembers(@NotNull PsiFileSystemItem anchor,
                                                    @Nullable PsiElement location,
                                                    @Nullable PyImportedModule importedModule,
                                                    @NotNull Predicate<String> filter,
                                                    @NotNull Processor<List<? extends RatedResolveResult>> resultProcessor) {
    final List<PyImportElement> importElements = new ArrayList<>();
    final PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
    if (anchor.getVirtualFile() == null) {
      return;
    }
    final PsiElement footHold = location != null ? location.getContainingFile() : module;
    if (footHold == null) {
      return;
    }
    final PyImportElement origImportElement = importedModule != null ? importedModule.getImportElement() : null;
    if (importedModule != null && (location == null || !inSameFile(location, importedModule))) {
      if (origImportElement != null) {
        importElements.add(origImportElement);
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

    if (importElements.isEmpty()) {
      return;
    }

    final Set<String> seen = Sets.newHashSet();
    if (!processImplicitlyImportedByImportElements(anchor, footHold,
                                                   importElements, name -> filter.test(name) && seen.add(name),
                                                   resultProcessor)) {
      return;
    }

    if (location != null) {
      processImplicitlyImportedByLocation(anchor, location,
                                          name -> filter.test(name) && seen.add(name),
                                          resultProcessor);
    }
  }

  private static boolean processImplicitlyImportedByImportElements(@NotNull PsiFileSystemItem anchor,
                                                                   @NotNull PsiElement footHold,
                                                                   @NotNull List<PyImportElement> importElements,
                                                                   @NotNull Predicate<String> filter,
                                                                   @NotNull Processor<List<? extends RatedResolveResult>> resultProcessor) {
    final PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
    final List<QualifiedName> packageQNames = QualifiedNameFinder.findImportableQNames(footHold, anchor.getVirtualFile());
    for (PyImportElement importElement : importElements) {
      for (QualifiedName packageQName : packageQNames) {
        for (QualifiedName importedQName : getImportedQNames(importElement)) {
          final String directChild = findFirstComponentAfterPrefix(importedQName, packageQName);
          if (directChild != null && filter.test(directChild)) {
            final List<RatedResolveResult> results =
              ResolveImportUtil.resolveChildren(anchor, directChild, module, true, true, false, false);
            if (!resultProcessor.process(ResolveResultList.asImportedResults(results, importElement))) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private static void processImplicitlyImportedByLocation(@NotNull PsiFileSystemItem anchor,
                                                          @NotNull PsiElement location,
                                                          @NotNull Predicate<String> filter,
                                                          @NotNull Processor<List<? extends RatedResolveResult>> resultProcessor) {

    if (location.getContainingFile().getVirtualFile() == null) {
      return;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(location);
    if (owner == null) {
      return;
    }

    final List<PyImportElement> visibleImports = getVisibleImports(owner);
    final PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
    final List<QualifiedName> packageQNames =
      QualifiedNameFinder.findImportableQNames(location.getContainingFile(), anchor.getVirtualFile());
    final List<QualifiedName> locationQNames =
      QualifiedNameFinder.findImportableQNames(location, location.getContainingFile().getVirtualFile());
    for (QualifiedName locationQName : locationQNames) {
      for (QualifiedName packageQName : packageQNames) {
        final String directChild = findFirstComponentAfterPrefix(locationQName, packageQName);
        if (directChild != null && filter.test(directChild)) {
          final QualifiedName mainPackage = QualifiedName.fromComponents(locationQName.getFirstComponent());
          final PyImportElement packageImportElement =
            visibleImports.stream().filter(el -> getImportedQNames(el).stream().anyMatch(qName -> qName.matchesPrefix(mainPackage)))
                          .findFirst().orElse(null);

          if (packageImportElement != null) {
            final List<RatedResolveResult> results =
              ResolveImportUtil.resolveChildren(anchor, directChild, module, true, true, false, false);
            if (!resultProcessor.process(ResolveResultList.asImportedResults(results, packageImportElement))) {
              return;
            }
          }
        }
      }
    }
  }

  @Nullable
  private static String findFirstComponentAfterPrefix(@NotNull QualifiedName qualifiedName, @NotNull QualifiedName prefix) {
    if (qualifiedName.matchesPrefix(prefix) && qualifiedName.getComponentCount() > prefix.getComponentCount()) {
      return qualifiedName.removeHead(prefix.getComponentCount()).getFirstComponent();
    }
    else {
      return null;
    }
  }

  @NotNull
  private static List<? extends RatedResolveResult> convertDirsToInit(@NotNull List<? extends RatedResolveResult> ratedResolveList) {
    return ContainerUtil.map(ratedResolveList, result -> {
      final PsiElement element = result.getElement();
      if (element instanceof PsiDirectory) {
        final PsiElement pkgInit = PyUtil.turnDirIntoInit(element);
        return pkgInit != null ? result.replace(pkgInit) : result;
      }
      else {
        return result;
      }
    });
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


  @NotNull
  private static List<QualifiedName> getImportedQNames(@NotNull PyImportElement element) {
    final List<QualifiedName> importedQNames = new ArrayList<>();
    final PyStatement stmt = element.getContainingImportStatement();
    final PyFromImportStatement fromImportStatement = ObjectUtils.tryCast(stmt, PyFromImportStatement.class);
    if (fromImportStatement != null) {
      final QualifiedName importedQName = fromImportStatement.getImportSourceQName();
      final String visibleName = element.getVisibleName();
      if (importedQName != null) {
        importedQNames.add(importedQName);
        if (visibleName != null) {
          importedQNames.add(importedQName.append(visibleName));
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
    if (!ResolveImportUtil.isAbsoluteImportEnabledFor(element) ||
        (fromImportStatement != null && fromImportStatement.getRelativeLevel() == 1)) {
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
  private static List<PyImportElement> getVisibleImports(@NotNull ScopeOwner owner) {
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


    final List<PsiElement> elements = new ArrayList<>();
    processImplicitPackageMembers(pyPackage, location, null, any -> true, results -> {

      elements.addAll(ResolveResultList.getElements(results));
      return true;
    });
    return ContainerUtil.mapNotNull(elements,
                                    element -> {
                                      if (element instanceof PsiFileSystemItem) {
                                        return buildFileLookupElement(location.getContainingFile(), (PsiFileSystemItem)element, existingNames);
                                      }
                                      else if (element instanceof PsiNamedElement) {
                                        return LookupElementBuilder.createWithIcon((PsiNamedElement)element);
                                      }
                                      return null;
                                    });
  }


  @NotNull
  public static List<LookupElement> getSubModuleVariants(@Nullable PsiDirectory directory,
                                                         @NotNull PsiElement location,
                                                         @Nullable Set<String> namesAlready) {
    final List<LookupElement> result = new ArrayList<>();
    for (PsiFileSystemItem item : getSubmodulesList(directory, location)) {
      if (item != location.getContainingFile().getOriginalFile()) {
        final LookupElement lookupElement = buildFileLookupElement(location.getContainingFile(), item, namesAlready);
        if (lookupElement != null) {
          result.add(lookupElement);
        }
      }
    }
    return result;
  }

  @Nullable
  public static LookupElementBuilder buildFileLookupElement(PsiFile file, PsiFileSystemItem item, @Nullable Set<String> existingNames) {
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
    return PyCompletionUtilsKt.createLookupElementBuilder(file, item);
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
