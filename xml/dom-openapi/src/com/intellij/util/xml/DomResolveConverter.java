/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.xml.highlighting.ResolvingElementQuickFix;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Converter which resolves {@link com.intellij.util.xml.DomElement}s by name in a defined scope. The scope is taken
 * from corresponding {@link com.intellij.util.xml.DomFileDescription#getResolveScope(GenericDomValue)}.
 *
 * @author peter
 */
public class DomResolveConverter<T extends DomElement> extends ResolvingConverter<T>{
  private static final FactoryMap<Class<? extends DomElement>,DomResolveConverter> ourCache = new ConcurrentFactoryMap<Class<? extends DomElement>, DomResolveConverter>() {
    @Override
    @NotNull
    protected DomResolveConverter create(final Class<? extends DomElement> key) {
      return new DomResolveConverter(key);
    }
  };
  private final boolean myAttribute;
  private final SoftFactoryMap<DomElement, CachedValue<Map<String, DomElement>>> myResolveCache = new SoftFactoryMap<DomElement, CachedValue<Map<String, DomElement>>>() {
    @Override
    @NotNull
    protected CachedValue<Map<String, DomElement>> create(final DomElement scope) {
      final DomManager domManager = scope.getManager();
      final Project project = domManager.getProject();
      return CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Map<String, DomElement>>() {
        @Override
        public Result<Map<String, DomElement>> compute() {
          final Map<String, DomElement> map = new THashMap<>();
          visitDomElement(scope, map);
          return new Result<>(map, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
        }

        private void visitDomElement(DomElement element, final Map<String, DomElement> map) {
          if (myClass.isInstance(element)) {
            final String name = ElementPresentationManager.getElementName(element);
            if (name != null && !map.containsKey(name)) {
              map.put(name, element);
            }
          } else {
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
  public final T fromString(final String s, final ConvertContext context) {
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
  public final String toString(final T t, final ConvertContext context) {
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
    final GenericDomValue value = ((GenericDomValue)element).createStableCopy();
    final String newName = value.getStringValue();
    if (newName == null) return LocalQuickFix.EMPTY_ARRAY;
    final DomElement scope = value.getManager().getResolvingScope(value);
    return ResolvingElementQuickFix.createFixes(newName, myClass, scope);
  }
}
