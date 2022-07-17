// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public abstract class ElementPresentationManager {
  private static final ConcurrentMap<Class<?>, Optional<Method>> ourNameValueMethods = ConcurrentFactoryMap.create(key -> ReflectionUtil
      .getClassPublicMethods(key)
      .stream()
      .filter(method -> JavaMethod.getMethod(key, method).getAnnotation(NameValue.class) != null)
      .findFirst(), CollectionFactory::createConcurrentWeakKeySoftValueMap);

  private final static Function<Object, String> DEFAULT_NAMER = element -> getElementName(element);

  public static ElementPresentationManager getInstance() {
    return ApplicationManager.getApplication().getService(ElementPresentationManager.class);
  }

  public <T> Object @NotNull [] createVariants(Collection<T> elements) {
    return createVariants(elements, DEFAULT_NAMER);
  }

  public <T> Object @NotNull [] createVariants(Collection<T> elements, int iconFlags) {
    return createVariants(elements, DEFAULT_NAMER, iconFlags);
  }

  public <T> Object @NotNull [] createVariants(Collection<? extends T> elements, Function<? super T, String> namer) {
    return createVariants(elements, namer, 0);
  }

  /**
   * @deprecated use {@link com.intellij.codeInsight.lookup.LookupElementBuilder}
   */
  @Deprecated(forRemoval = true)
  public abstract Object createVariant(final Object variant, final String name, final PsiElement psiElement);

  public abstract <T> Object @NotNull [] createVariants(Collection<? extends T> elements, Function<? super T, String> namer, int iconFlags);


  public static <T>NullableFunction<T, String> NAMER() {
    return o -> getElementName(o);
  }

  public static final NullableFunction<Object, String> NAMER = o -> getElementName(o);
  public static <T> NullableFunction<T, String> namer() {
    //noinspection unchecked
    return (NullableFunction<T, String>)NAMER;
  }

  @Nullable
  public static String getElementName(@NotNull Object element) {
    Object o = invokeNameValueMethod(element);
    if (o == null || o instanceof String) return (String)o;
    if (o instanceof GenericValue) {
      final GenericValue gv = (GenericValue)o;
      final String s = gv.getStringValue();
      if (s == null) {
        final Object value = gv.getValue();
        if (value != null) {
          return String.valueOf(value);
        }
      }
      return s;
    }
    return null;
  }


  @Nullable
  public static Object invokeNameValueMethod(@NotNull final Object element) {
    return ourNameValueMethods.get(element.getClass()).map(method -> DomReflectionUtil.invokeMethod(method, element)).orElse(null);
  }

  @NlsSafe
  public static String getTypeNameForObject(Object o) {
    final Object firstImpl = ModelMergerUtil.getFirstImplementation(o);
    o = firstImpl != null ? firstImpl : o;
    String typeName = TypePresentationService.getService().getTypeName(o);
    if (typeName != null) return typeName;
    if (o instanceof DomElement) {
      final DomElement element = (DomElement)o;
      return StringUtil.capitalizeWords(element.getNameStrategy().splitIntoWords(element.getXmlElementName()), true);
    }
    return TypePresentationService.getDefaultTypeName(o.getClass());
  }

  public static Icon getIcon(@NotNull Object o) {
    if (o instanceof Iconable) {
      Icon icon = ((Iconable)o).getIcon(Iconable.ICON_FLAG_READ_STATUS);
      if (icon != null) {
        return icon;
      }
    }
    if (o instanceof DomElement) {
      return ((DomElement)o).getPresentation().getIcon();
    }

    return getIconOld(o);
  }

  @Nullable
  public static Icon getIconOld(Object o) {
    return getFirst(getIconsForClass(o.getClass(), o));
  }

  @Nullable
  private static <T> T getFirst(final T @Nullable [] array) {
    return array == null || array.length == 0 ? null : array[0];
  }


  @Nullable
  public static Icon getIconForClass(Class clazz) {
    return getFirst(getIconsForClass(clazz, null));
  }

  private static Icon @Nullable [] getIconsForClass(final Class clazz, @Nullable Object o) {
    TypePresentationService service = TypePresentationService.getService();
    final Icon icon = o == null ? service.getTypeIcon(clazz) : service.getIcon(o);
    if (icon != null) {
      return new Icon[]{icon};
    }

    return null;
  }

  @Nullable
  public static <T> T findByName(Collection<T> collection, final String name) {
    return ContainerUtil.find(collection, object -> Comparing.equal(name, getElementName(object), true));
  }

}
