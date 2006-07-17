/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.07.2006
 * Time: 13:49:57
 */
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.LoaderFactory;
import com.intellij.uiDesigner.palette.Palette;

public abstract class RadComponentFactory {
  public RadComponent newInstance(Module module, String className, String id) throws ClassNotFoundException {
    final Class<?> aClass = Class.forName(className, true, LoaderFactory.getInstance(module.getProject()).getLoader(module));
    return newInstance(module, aClass, id);
  }

  protected abstract RadComponent newInstance(Module module, Class aClass, String id);

  public abstract RadComponent newInstance(final Class componentClass, final String id, final Palette palette);
}