// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractConvertContext extends ConvertContext {

  @Override
  public final XmlTag getTag() {
    return getInvocationElement().getXmlTag();
  }

  @Override
  public @Nullable XmlElement getXmlElement() {
    return getInvocationElement().getXmlElement();
  }

  @Override
  public final @NotNull XmlFile getFile() {
    return DomUtil.getFile(getInvocationElement());
  }

  @Override
  public Module getModule() {
    final DomFileElement<DomElement> fileElement = DomUtil.getFileElement(getInvocationElement());
    if (fileElement == null) {
      final XmlElement xmlElement = getInvocationElement().getXmlElement();
      return xmlElement == null ? null : ModuleUtilCore.findModuleForPsiElement(xmlElement);
    }
    return fileElement.isValid() ? fileElement.getRootElement().getModule() : null;
  }

  @Override
  public @Nullable GlobalSearchScope getSearchScope() {
    GlobalSearchScope scope = null;

    Module[] modules = getConvertContextModules();
    if (modules.length != 0) {

      PsiFile file = getFile();
      file = file.getOriginalFile();
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        boolean tests = TestSourcesFilter.isTestSources(virtualFile, file.getProject());
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

  private Module @NotNull [] getConvertContextModules() {
    Module[] modules = ModuleContextProvider.getModules(getFile());
    if (modules.length > 0) return modules;

    final Module module = getModule();
    if (module != null) return new Module[]{module};

    return Module.EMPTY_ARRAY;
  }
}
