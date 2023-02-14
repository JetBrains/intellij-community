// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.spellchecker;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;


public class PythonSpellcheckerGenerateDictionariesAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    if (module == null) {
      return;
    }
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return;
    }
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) {
      return;
    }

    final PythonSpellcheckerDictionaryGenerator generator = new PythonSpellcheckerDictionaryGenerator(module.getProject(),
                                                                                                      contentRoots[0].getPath() + "/dicts");

    VirtualFile[] roots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      if (root.getName().equals("Lib")) {
        generator.addFolder("python", root);
        generator.excludeFolder(root.findChild("test"));
        generator.excludeFolder(root.findChild("site-packages"));
      }
      else if (root.getName().equals("site-packages")) {
        VirtualFile djangoRoot = root.findChild("django");
        if (djangoRoot != null) {
          generator.addFolder("django", djangoRoot);
        }
      }
    }

    generator.generate();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}