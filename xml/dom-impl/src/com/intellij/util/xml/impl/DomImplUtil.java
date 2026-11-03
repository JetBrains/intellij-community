// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.HyphenNameStrategy;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.JavaNameStrategy;
import com.intellij.util.xml.NameStrategy;
import com.intellij.util.xml.NameStrategyForAttributes;
import com.intellij.util.xml.Namespace;
import com.intellij.util.xml.Resolve;
import com.intellij.util.xml.SubTag;
import com.intellij.util.xml.SubTagList;
import com.intellij.util.xml.TagValue;
import com.intellij.util.xml.WriteAfter;
import com.intellij.util.xml.WriteBefore;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DomImplUtil {
  private static final Logger LOG = Logger.getInstance(DomImplUtil.class);

  private DomImplUtil() {
  }

  record TagAnchors(@NotNull Set<String> before, @NotNull Set<String> after) {
  }

  static @NotNull XmlTag addOrdered(@NotNull DomInvocationHandler parentHandler,
                                    @NotNull XmlTag parentTag,
                                    @NotNull XmlTag newTag) {
    String ownName = newTag.getLocalName();
    TagAnchors anchors = parentHandler.getGenericInfo().getWriteOrder().get(ownName);
    if (anchors == null) {
      return (XmlTag)parentTag.add(newTag);
    }

    XmlTag[] subTags = parentTag.getSubTags();
    int beforeIndex = -1;
    int afterIndex = -1;
    for (int i = 0; i < subTags.length; i++) {
      String name = subTags[i].getLocalName();
      if (beforeIndex < 0 && anchors.before().contains(name)) {
        beforeIndex = i;
      }
      // a tag of the same name is an anchor too, so that the tags of one name stay together
      if (anchors.after().contains(name) || name.equals(ownName)) {
        afterIndex = i;
      }
    }

    if (beforeIndex >= 0 && afterIndex < beforeIndex) {
      return (XmlTag)parentTag.addBefore(newTag, subTags[beforeIndex]);
    }
    if (afterIndex >= 0) {
      return (XmlTag)parentTag.addAfter(newTag, subTags[afterIndex]);
    }
    return (XmlTag)parentTag.add(newTag);
  }

  static @NotNull Map<String, TagAnchors> buildWriteOrder(@NotNull Iterable<? extends AbstractDomChildrenDescription> descriptions) {
    MultiMap<String, String> before = new MultiMap<>();
    MultiMap<String, String> after = new MultiMap<>();
    for (AbstractDomChildrenDescription description : descriptions) {
      if (!(description instanceof DomChildrenDescription childDescription)) continue;
      String name = childDescription.getXmlElementName();
      for (String anchor : getWriteBeforeNames(description)) {
        before.putValue(name, anchor);
        after.putValue(anchor, name);
      }
      for (String anchor : getWriteAfterNames(description)) {
        after.putValue(name, anchor);
        before.putValue(anchor, name);
      }
    }
    if (before.isEmpty() && after.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, TagAnchors> result = new HashMap<>();
    for (String name : ContainerUtil.union(before.keySet(), after.keySet())) {
      result.put(name, new TagAnchors(new HashSet<>(before.get(name)), new HashSet<>(after.get(name))));
    }
    return result;
  }

  private static @NotNull Set<String> getWriteBeforeNames(@NotNull AbstractDomChildrenDescription description) {
    Set<String> names = new HashSet<>();
    WriteBefore single = description.getAnnotation(WriteBefore.class);
    if (single != null) {
      names.add(single.value());
    }
    WriteBefore.List list = description.getAnnotation(WriteBefore.List.class);
    if (list != null) {
      for (WriteBefore annotation : list.value()) {
        names.add(annotation.value());
      }
    }
    return names;
  }

  private static @NotNull Set<String> getWriteAfterNames(@NotNull AbstractDomChildrenDescription description) {
    Set<String> names = new HashSet<>();
    WriteAfter single = description.getAnnotation(WriteAfter.class);
    if (single != null) {
      names.add(single.value());
    }
    WriteAfter.List list = description.getAnnotation(WriteAfter.List.class);
    if (list != null) {
      for (WriteAfter annotation : list.value()) {
        names.add(annotation.value());
      }
    }
    return names;
  }

  public static void assertValidity(DomElement element, String msg) {
    if (element instanceof DomFileElementImpl) {
      final String s = ((DomFileElementImpl<?>)element).checkValidity();
      if (s != null) {
        throw new AssertionError(s);
      }
      return;
    }

    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
    assert handler != null;
    try {
      handler.assertValid();
    }
    catch (AssertionError e) {
      throw new AssertionError(msg + e.getMessage());
    }
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
        return !ReflectionUtil.isAssignable(GenericDomValue.class, method.getReturnType());
      }
      if (ReflectionUtil.isAssignable(DomElement.class, method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  private static boolean hasTagValueAnnotation(final JavaMethod method) {
    return method.getAnnotation(TagValue.class) != null;
  }

  public static boolean isGetter(final JavaMethod method) {
    final @NonNls String name = method.getName();
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

  public static @Nullable DomNameStrategy getDomNameStrategy(Class<?> rawType, boolean isAttribute) {
    Class<?> aClass = null;
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
      catch (InstantiationException | IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }


  public static @Unmodifiable List<XmlTag> findSubTags(final @NotNull XmlTag tag, final EvaluatedXmlName name, final XmlFile file) {
    return findSubTags(tag, name, file, false);
  }

  static @Unmodifiable List<XmlTag> findSubTags(final @NotNull XmlTag tag, final EvaluatedXmlName name, final XmlFile file, boolean processIncludes) {

    if (!tag.isValid()) {
      throw new AssertionError("Invalid tag");
    }
    final XmlTag[] tags = tag instanceof XmlTagImpl ? ((XmlTagImpl)tag).getSubTags(processIncludes) : tag.getSubTags();
    if (tags.length == 0) {
      return Collections.emptyList();
    }

    return ContainerUtil.findAll(tags, childTag -> {
      ProgressManager.checkCanceled();
      try {
        return isNameSuitable(name, childTag, file);
      }
      catch (PsiInvalidElementAccessException e) {
        if (!childTag.isValid()) {
          LOG.error("tag.getSubTags() returned invalid, " +
                    "tag=" + tag + ", " +
                    "containing file: " + tag.getContainingFile() +
                    "subTag.parent=" + childTag.getNode().getTreeParent());
          return false;
        }
        throw e;
      }
    });
  }

  public static @Unmodifiable List<XmlTag> findSubTags(final XmlTag[] tags, final EvaluatedXmlName name, final XmlFile file) {
    if (tags.length == 0) {
      return Collections.emptyList();
    }

    return ContainerUtil.findAll(tags, childTag -> isNameSuitable(name, childTag, file));
  }

  public static boolean isNameSuitable(final XmlName name, final XmlTag tag, final @NotNull DomInvocationHandler handler, final XmlFile file) {
    return isNameSuitable(handler.createEvaluatedXmlName(name), tag, file);
  }

  private static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName, final XmlTag tag, final XmlFile file) {
    String evaluatedLocalName = evaluatedXmlName.getXmlName().getLocalName();
    boolean qNameMatch = evaluatedLocalName.equals(tag.getName());
    return (qNameMatch || evaluatedLocalName.equals(tag.getLocalName())) &&
           evaluatedXmlName.isNamespaceAllowed(tag.getNamespace(), file, !qNameMatch);
  }

  public static @Nullable XmlName createXmlName(@NotNull String name, Type type, @Nullable JavaMethod javaMethod) {
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

  private static @Nullable Class<?> getErasure(Type type) {
    if (type instanceof Class) {
      return (Class<?>)type;
    }
    if (type instanceof ParameterizedType) {
      return getErasure(((ParameterizedType)type).getRawType());
    }
    if (type instanceof TypeVariable) {
      for (final Type bound : ((TypeVariable<?>)type).getBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    if (type instanceof WildcardType wildcardType) {
      for (final Type bound : wildcardType.getUpperBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    return null;
  }

  private static @Nullable String getNamespaceKey(@NotNull Class<?> type) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(type, Namespace.class);
    return namespace != null ? namespace.value() : null;
  }

  public static @Nullable XmlName createXmlName(final @NotNull String name, final JavaMethod method) {
    return createXmlName(name, method.getGenericReturnType(), method);
  }

  public static @Unmodifiable List<XmlTag> getCustomSubTags(final DomInvocationHandler handler, final XmlTag[] subTags, final XmlFile file) {
    if (subTags.length == 0) {
      return Collections.emptyList();
    }

    final DomGenericInfoEx info = handler.getGenericInfo();
    final Set<XmlName> usedNames = new HashSet<>();
    List<? extends DomCollectionChildDescription> collectionChildrenDescriptions = info.getCollectionChildrenDescriptions();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = collectionChildrenDescriptions.size(); i < size; i++) {
      DomCollectionChildDescription description = collectionChildrenDescriptions.get(i);
      usedNames.add(description.getXmlName());
    }
    List<? extends DomFixedChildDescription> fixedChildrenDescriptions = info.getFixedChildrenDescriptions();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = fixedChildrenDescriptions.size(); i < size; i++) {
      DomFixedChildDescription description = fixedChildrenDescriptions.get(i);
      usedNames.add(description.getXmlName());
    }
    return ContainerUtil.findAll(subTags, tag -> {
      if (StringUtil.isEmpty(tag.getLocalName())) return false;

      for (final XmlName name : usedNames) {
        if (isNameSuitable(name, tag, handler, file)) {
          return false;
        }
      }
      return true;
    });
  }

  static XmlFile getFile(DomElement domElement) {
    if (domElement instanceof DomFileElement) {
      return ((DomFileElement<?>)domElement).getFile();
    }
    DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(domElement);
    assert handler != null : domElement;
    while (true) {
      if (handler instanceof DomRootInvocationHandler) {
        return ((DomRootInvocationHandler)handler).getParent().getFile();
      }

      XmlTag tag = handler.getXmlTag();
      if (tag != null) {
        return getContainingFile(tag);
      }
      DomInvocationHandler parent = handler.getParentHandler();
      if (parent == null) {
        throw new AssertionError("No parent for " + handler.toStringEx());
      }
      handler = parent;
    }
  }

  private static XmlFile getContainingFile(XmlTag tag) {
    while (true) {
      final PsiElement parentTag = PhysicalDomParentStrategy.getParentTagCandidate(tag);
      if (!(parentTag instanceof XmlTag)) {
        return (XmlFile)tag.getContainingFile();
      }

      tag = (XmlTag)parentTag;
    }
  }
}
