package com.jetbrains.python.spellchecker;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author yole
 */
public class PythonSpellcheckerGenerateDictionariesAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) {
      return;
    }
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return;
    }
    Sdk sdk = PythonSdkType.findPythonSdk(module);
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
}
