package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.resolve.ResolveImportUtil.PointInImport.ROLE.NONE;
import static com.jetbrains.python.psi.resolve.ResolveImportUtil.PointInImport.ROLE.AS_MODULE;
import static com.jetbrains.python.psi.resolve.ResolveImportUtil.PointInImport.ROLE.AS_NAME;
// .impl looks impure

/**
 * @author yole
 */
public class PyModuleType implements PyType { // Modules don't descend from object
  private final PyFile myModule;

  protected static ImmutableSet<String> ourPossibleFields = ImmutableSet.of("__name__", "__file__", "__path__", "__doc__", "__dict__");

  public PyModuleType(PyFile source) {
    myModule = source;
  }

  public PyFile getModule() {
    return myModule;
  }

  @Nullable
  public List<? extends PsiElement> resolveMember(final String name,
                                                  PyExpression location,
                                                  AccessDirection direction,
                                                  PyResolveContext resolveContext) {
    for(PyModuleMembersProvider provider: Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      final PsiElement element = provider.resolveMember(myModule, name);
      if (element != null) {
        return new SmartList<PsiElement>(element);
      }
    }

    //return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myModule, null, null);
    final PsiElement result = ResolveImportUtil.resolveChild(myModule, name, myModule, null, false);
    if (result != null) return new SmartList<PsiElement>(result);
    return Collections.emptyList();
  }


  /**
   * @return a list of submodules of this module, either files or dirs, for easier naming; may contain filenames
   *         not suitable for import.
   */
  @NotNull
  public List<PsiFileSystemItem> getSubmodulesList() {
    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();

    if (PyNames.INIT_DOT_PY.equals(myModule.getName())) { // our module is a dir, not a single file
      PsiDirectory mydir = myModule.getContainingDirectory();
      if (mydir != null) { // just in case
        // file modules
        for (PsiFile f : mydir.getFiles()) {
          final String filename = f.getName();
          // if we have a binary module, we'll most likely also have a stub for it in site-packages
          if ((f instanceof PyFile && !filename.equals(PyNames.INIT_DOT_PY)) || isBinaryModule(filename)) {
            result.add(f);
          }
        }
        // dir modules
        for (PsiDirectory dir : mydir.getSubdirectories()) {
          if (dir.findFile(PyNames.INIT_DOT_PY) instanceof PyFile) result.add(dir);
        }
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

    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      for (PyDynamicMember member : provider.getMembers(myModule)) {
        final String name = member.getName();
        result.add(LookupElementBuilder.create(name).setIcon(member.getIcon()).setTypeText(member.getShortType()));
      }
    }

    ResolveImportUtil.PointInImport point = ResolveImportUtil.getPointInImport(location);
    if (point.role == NONE || point.role == AS_NAME) { // when not imported from, add regular attributes
      final VariantsProcessor processor = new VariantsProcessor(location);
      processor.setPlainNamesOnly(point.role == AS_NAME); // no parens after imported function names
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
    if (point.role == AS_MODULE || point.role == AS_NAME) { // when imported from somehow, add submodules
      for (PsiFileSystemItem pfsi : getSubmodulesList()) {
        if (pfsi == location.getContainingFile().getOriginalFile()) continue;
        String s = pfsi.getName();
        int pos = s.lastIndexOf('.'); // it may not contain a dot, except in extension; cut it off.
        if (pos > 0) s = s.substring(0, pos);
        if (!PyNames.isIdentifier(s)) continue;
        if (names_already != null) {
          if (names_already.contains(s)) continue;
          else names_already.add(s);
        }
        result.add(LookupElementBuilder.create(pfsi, s).setPresentableText(s).setIcon(pfsi.getIcon(0)));
      }
    }
    return result.toArray();
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
  public boolean isBuiltin() {
    return true;
  }

  @NotNull
  public static Set<String> getPossibleInstanceMembers() {
    return ourPossibleFields;
  }

}
