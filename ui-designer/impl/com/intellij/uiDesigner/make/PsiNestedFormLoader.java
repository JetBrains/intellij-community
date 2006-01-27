/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.PsiPropertiesProvider;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;

/**
 * @author yole
 */
public class PsiNestedFormLoader implements NestedFormLoader {
  private Module myModule;

  public PsiNestedFormLoader(final Module module) {
    myModule = module;
  }

  public LwRootContainer loadForm(String formFileName) throws Exception {
    VirtualFile formFile = ModuleUtil.findResourceFileInDependents(myModule, formFileName);
    if (formFile == null) {
      throw new Exception("Could not find nested form file " + formFileName);
    }
    return Utils.getRootContainer(formFile.getInputStream(), new PsiPropertiesProvider(myModule));
  }
}
