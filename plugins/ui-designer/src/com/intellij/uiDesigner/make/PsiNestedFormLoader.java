/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.uiDesigner.make;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.PsiPropertiesProvider;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.util.ClassUtil;

import java.util.Map;
import java.util.HashMap;

/**
 * @author yole
 */
public class PsiNestedFormLoader implements NestedFormLoader {
  protected Module myModule;
  private final Map<String, LwRootContainer> myFormCache = new HashMap<>();

  public PsiNestedFormLoader(final Module module) {
    myModule = module;
  }

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

  public String getClassToBindName(LwRootContainer container) {
    PsiClass psiClass =
      JavaPsiFacade.getInstance(myModule.getProject()).findClass(container.getClassToBind(), myModule.getModuleWithDependenciesScope());
    if (psiClass != null) {
      return ClassUtil.getJVMClassName(psiClass);
    }

    return container.getClassToBind();
  }
}
