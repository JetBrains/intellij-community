/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.intellij.psi.util.QualifiedName;
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
    final PsiElement overridingMember = resolveByOverridingMembersProviders(myModule, name);
    if (overridingMember != null) {
      return ResolveResultList.to(overridingMember);
    }
    final PsiElement attribute = myModule.getElementNamed(name);
    if (attribute != null) {
      return ResolveResultList.to(attribute);
    }
    if (PyUtil.isPackage(myModule)) {
      final List<PyImportElement> importElements = new ArrayList<PyImportElement>();
      if (myImportedModule != null && (location == null || !inSameFile(location, myImportedModule))) {
        final PyImportElement importElement = myImportedModule.getImportElement();
        if (importElement != null) {
          importElements.add(importElement);
        }
      }
      else if (location != null) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(location);
        if (owner != null) {
          importElements.addAll(getVisibleImports(owner));
        }
        if (!inSameFile(location, myModule)) {
          importElements.addAll(myModule.getImportTargets());
        }
      }
      final List<? extends RatedResolveResult> implicitMembers = resolveImplicitPackageMember(name, importElements);
      if (implicitMembers != null) {
        return implicitMembers;
      }
    }
    final PsiElement member = resolveByMembersProviders(myModule, name);
    if (member != null) {
      return ResolveResultList.to(member);
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveByMembersProviders(PyFile module, String name) {
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      if (!(provider instanceof PyOverridingModuleMembersProvider)) {
        final PsiElement element = provider.resolveMember(module, name);
        if (element != null) {
          return element;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveByOverridingMembersProviders(@NotNull PyFile module, @NotNull String name) {
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      if (provider instanceof PyOverridingModuleMembersProvider) {
        final PsiElement element = provider.resolveMember(module, name);
        if (element != null) {
          return element;
        }
      }
    }
    return null;
  }

  @Nullable
  private List<? extends RatedResolveResult> resolveImplicitPackageMember(@NotNull String name,
                                                                          @NotNull List<PyImportElement> importElements) {
    final VirtualFile moduleFile = myModule.getVirtualFile();
    if (moduleFile != null) {
      for (QualifiedName packageQName : QualifiedNameFinder.findImportableQNames(myModule, myModule.getVirtualFile())) {
        final QualifiedName resolvingQName = packageQName.append(name);
        for (PyImportElement importElement : importElements) {
          for (QualifiedName qName : getImportedQNames(importElement)) {
            if (qName.matchesPrefix(resolvingQName)) {
              final PsiElement subModule = ResolveImportUtil.resolveChild(myModule, name, myModule, false, true);
              if (subModule != null) {
                final ResolveResultList results = new ResolveResultList();
                results.add(new ImportedResolveResult(subModule, RatedResolveResult.RATE_NORMAL,
                                                      Collections.<PsiElement>singletonList(importElement)));
                return results;
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
    final List<QualifiedName> importedQNames = new ArrayList<QualifiedName>();
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
          final List<QualifiedName> results = new ArrayList<QualifiedName>();
          results.addAll(importedQNames);
          for (QualifiedName qName : importedQNames) {
            final List<String> components = new ArrayList<String>();
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
    final List<PyImportElement> visibleImports = new ArrayList<PyImportElement>();
    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        if (element instanceof PyImportElement) {
          visibleImports.add((PyImportElement)element);
        }
        return true;
      }

      @Nullable
      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, @Nullable Object associated) {
      }
    }, owner, null, null);
    return visibleImports;
  }

  /**
   * @param directory the module directory
   *
   * @return a list of submodules of the specified module directory, either files or dirs, for easier naming; may contain file names
   *         not suitable for import.
   */
  @NotNull
  private static List<PsiFileSystemItem> getSubmodulesList(final PsiDirectory directory) {
    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();

    if (directory != null) { // just in case
      // file modules
      for (PsiFile f : directory.getFiles()) {
        final String filename = f.getName();
        // if we have a binary module, we'll most likely also have a stub for it in site-packages
        if ((f instanceof PyFile && !filename.equals(PyNames.INIT_DOT_PY)) || isBinaryModule(filename)) {
          result.add(f);
        }
      }
      // dir modules
      for (PsiDirectory dir : directory.getSubdirectories()) {
        if (dir.findFile(PyNames.INIT_DOT_PY) instanceof PyFile) result.add(dir);
      }
    }
    return result;
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
    List<LookupElement> result = getCompletionVariantsAsLookupElements(location, context, false, false);
    return result.toArray();
  }

  public List<LookupElement> getCompletionVariantsAsLookupElements(PsiElement location,
                                                                   ProcessingContext context,
                                                                   boolean wantAllSubmodules, boolean suppressParentheses) {
    List<LookupElement> result = new ArrayList<LookupElement>();

    Set<String> namesAlready = context.get(CTX_NAMES);
    PointInImport point = ResolveImportUtil.getPointInImport(location);
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      for (PyDynamicMember member : provider.getMembers(myModule, point)) {
        final String name = member.getName();
        if (namesAlready != null) {
          namesAlready.add(name);
        }
        if (PyUtil.isClassPrivateName(name)) {
          continue;
        }
        result.add(LookupElementBuilder.create(name).withIcon(member.getIcon()).withTypeText(member.getShortType()));
      }
    }

    if (point == PointInImport.NONE || point == PointInImport.AS_NAME) { // when not imported from, add regular attributes
      final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location, new Condition<PsiElement>() {
        @Override
        public boolean value(PsiElement psiElement) {
          return !(psiElement instanceof PyImportElement) ||
                 PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class) instanceof PyFromImportStatement;
        }
      }, new PyUtil.UnderscoreFilter(0));
      if (suppressParentheses) {
        processor.suppressParentheses();
      }
      processor.setPlainNamesOnly(point  == PointInImport.AS_NAME); // no parens after imported function names
      myModule.processDeclarations(processor, ResolveState.initial(), null, location);
      if (namesAlready != null) {
        for (LookupElement le : processor.getResultList()) {
          String name = le.getLookupString();
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
      if (point == PointInImport.AS_MODULE || point == PointInImport.AS_NAME || wantAllSubmodules) { // when imported from somehow, add submodules
        result.addAll(getSubModuleVariants(myModule.getContainingDirectory(), location, namesAlready));
      }
      else {
        addImportedSubmodules(location, namesAlready, result);
      }
    }
    return result;
  }

  private void addImportedSubmodules(PsiElement location, Set<String> existingNames, List<LookupElement> result) {
    PsiFile file = location.getContainingFile();
    if (file instanceof PyFile) {
      PyFile pyFile = (PyFile)file;
      PsiElement moduleBase = PyUtil.isPackage(myModule) ? myModule.getContainingDirectory() : myModule;
      for (PyImportElement importElement : pyFile.getImportTargets()) {
        PsiElement target = PyUtil.turnInitIntoDir(importElement.resolve());
        if (target != null && PsiTreeUtil.isAncestor(moduleBase, target, true)) {
          LookupElement element = null;
          if (target instanceof PsiFileSystemItem) {
            element = buildFileLookupElement((PsiFileSystemItem) target, existingNames);
          }
          else if (target instanceof PsiNamedElement) {
            element = LookupElementBuilder.createWithIcon((PsiNamedElement)target);
          }
          if (element != null) {
            result.add(element);
          }
        }
      }
    }
  }

  public static List<LookupElement> getSubModuleVariants(final PsiDirectory directory,
                                                         PsiElement location,
                                                         Set<String> namesAlready) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    for (PsiFileSystemItem item : getSubmodulesList(directory)) {
      if (item != location.getContainingFile().getOriginalFile()) {
        LookupElement lookupElement = buildFileLookupElement(item, namesAlready);
        if (lookupElement != null) {
          result.add(lookupElement);
        }
      }
    }
    return result;
  }

  @Nullable
  public static LookupElementBuilder buildFileLookupElement(PsiFileSystemItem item, @Nullable Set<String> existingNames) {
    String s = FileUtil.getNameWithoutExtension(item.getName());
    if (!PyNames.isIdentifier(s)) return null;
    if (existingNames != null) {
      if (existingNames.contains(s)) return null;
      else existingNames.add(s);
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
  public boolean isBuiltin(TypeEvalContext context) {
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
