package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyModuleType implements PyType { // Modules don't descend from object
  private final PyFile myModule;

  protected static ImmutableSet<String> ourPossibleFields = ImmutableSet.of("__name__", "__file__", "__path__", "__doc__", "__dict__");

  public PyModuleType(@NotNull PyFile source) {
    myModule = source;
  }

  public PyFile getModule() {
    return myModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(final String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      final PsiElement element = provider.resolveMember(myModule, name);
      if (element != null) {
        return ResolveResultList.to(element);
      }
    }
    final PsiElement attribute = myModule.getElementNamed(name);
    if (attribute != null) {
      return ResolveResultList.to(attribute);
    }
    if (location != null && isPackage(myModule)) {
      return resolveImplicitPackageMember(name, location);
    }
    return null;
  }

  @Nullable
  private List<? extends RatedResolveResult> resolveImplicitPackageMember(@NotNull String name, @NotNull PyExpression location) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(location);
    final PyQualifiedName packageQName = ResolveImportUtil.findCanonicalImportPath(myModule, location);
    if (owner != null && packageQName != null) {
      for (PyImportElement importElement : getVisibleImports(owner)) {
        final PyStatement stmt = importElement.getContainingImportStatement();
        PyQualifiedName importedQName = null;
        PyQualifiedName implicitSubmoduleQName = null;
        if (stmt instanceof PyFromImportStatement) {
          final PyFromImportStatement fromImportStatement = (PyFromImportStatement)stmt;
          importedQName = fromImportStatement.getImportSourceQName();
          final String visibleName = importElement.getVisibleName();
          if (importedQName != null) {
            implicitSubmoduleQName = importedQName.append(visibleName);
          }
        }
        else if (stmt instanceof PyImportStatement) {
          importedQName = importElement.getImportedQName();
        }
        final PyQualifiedName resolvedQName = packageQName.append(name);
        if ((importedQName != null && importedQName.matchesPrefix(resolvedQName)) ||
            (implicitSubmoduleQName != null && implicitSubmoduleQName.equals(resolvedQName))) {
          final PsiElement submodule = ResolveImportUtil.resolveChild(myModule, name, myModule, null, null, false, true);
          if (submodule != null) {
            final ResolveResultList results = new ResolveResultList();
            results.add(new ImportedResolveResult(submodule, RatedResolveResult.RATE_NORMAL,
                                                  Collections.<PsiElement>singletonList(importElement)));
            return results;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<PyImportElement> getVisibleImports(@NotNull ScopeOwner owner) {
    final List<PyImportElement> visibleImports = new ArrayList<PyImportElement>();
    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(PsiElement element, ResolveState state) {
        if (element instanceof PyImportElement) {
          visibleImports.add((PyImportElement)element);
        }
        return true;
      }

      @Nullable
      @Override
      public <T> T getHint(Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, @Nullable Object associated) {
      }
    }, owner, null);
    return visibleImports;
  }

  private static boolean isPackage(@NotNull PyFile file) {
    return PyUtil.turnInitIntoDir(file) != null;
  }

  /**
   * @param directory the module directory
   *
   * @return a list of submodules of the specified module directory, either files or dirs, for easier naming; may contain filenames
   *         not suitable for import.
   */
  @NotNull
  public static List<PsiFileSystemItem> getSubmodulesList(final PsiDirectory directory) {
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
    final String ext = FileUtil.getExtension(filename);
    if (SystemInfo.isWindows) {
      return "pyd".equalsIgnoreCase(ext);
    }
    else {
      return "so".equals(ext);
    }
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    Set<String> names_already = context.get(CTX_NAMES);
    List<Object> result = new ArrayList<Object>();

    ResolveImportUtil.PointInImport point = ResolveImportUtil.getPointInImport(location);
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      for (PyDynamicMember member : provider.getMembers(myModule, point)) {
        final String name = member.getName();
        result.add(LookupElementBuilder.create(name).setIcon(member.getIcon()).setTypeText(member.getShortType()));
      }
    }

    if (point == ResolveImportUtil.PointInImport.NONE || point == ResolveImportUtil.PointInImport.AS_NAME) { // when not imported from, add regular attributes
      final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location, new Condition<PsiElement>() {
        @Override
        public boolean value(PsiElement psiElement) {
          return !(psiElement instanceof PyImportElement) ||
                 PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class) instanceof PyFromImportStatement;
        }
      }, new PyUtil.UnderscoreFilter(0));
      processor.setPlainNamesOnly(point  == ResolveImportUtil.PointInImport.AS_NAME); // no parens after imported function names
      myModule.processDeclarations(processor, ResolveState.initial(), null, location);
      if (names_already != null) {
        for (LookupElement le : processor.getResultList()) {
          String name = le.getLookupString();
          if (!names_already.contains(name)) {
            result.add(le);
            names_already.add(name);
          }
        }
      }
      else {
        result.addAll(processor.getResultList());
      }
    }
    if (PyNames.INIT_DOT_PY.equals(myModule.getName())) { // our module is a dir, not a single file
      if (point == ResolveImportUtil.PointInImport.AS_MODULE || point == ResolveImportUtil.PointInImport.AS_NAME) { // when imported from somehow, add submodules
        result.addAll(getSubmoduleVariants(myModule.getContainingDirectory(), location, names_already));
      }
      else {
        addImportedSubmodules(location, names_already, result);
      }
    }
    return result.toArray();
  }

  private void addImportedSubmodules(PyExpression location, Set<String> names_already, List<Object> result) {
    PsiFile file = location.getContainingFile();
    if (file instanceof PyFile) {
      PyFile pyFile = (PyFile)file;
      PsiElement moduleBase = myModule.getName().equals(PyNames.INIT_DOT_PY) ? myModule.getContainingDirectory() : myModule;
      for (PyImportElement importElement : pyFile.getImportTargets()) {
        PsiElement target = ResolveImportUtil.resolveImportElement(importElement);
        if (target != null && PsiTreeUtil.isAncestor(moduleBase, target, true)) {
          LookupElement element = null;
          if (target instanceof PsiFileSystemItem) {
            element = buildFileLookupElement(location, names_already, (PsiFileSystemItem) target);
          }
          if (element == null && target instanceof PsiNamedElement) {
            element = LookupElementBuilder.create((PsiNamedElement)target).setIcon(target.getIcon(0));
          }
          if (element != null) {
            result.add(element);
          }
        }
      }
    }
  }

  public static List<LookupElement> getSubmoduleVariants(final PsiDirectory directory,
                                                         PsiElement location,
                                                         Set<String> names_already) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    for (PsiFileSystemItem pfsi : getSubmodulesList(directory)) {
      LookupElement lookupElement = buildFileLookupElement(location, names_already, pfsi);
      if (lookupElement != null) {
        result.add(lookupElement);
      }
    }
    return result;
  }

  private static LookupElement buildFileLookupElement(PsiElement location,
                                                      Set<String> names_already,
                                                      PsiFileSystemItem pfsi) {
    if (pfsi == location.getContainingFile().getOriginalFile()) return null;
    String s = pfsi.getName();
    int pos = s.lastIndexOf('.'); // it may not contain a dot, except in extension; cut it off.
    if (pos > 0) s = s.substring(0, pos);
    if (!PyNames.isIdentifier(s)) return null;
    if (names_already != null) {
      if (names_already.contains(s)) return null;
      else names_already.add(s);
    }
    return LookupElementBuilder.create(pfsi, s)
      .setTypeText(getPresentablePath((PsiDirectory)pfsi.getParent()))
      .setPresentableText(s)
      .setIcon(pfsi.getIcon(0));
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

  public String getName() {
    PsiFile mod = getModule();
    if (mod != null) {
      return mod.getName();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return true;
  }

  @Override
  public void assertValid() {
  }

  @NotNull
  public static Set<String> getPossibleInstanceMembers() {
    return ourPossibleFields;
  }

}
