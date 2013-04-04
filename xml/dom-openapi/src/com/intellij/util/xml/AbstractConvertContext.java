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
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class AbstractConvertContext extends ConvertContext {

  public final XmlTag getTag() {
    return getInvocationElement().getXmlTag();
  }

  @Nullable
  public XmlElement getXmlElement() {
    return getInvocationElement().getXmlElement();
  }

  @NotNull
  public final XmlFile getFile() {
    return DomUtil.getFile(getInvocationElement());
  }

  public Module getModule() {
    final DomFileElement<DomElement> fileElement = DomUtil.getFileElement(getInvocationElement());
    if (fileElement == null) {
      final XmlElement xmlElement = getInvocationElement().getXmlElement();
      return xmlElement == null ? null : ModuleUtil.findModuleForPsiElement(xmlElement);
    }
    return fileElement.getRootElement().getModule();
  }

  public PsiManager getPsiManager() {
    return getFile().getManager();
  }

  @Nullable
  public GlobalSearchScope getSearchScope() {
    GlobalSearchScope scope = null;

    Module[] modules = getConvertContextModules(this);
    if (modules.length != 0) {

      PsiFile file = getFile();
      file = file.getOriginalFile();
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
        boolean tests = fileIndex.isInTestSourceContent(virtualFile);

        for (Module module : modules) {
          if (scope == null) {
            scope = module.getModuleRuntimeScope(tests);
          }
          else {
            scope = scope.union(module.getModuleRuntimeScope(tests));
          }
        }
      }
    }
    return scope; // ??? scope == null ? GlobalSearchScope.allScope(getProject()) : scope; ???
  }

  public static GlobalSearchScope getSearchScope(@NotNull ConvertContext context) {
    Module[] modules = getConvertContextModules(context);
    if (modules.length == 0) return null;

    PsiFile file = context.getFile();
    file = file.getOriginalFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
    boolean tests = fileIndex.isInTestSourceContent(virtualFile);


    GlobalSearchScope scope = null;
    for (Module module : modules) {
      if (scope == null) {
        scope = module.getModuleRuntimeScope(tests);
      }
      else {
        scope.union(module.getModuleRuntimeScope(tests));
      }
    }
    return scope;
  }


  @NotNull
  private static Module[] getConvertContextModules(@NotNull ConvertContext context) {
    Module[] modules = ModuleContextProvider.getModules(context.getFile());
    if (modules.length > 0) return modules;

    final Module module = context.getModule();
    if (module != null) return new Module[]{module};

    return new Module[0];
  }
}
