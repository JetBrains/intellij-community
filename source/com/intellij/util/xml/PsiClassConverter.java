/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PsiClassConverter extends Converter<PsiClass> implements CustomReferenceConverter<PsiClass> {

  public PsiClass fromString(final String s, final ConvertContext context) {
    final DomElement element = context.getInvocationElement();
    final GlobalSearchScope scope = element instanceof GenericDomValue ? getScope((GenericDomValue)element) : null;
    return DomJavaUtil.findClass(s, context.getFile(), context.getModule(), scope);
  }

  @Nullable
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return null;
  }

  public String toString(final PsiClass t, final ConvertContext context) {
    return t == null ? null:t.getQualifiedName();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<PsiClass> genericDomValue, PsiElement element, ConvertContext context) {

    ExtendClass extendClass = genericDomValue.getAnnotation(ExtendClass.class);
    final JavaClassReferenceProvider provider = createClassReferenceProvider(genericDomValue, context, extendClass);
    return provider.getReferencesByElement(element);
  }

  protected JavaClassReferenceProvider createClassReferenceProvider(GenericDomValue<PsiClass> genericDomValue, ConvertContext context,
                                                                    ExtendClass extendClass) {
    final JavaClassReferenceProvider provider = new JavaClassReferenceProvider(getScope(genericDomValue), context.getPsiManager().getProject());
    if (extendClass != null) {
      if (StringUtil.isNotEmpty(extendClass.value())) {
        provider.setOption(JavaClassReferenceProvider.EXTEND_CLASS_NAMES, new String[] {extendClass.value()});
      }
      if (extendClass.instantiatable()) {
        provider.setOption(JavaClassReferenceProvider.INSTANTIATABLE, Boolean.TRUE);
      }
      if (!extendClass.allowAbstract()) {
        provider.setOption(JavaClassReferenceProvider.CONCRETE, Boolean.TRUE);
      }
      if (!extendClass.allowInterface()) {
        provider.setOption(JavaClassReferenceProvider.NOT_INTERFACE, Boolean.TRUE);
      }
      if (!extendClass.allowEnum()) {
        provider.setOption(JavaClassReferenceProvider.NOT_ENUM, Boolean.TRUE);
      }
      if (extendClass.jvmFormat()) {
        provider.setOption(JavaClassReferenceProvider.JVM_FORMAT, Boolean.TRUE);
      }
      provider.setAllowEmpty(extendClass.allowEmpty());

    }

    ClassTemplate template = genericDomValue.getAnnotation(ClassTemplate.class);
    if (template != null) {
      if (StringUtil.isNotEmpty(template.value())) {
        provider.setOption(JavaClassReferenceProvider.CLASS_TEMPLATE, template.value());
      }
      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, template.kind());
    }

    provider.setSoft(true);
    return provider;
  }

  @Nullable
  protected GlobalSearchScope getScope(final GenericDomValue domValue) {
    final Module module = domValue.getModule();
    return module == null ? null : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
  }

}
