// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.spellchecker;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModelKt;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.backend.evolution.EvoPyProject;
import com.jetbrains.python.PyNames;
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
    EvoPyProject pyProject = EvoPyProjectModelKt.findEvoPyProjectIfReady(module);
    PythonInterpreter interpreter = pyProject == null ? null : pyProject.getInterpreter();
    if (interpreter == null) {
      return;
    }

    final PythonSpellcheckerDictionaryGenerator generator = new PythonSpellcheckerDictionaryGenerator(module.getProject(),
                                                                                                      contentRoots[0].getPath() + "/dicts");

    VirtualFile[] roots = PythonInterpreterExtKt.classRoots(interpreter, module.getProject());
    for (VirtualFile root : roots) {
      if (root.getName().equals("Lib")) {
        generator.addFolder("python", root);
        generator.excludeFolder(root.findChild("test"));
        generator.excludeFolder(root.findChild(PyNames.SITE_PACKAGES));
      }
      else if (root.getName().equals(PyNames.SITE_PACKAGES)) {
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