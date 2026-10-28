// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.backend.PyInterpreterLibraryKt;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonRuntimeService;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public final class RootVisitorHost {
  public static void visitRoots(final @NotNull PsiElement elt, final @NotNull RootVisitor visitor) {
    // real search
    final Module module = ModuleUtilCore.findModuleForPsiElement(elt);
    if (module != null) {
      visitRoots(module, false, visitor);
    }
    else {
      if (PythonRuntimeService.getInstance().isInScratchFile(elt)) {
        visitRootsInAllModules(elt.getProject(), visitor);
      }
      final PsiFile containingFile = elt.getContainingFile();
      if (containingFile != null) {
        visitSdkRoots(containingFile, visitor);
      }
    }
  }

  public static void visitRootsInAllModules(@NotNull Project project, @NotNull RootVisitor visitor) {
    for (Module mod : ModuleManager.getInstance(project).getModules()) {
      visitRoots(mod, true, false, visitor);
    }
  }

  public static void visitRoots(@NotNull Module module, final boolean skipSdk, final RootVisitor visitor) {
    visitRoots(module, skipSdk, true, visitor);
  }

  private static void visitRoots(@NotNull Module module, final boolean skipSdk, boolean processModuleDeps,
                                 final RootVisitor visitor) {
    OrderEnumerator enumerator = OrderEnumerator.orderEntries(module);
    if (processModuleDeps) {
      enumerator = enumerator.recursively();
    }
    if (skipSdk) {
      // The interpreter library of a module carries its interpreter as the SDK does, so it is skipped with it.
      enumerator = enumerator.withoutSdk().satisfying(entry -> !PyInterpreterLibraryKt.isPyInterpreterLibrary(entry));
    }
    else {
      // A module names its interpreter by one carrier, and the platform loads the other too. See isIgnoredInterpreterEntry.
      enumerator = enumerator.satisfying(entry -> !PyInterpreterLibraryKt.isIgnoredInterpreterEntry(entry));
    }
    enumerator.forEach(orderEntry -> {
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        return visitModuleContentEntries(((ModuleSourceOrderEntry)orderEntry).getRootModel(), visitor);
      }
      return visitOrderEntryRoots(visitor, orderEntry);
    });
  }

  static void visitSdkRoots(PsiFile file, RootVisitor visitor) {
    // formality
    final VirtualFile elt_vfile = file.getOriginalFile().getVirtualFile();
    List<OrderEntry> orderEntries = null;
    if (elt_vfile != null) { // reality TODO: check this reality
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
      orderEntries = fileIndex.getOrderEntriesForFile(elt_vfile);
      if (!orderEntries.isEmpty()) {
        for (OrderEntry entry : orderEntries) {
          if (!visitOrderEntryRoots(visitor, entry)) break;
        }
      }
      else {
        orderEntries = null;
      }
    }

    // out-of-project file or non-file(e.g. console) - use roots of the main PyProject's interpreter
    if (orderEntries == null) {
      final PythonInterpreter interpreter = MainPythonSdkKt.mainPythonInterpreter(file.getProject());
      if (interpreter != null) {
        visitInterpreterRoots(file.getProject(), interpreter, visitor);
      }
    }
  }

  /** Visits the class roots of [interpreter] in [project]. Returns whether the visitor stopped. */
  public static boolean visitInterpreterRoots(@NotNull Project project, @NotNull PythonInterpreter interpreter, @NotNull RootVisitor visitor) {
    final Sdk sdk = MainPythonSdkKt.resolveSdk(interpreter);
    for (VirtualFile root : PythonInterpreterExtKt.classRoots(interpreter, project)) {
      if (!visitor.visitRoot(root, null, sdk, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean visitModuleContentEntries(ModuleRootModel rootModel, RootVisitor visitor) {
    // look in module sources
    for (ContentEntry entry : rootModel.getContentEntries()) {
      VirtualFile rootFile = entry.getFile();

      if (rootFile != null && !visitor.visitRoot(rootFile, null, null, true)) return false;
      for (VirtualFile folder : entry.getSourceFolderFiles()) {
        if (!visitor.visitRoot(folder, rootModel.getModule(), null, true)) return false;
      }
    }
    return true;
  }

  /** The SDK of the interpreter that [entry] carries, when it is the interpreter library of its module. */
  private static @Nullable Sdk interpreterSdkOf(@NotNull OrderEntry entry) {
    return PyInterpreterLibraryKt.isInterpreterLibraryCarrier(entry) ? MainPythonSdkKt.pythonSdkIfReady(entry.getOwnerModule()) : null;
  }

  private static boolean visitOrderEntryRoots(RootVisitor visitor, OrderEntry entry) {
    Set<VirtualFile> allRoots = new LinkedHashSet<>();
    Collections.addAll(allRoots, entry.getFiles(OrderRootType.SOURCES));
    Collections.addAll(allRoots, entry.getFiles(OrderRootType.CLASSES));
    Module module = entry instanceof ModuleOrderEntry ? ((ModuleOrderEntry)entry).getModule() : null;
    Sdk sdk = entry instanceof JdkOrderEntry ? ((JdkOrderEntry)entry).getJdk() : interpreterSdkOf(entry);
    for (VirtualFile root : allRoots) {
      if (!visitor.visitRoot(root, module, sdk, false)) {
        return false;
      }
    }
    return true;
  }
}
