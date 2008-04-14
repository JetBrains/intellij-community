/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import com.intellij.util.xml.converters.values.ClassArrayConverter;
import com.intellij.util.xml.converters.values.ClassValueConverter;
import com.intellij.util.xml.ui.*;

/**
 * @author peter
 */
public class JavaDomComponent extends AbstractProjectComponent{
  protected JavaDomComponent(Project project, DomManager domManager, DomUIFactory factory) {
    super(project);

    final ConverterManager converterManager = domManager.getConverterManager();
    converterManager.addConverter(PsiClass.class, new PsiClassConverter());
    converterManager.addConverter(PsiType.class, new CanonicalPsiTypeConverterImpl());
    converterManager.registerConverterImplementation(JvmPsiTypeConverter.class, new JvmPsiTypeConverterImpl());
    converterManager.registerConverterImplementation(CanonicalPsiTypeConverter.class, new CanonicalPsiTypeConverterImpl());

    final ClassValueConverter classValueConverter = ClassValueConverter.getClassValueConverter(myProject);
    converterManager.registerConverterImplementation(ClassValueConverter.class, classValueConverter);
    final ClassArrayConverter classArrayConverter = ClassArrayConverter.getClassArrayConverter(myProject);
    converterManager.registerConverterImplementation(ClassArrayConverter.class, classArrayConverter);

    factory.registerCustomControl(PsiClass.class, new Function<DomWrapper<String>, BaseControl>() {
      public BaseControl fun(final DomWrapper<String> wrapper) {
        return new PsiClassControl(wrapper, false);
      }
    });
    factory.registerCustomControl(PsiType.class, new Function<DomWrapper<String>, BaseControl>() {
      public BaseControl fun(final DomWrapper<String> wrapper) {
        return new PsiTypeControl(wrapper, false);
      }
    });
  }

}
