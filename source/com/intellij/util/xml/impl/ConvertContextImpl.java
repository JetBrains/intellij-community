/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.Module;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public class ConvertContextImpl implements ConvertContext {
  private final XmlTag myTag;
  private final XmlFile myFile;
  private final DomInvocationHandler myHandler;
  private final Method myMethod;

  public ConvertContextImpl(final DomInvocationHandler handler, final Method method) {
    myHandler = handler;
    myMethod = method;
    myFile = handler.getFile();
    myTag = handler.getXmlTag();
  }

  public final DomElement getInvocationElement() {
    return myHandler.findCallerProxy(myMethod);
  }

  public final PsiClass findClass(String name) {
    if (name == null) return null;
    final XmlFile file = getFile();
    if (name.indexOf('$')>=0) name = name.replace('$', '.');

    // find module-based classes first, if available
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      final PsiClass aClass = file.getManager().findClass(name, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (aClass != null) return aClass;
    }
    return file.getManager().findClass(name, file.getResolveScope());
  }

  public final XmlTag getTag() {
    return myTag;
  }

  public final XmlFile getFile() {
    return myFile;
  }

  public Module getModule() {
    return myHandler.getModule();
  }


}
