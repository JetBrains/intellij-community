// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.xml.highlighting.ResolvingElementQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Converter which resolves {@link DomElement}s by name in a defined scope. The scope is taken
 * from corresponding {@link DomFileDescription#getResolveScope(GenericDomValue)}.
 */
public final class DomResolveConverter<T extends DomElement> extends ResolvingConverter<T>{
  private static final Map<Class<? extends DomElement>, DomResolveConverter> ourCache =
    ConcurrentFactoryMap.createMap(key -> new DomResolveConverter(key));
  private final boolean myAttribute;
  private final SoftFactoryMap<DomElement, CachedValue<Map<String, DomElement>>> myResolveCache = new SoftFactoryMap<>() {
    @Override
    @NotNull
    protected CachedValue<Map<String, DomElement>> create(final @NotNull DomElement scope) {
      final DomManager domManager = scope.getManager();
      //noinspection ConstantConditions
      if (domManager == null) throw new AssertionError("Null DomManager for " + scope.getClass());
      final Project project = domManager.getProject();
      return CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<>() {
        @Override
        public Result<Map<String, DomElement>> compute() {
          final Map<String, DomElement> map = new HashMap<>();
          visitDomElement(scope, map);
          return new Result<>(map, PsiModificationTracker.MODIFICATION_COUNT);
        }

        private void visitDomElement(DomElement element, final Map<String, DomElement> map) {
          if (myClass.isInstance(element)) {
            final String name = ElementPresentationManager.getElementName(element);
            if (name != null && !map.containsKey(name)) {
              map.put(name, element);
            }
          }
          else {
            for (final DomElement child : DomUtil.getDefinedChildren(element, true, myAttribute)) {
              visitDomElement(child, map);
            }
          }
        }
      }, false);
    }
  };

  private final Class<T> myClass;

  public DomResolveConverter(final Class<T> aClass) {
    myClass = aClass;
    myAttribute = GenericAttributeValue.class.isAssignableFrom(myClass);
  }

  public static <T extends DomElement> DomResolveConverter<T> createConverter(Class<T> aClass) {
    return ourCache.get(aClass);
  }

  @Override
  public T fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    return (T) myResolveCache.get(getResolvingScope(context)).getValue().get(s);
  }

  @Override
  public PsiElement getPsiElement(@Nullable T resolvedValue) {
    if (resolvedValue == null) return null;
    DomTarget target = DomTarget.getTarget(resolvedValue);
    return target == null ? super.getPsiElement(resolvedValue) : PomService.convertToPsi(target);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element, String stringValue, @Nullable T resolveResult, ConvertContext context) {
    return resolveResult != null && element.getManager().areElementsEquivalent(element, resolveResult.getXmlElement());
  }

  private static DomElement getResolvingScope(final ConvertContext context) {
    final DomElement invocationElement = context.getInvocationElement();
    return invocationElement.getManager().getResolvingScope((GenericDomValue)invocationElement);
  }

  @Override
  public String getErrorMessage(final String s, final ConvertContext context) {

    return CodeInsightBundle.message("error.cannot.resolve.0.1", TypePresentationService.getService().getTypePresentableName(myClass), s);
  }

  @Override
  public String toString(final T t, final ConvertContext context) {
    if (t == null) return null;
    return ElementPresentationManager.getElementName(t);
  }

  @Override
  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    final DomElement reference = context.getInvocationElement();
    final DomElement scope = reference.getManager().getResolvingScope((GenericDomValue)reference);
    return (Collection<T>)myResolveCache.get(scope).getValue().values();
  }

  @Override
  public LocalQuickFix[] getQuickFixes(final ConvertContext context) {
    final DomElement element = context.getInvocationElement();
    final GenericDomValue value = element.createStableCopy();
    final String newName = value.getStringValue();
    if (newName == null) return LocalQuickFix.EMPTY_ARRAY;
    final DomElement scope = value.getManager().getResolvingScope(value);
    return ResolvingElementQuickFix.createFixes(newName, myClass, scope);
  }
}
