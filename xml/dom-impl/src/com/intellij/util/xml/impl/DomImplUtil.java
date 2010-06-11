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
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomImplUtil");

  private DomImplUtil() {
  }

  public static boolean isTagValueGetter(final JavaMethod method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      if (method.getAnnotation(SubTag.class) != null) return false;
      if (method.getAnnotation(SubTagList.class) != null) return false;
      if (method.getAnnotation(Convert.class) != null || method.getAnnotation(Resolve.class) != null) {
        return !ReflectionCache.isAssignable(GenericDomValue.class, method.getReturnType());
      }
      if (ReflectionCache.isAssignable(DomElement.class, method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  private static boolean hasTagValueAnnotation(final JavaMethod method) {
    return method.getAnnotation(TagValue.class) != null;
  }

  public static boolean isGetter(final JavaMethod method) {
    @NonNls final String name = method.getName();
    final boolean isGet = name.startsWith("get");
    final boolean isIs = !isGet && name.startsWith("is");
    if (!isGet && !isIs) {
      return false;
    }
    if (method.getGenericParameterTypes().length != 0) {
      return false;
    }
    final Type returnType = method.getGenericReturnType();
    if (isGet) {
      return returnType != void.class;
    }
    return DomReflectionUtil.canHaveIsPropertyGetterPrefix(returnType);
  }


  public static boolean isTagValueSetter(final JavaMethod method) {
    boolean setter = method.getName().startsWith("set") && method.getGenericParameterTypes().length == 1 && method.getReturnType() == void.class;
    return setter && (hasTagValueAnnotation(method) || "setValue".equals(method.getName()));
  }

  @Nullable
  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType, boolean isAttribute) {
    Class aClass = null;
    if (isAttribute) {
      NameStrategyForAttributes annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategyForAttributes.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass == null) {
      NameStrategy annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategy.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass != null) {
      if (HyphenNameStrategy.class.equals(aClass)) return DomNameStrategy.HYPHEN_STRATEGY;
      if (JavaNameStrategy.class.equals(aClass)) return DomNameStrategy.JAVA_STRATEGY;
      try {
        return (DomNameStrategy)aClass.newInstance();
      }
      catch (InstantiationException e) {
        LOG.error(e);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  public static List<XmlTag> findSubTags(@NotNull XmlTag tag, final EvaluatedXmlName name, final XmlFile file) {
    return findSubTags(tag.getSubTags(), name, file);
  }

  public static List<XmlTag> findSubTags(final XmlTag[] tags, final EvaluatedXmlName name, final XmlFile file) {
    return ContainerUtil.findAll(tags, new Condition<XmlTag>() {
      public boolean value(XmlTag childTag) {
        return isNameSuitable(name, childTag, file);
      }
    });
  }

  public static boolean isNameSuitable(final XmlName name, final XmlTag tag, @NotNull final DomInvocationHandler handler, final XmlFile file) {
    return isNameSuitable(handler.createEvaluatedXmlName(name), tag, file);
  }

  private static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName, final XmlTag tag, final XmlFile file) {
    if (!tag.isValid()) {
      TreeElement parent = ((TreeElement) tag).getTreeParent();
      LOG.error("Invalid child tag of valid parent. Parent:" + (parent == null ? null : parent.getPsi().isValid()));
    }
    return isNameSuitable(evaluatedXmlName, tag.getLocalName(), tag.getName(), tag.getNamespace(), file);
  }

  public static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName, final String localName, final String qName, final String namespace,
                                       final XmlFile file) {
    final String localName1 = evaluatedXmlName.getXmlName().getLocalName();
    return (localName1.equals(localName) || localName1.equals(qName)) && evaluatedXmlName.isNamespaceAllowed(namespace, file);
  }

  @NotNull 
  public static XmlFileHeader getXmlFileHeader(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile instanceof LightVirtualFile && file instanceof XmlFile && FileDocumentManager.getInstance().getCachedDocument(virtualFile) == null) {
      final XmlDocument document = ((XmlFile)file).getDocument();
      if (document != null) {
        String publicId = null;
        String systemId = null;
        final XmlProlog prolog = document.getProlog();
        if (prolog != null) {
          final XmlDoctype doctype = prolog.getDoctype();
          if (doctype != null) {
            publicId = doctype.getPublicId();
            systemId = doctype.getSystemId();
          }
        }

        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return new XmlFileHeader(tag.getLocalName(), tag.getNamespace(), publicId, systemId);
        }
      }
      return XmlFileHeader.EMPTY;
    }

    if (!file.isValid()) return XmlFileHeader.EMPTY;
    return NanoXmlUtil.parseHeader(file);
  }

  @Nullable
  public static XmlName createXmlName(@NotNull String name, Type type, @Nullable JavaMethod javaMethod) {
    final Class<?> aClass = getErasure(type);
    if (aClass == null) return null;
    String key = getNamespaceKey(aClass);
    if (key == null && javaMethod != null) {
      for (final Method method : javaMethod.getHierarchy()) {
        final String key1 = getNamespaceKey(method.getDeclaringClass());
        if (key1 != null) {
          return new XmlName(name, key1);
        }
      }
    }
    return new XmlName(name, key);
  }

  @Nullable
  private static Class<?> getErasure(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getErasure(((ParameterizedType)type).getRawType());
    }
    if (type instanceof TypeVariable) {
      for (final Type bound : ((TypeVariable)type).getBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    if (type instanceof WildcardType) {
      final WildcardType wildcardType = (WildcardType)type;
      for (final Type bound : wildcardType.getUpperBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getNamespaceKey(@NotNull Class<?> type) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(type, Namespace.class);
    return namespace != null ? namespace.value() : null;
  }

  @Nullable
  public static XmlName createXmlName(@NotNull final String name, final JavaMethod method) {
    return createXmlName(name, method.getGenericReturnType(), method);
  }

  public static List<XmlTag> getCustomSubTags(final DomInvocationHandler handler, final XmlTag[] subTags, final XmlFile file) {
    final DomGenericInfoEx info = handler.getGenericInfo();
    final Set<XmlName> usedNames = new THashSet<XmlName>();
    for (final DomCollectionChildDescription description : info.getCollectionChildrenDescriptions()) {
      usedNames.add(description.getXmlName());
    }
    for (final DomFixedChildDescription description : info.getFixedChildrenDescriptions()) {
      usedNames.add(description.getXmlName());
    }
    return ContainerUtil.findAll(subTags, new Condition<XmlTag>() {
      public boolean value(final XmlTag tag) {
        if (StringUtil.isEmpty(tag.getName())) return false;

        for (final XmlName name : usedNames) {
          if (isNameSuitable(name, tag, handler, file)) {
            return false;
          }
        }
        return true;
      }
    });
  }
}
