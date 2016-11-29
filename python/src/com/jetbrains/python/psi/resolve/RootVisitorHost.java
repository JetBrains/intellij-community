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
package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class RootVisitorHost {
  public static void visitRoots(@NotNull final PsiElement elt, @NotNull final RootVisitor visitor) {
    // real search
    final Module module = ModuleUtil.findModuleForPsiElement(elt);
    if (module != null) {
      visitRoots(module, false, visitor);
    }
    else {
      final PsiFile containingFile = elt.getContainingFile();
      if (containingFile != null) {
        visitSdkRoots(containingFile, visitor);
      }
    }
  }

  public static void visitRoots(@NotNull Module module, final boolean skipSdk, final RootVisitor visitor) {
    OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).recursively();
    if (skipSdk) {
      enumerator = enumerator.withoutSdk();
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
    if (elt_vfile != null) { // reality
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
      orderEntries = fileIndex.getOrderEntriesForFile(elt_vfile);
      if (orderEntries.size() > 0) {
        for (OrderEntry entry : orderEntries) {
          if (!visitOrderEntryRoots(visitor, entry)) break;
        }
      }
      else {
        orderEntries = null;
      }
    }

    // out-of-project file or non-file(e.g. console) - use roots of SDK assigned to project
    if (orderEntries == null) {
      final Sdk sdk = ProjectRootManager.getInstance(file.getProject()).getProjectSdk();
      if (sdk != null) {
        visitSdkRoots(sdk, visitor);
      }
    }
  }

  public static boolean visitSdkRoots(@NotNull Sdk sdk, @NotNull RootVisitor visitor) {
    final VirtualFile[] roots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      if (!visitor.visitRoot(root, null, sdk, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean visitModuleContentEntries(ModuleRootModel rootModel, RootVisitor visitor) {
    // look in module sources
    Set<VirtualFile> contentRoots = Sets.newHashSet();
    for (ContentEntry entry : rootModel.getContentEntries()) {
      VirtualFile rootFile = entry.getFile();

      if (rootFile != null && !visitor.visitRoot(rootFile, null, null, true)) return false;
      contentRoots.add(rootFile);
      for (VirtualFile folder : entry.getSourceFolderFiles()) {
        if (!visitor.visitRoot(folder, rootModel.getModule(), null, true)) return false;
      }
    }
    return true;
  }

  private static boolean visitOrderEntryRoots(RootVisitor visitor, OrderEntry entry) {
    Set<VirtualFile> allRoots = new LinkedHashSet<>();
    Collections.addAll(allRoots, entry.getFiles(OrderRootType.SOURCES));
    Collections.addAll(allRoots, entry.getFiles(OrderRootType.CLASSES));
    Module module = entry instanceof ModuleOrderEntry ? ((ModuleOrderEntry) entry).getModule() : null;
    Sdk sdk = entry instanceof JdkOrderEntry ? ((JdkOrderEntry) entry).getJdk() : null;
    for (VirtualFile root : allRoots) {
      if (!visitor.visitRoot(root, module, sdk, false)) {
        return false;
      }
    }
    return true;
  }
}
