/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.PsiPropertiesProvider;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;

import java.util.Map;
import java.util.HashMap;

/**
 * @author yole
 */
public class PsiNestedFormLoader implements NestedFormLoader {
  private Module myModule;
  private Map<String, LwRootContainer> myFormCache = new HashMap<String, LwRootContainer>();

  public PsiNestedFormLoader(final Module module) {
    myModule = module;
  }

  public LwRootContainer loadForm(String formFileName) throws Exception {
    if (myFormCache.containsKey(formFileName)) {
      return myFormCache.get(formFileName);
    }
    VirtualFile formFile = ModuleUtil.findResourceFileInDependents(myModule, formFileName);
    if (formFile == null) {
      throw new Exception("Could not find nested form file " + formFileName);
    }
    final LwRootContainer container = Utils.getRootContainer(formFile.getInputStream(), new PsiPropertiesProvider(myModule));
    myFormCache.put(formFileName, container);
    return container;
  }
}
