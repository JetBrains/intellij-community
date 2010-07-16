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

import com.intellij.ide.IconProvider;
import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public abstract class ElementPresentationManager {
  private static final ConcurrentFactoryMap<Class,Method> ourNameValueMethods = new ConcurrentFactoryMap<Class, Method>() {
    @Nullable
    protected Method create(final Class key) {
      for (final Method method : ReflectionCache.getMethods(key)) {
      if (JavaMethod.getMethod(key, method).getAnnotation(NameValue.class) != null) {
        return method;
      }
    }
    return null;
    }
  };

  private final static Function<Object, String> DEFAULT_NAMER = new Function<Object, String>() {
    @Nullable
    public String fun(final Object element) {
      return getElementName(element);
    }
  };

  public static ElementPresentationManager getInstance() {
    return ServiceManager.getService(ElementPresentationManager.class);
  }

  @NotNull
  public <T> Object[] createVariants(Collection<T> elements) {
    return createVariants(elements, (Function<T, String>)DEFAULT_NAMER);
  }

  @NotNull
  public <T> Object[] createVariants(Collection<T> elements, int iconFlags) {
    return createVariants(elements, (Function<T, String>)DEFAULT_NAMER, iconFlags);
  }

  @NotNull
  public <T> Object[] createVariants(Collection<T> elements, Function<T, String> namer) {
    return createVariants(elements, namer, 0);
  }

  public abstract Object createVariant(final Object variant, final String name, final PsiElement psiElement);

  @NotNull
  public abstract <T> Object[] createVariants(Collection<T> elements, Function<T, String> namer, int iconFlags);


  private static final Map<Class, Icon[]> ourIcons = new HashMap<Class, Icon[]>();

  private static final List<Function<Object, String>> ourNameProviders = new ArrayList<Function<Object, String>>();
  private static final List<Function<Object, String>> ourDocumentationProviders = new ArrayList<Function<Object, String>>();
  private static final List<Function<Object, Icon>> ourIconProviders = new ArrayList<Function<Object, Icon>>();

  static {
    ourIconProviders.add(new NullableFunction<Object, Icon>() {
      public Icon fun(final Object o) {
        return o instanceof Iconable ? ((Iconable)o).getIcon(Iconable.ICON_FLAG_READ_STATUS) : null;
      }
    });
  }

  public static void registerNameProvider(Function<Object, String> function) { ourNameProviders.add(function); }
  public static void registerDocumentationProvider(Function<Object, String> function) { ourDocumentationProviders.add(function); }
  public static void registerIconProvider(Function<Object, Icon> function) { ourIconProviders.add(function); }

  public static void unregisterNameProvider(Function<Object, String> function) { ourNameProviders.remove(function); }

  public static void registerIcon(Class aClass, Icon icon) { registerIcons(aClass, icon); }
  public static void registerIcons(Class aClass, Icon... icon) { ourIcons.put(aClass, icon); }


  public static final NullableFunction<Object, String> NAMER = new NullableFunction<Object, String>() {
    public String fun(final Object o) {
      return getElementName(o);
    }
  };

  @Nullable
  public static String getElementName(Object element) {
    for (final Function<Object, String> function : ourNameProviders) {
      final String s = function.fun(element);
      if (s != null) {
        return s;
      }
    }
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
  public static String getDocumentationForElement(Object element) {
    for (final Function<Object, String> function : ourDocumentationProviders) {
      final String s = function.fun(element);
      if (s != null) {
        return s;
      }
    }
    return null;
  }

  @Nullable
  public static Object invokeNameValueMethod(final Object element) {
    final Method nameValueMethod = findNameValueMethod(element.getClass());
    if (nameValueMethod == null) {
      return null;
    }

    return DomReflectionUtil.invokeMethod(nameValueMethod, element);
  }

  public static String getTypeNameForObject(Object o) {
    final Object firstImpl = ModelMergerUtil.getFirstImplementation(o);
    o = firstImpl != null ? firstImpl : o;
    final Class<? extends Object> aClass = o.getClass();
    String s = TypeNameManager._getTypeName(aClass);
    if (s != null) {
      return s;
    }

    if (o instanceof DomElement) {
      final DomElement element = (DomElement)o;
      return StringUtil.capitalizeWords(element.getNameStrategy().splitIntoWords(element.getXmlElementName()), true);
    }
    return TypeNameManager.getDefaultTypeName(aClass);
  }

  @Nullable
  public static Icon getIcon(Object o) {
    for (final Function<Object, Icon> function : ourIconProviders) {
      final Icon icon = function.fun(o);
      if (icon != null) {
        return icon;
      }
    }
    if (o instanceof DomElement) {
      final DomElement domElement = (DomElement)o;
      final boolean dumb = DumbService.getInstance(domElement.getManager().getProject()).isDumb();

      for (final IconProvider provider : IconProvider.EXTENSION_POINT_NAME.getExtensions()) {
        if (provider instanceof DomIconProvider) {
          if (dumb && !DumbService.isDumbAware(provider)) {
            continue;
          }

          final Icon icon = ((DomIconProvider)provider).getIcon(domElement, 0);
          if (icon != null) {
            return icon;
          }
        }
      }
    }

    final Icon[] icons = getIconsForClass(o.getClass());
    if (icons != null && icons.length > 0) {
      return icons[0];
    }
    return null;
  }

  @Nullable
  public static Icon getIconOld(Object o) {
    for (final Function<Object, Icon> function : ourIconProviders) {
      final Icon icon = function.fun(o);
      if (icon != null) {
        return icon;
      }
    }
    final Icon[] icons = getIconsForClass(o.getClass());
    if (icons != null && icons.length > 0) {
      return icons[0];
    }
    return null;
  }

  @Nullable
  private static <T> T getFirst(@Nullable final T[] array) {
    return array == null || array.length == 0 ? null : array[0];
  }


  @Nullable
  public static Icon getIconForClass(Class clazz) {
    return getFirst(getIconsForClass(clazz));
  }

  @Nullable
  private static Icon[] getIconsForClass(final Class clazz) {
    final Icon icon = TypePresentationService.getService().getTypeIcon(clazz);
    if (icon != null) {
      return new Icon[]{icon};
    }

    return TypeNameManager.getFromClassMap(ourIcons, clazz);
  }

  public static Method findNameValueMethod(final Class<? extends Object> aClass) {
    synchronized (ourNameValueMethods) {
      return ourNameValueMethods.get(aClass);
    }
  }

  @Nullable
  public static <T> T findByName(Collection<T> collection, final String name) {
    return ContainerUtil.find(collection, new Condition<T>() {
      public boolean value(final T object) {
        return Comparing.equal(name, getElementName(object), true);
      }
    });
  }

}
