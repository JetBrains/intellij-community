// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.make;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.ClassUtil;
import com.intellij.uiDesigner.PsiPropertiesProvider;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PsiNestedFormLoader implements NestedFormLoader {
  protected Module myModule;
  private final Map<String, LwRootContainer> myFormCache = new HashMap<>();

  public PsiNestedFormLoader(final Module module) {
    myModule = module;
  }

  @Override
  public LwRootContainer loadForm(String formFileName) throws Exception {
    if (myFormCache.containsKey(formFileName)) {
      return myFormCache.get(formFileName);
    }
    VirtualFile formFile = ResourceFileUtil.findResourceFileInDependents(myModule, formFileName);
    if (formFile == null) {
      throw new Exception("Could not find nested form file " + formFileName);
    }
    final LwRootContainer container = Utils.getRootContainer(formFile.getInputStream(), new PsiPropertiesProvider(myModule));
    myFormCache.put(formFileName, container);
    return container;
  }

  @Override
  public String getClassToBindName(LwRootContainer container) {
    PsiClass psiClass =
      JavaPsiFacade.getInstance(myModule.getProject()).findClass(container.getClassToBind(), myModule.getModuleWithDependenciesScope());
    if (psiClass != null) {
      return ClassUtil.getJVMClassName(psiClass);
    }

    return container.getClassToBind();
  }
}
