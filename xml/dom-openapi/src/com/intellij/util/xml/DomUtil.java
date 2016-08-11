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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.reflect.*;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * @author peter
 */
public class DomUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomUtil");
  public static final TypeVariable<Class<GenericValue>> GENERIC_VALUE_TYPE_VARIABLE = GenericValue.class.getTypeParameters()[0];
  private static final Class<Void> DUMMY = void.class;
  private static final Key<DomFileElement> FILE_ELEMENT_KEY = Key.create("dom file element");

  private static final ConcurrentFactoryMap<Type, Class> ourTypeParameters = new ConcurrentFactoryMap<Type, Class>() {
    @Override
    @NotNull
    protected Class create(final Type key) {
      final Class<?> result = substituteGenericType(GENERIC_VALUE_TYPE_VARIABLE, key);
      return result == null ? DUMMY : result;
    }
  };
  private static final ConcurrentFactoryMap<Couple<Type>, Class> ourVariableSubstitutions = new ConcurrentFactoryMap<Couple<Type>, Class>() {
    @Override
    @Nullable
    protected Class create(final Couple<Type> key) {
      return ReflectionUtil.substituteGenericType(key.first, key.second);
    }
  };

  public static Class extractParameterClassFromGenericType(Type type) {
    return getGenericValueParameter(type);
  }

  public static boolean isGenericValueType(Type type) {
    return getGenericValueParameter(type) != null;
  }

  @Nullable
  public static <T extends DomElement> T findByName(@NotNull Collection<T> list, @NonNls @NotNull String name) {
    for (T element: list) {
      String elementName = element.getGenericInfo().getElementName(element);
      if (elementName != null && elementName.equals(name)) {
        return element;
      }
    }
    return null;
  }

  @NotNull
  public static String[] getElementNames(@NotNull Collection<? extends DomElement> list) {
    ArrayList<String> result = new ArrayList<>(list.size());
    if (list.size() > 0) {
      for (DomElement element: list) {
        String name = element.getGenericInfo().getElementName(element);
        if (name != null) {
          result.add(name);
        }
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public static List<XmlTag> getElementTags(@NotNull Collection<? extends DomElement> list) {
    ArrayList<XmlTag> result = new ArrayList<>(list.size());
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result.add(tag);
      }
    }
    return result;
  }

  @NotNull
  public static XmlTag[] getElementTags(@NotNull DomElement[] list) {
    XmlTag[] result = new XmlTag[list.length];
    int i = 0;
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result[i++] = tag;
      }
    }
    return result;
  }

  @Nullable
  public static List<JavaMethod> getFixedPath(DomElement element) {
    assert element.isValid();
    final LinkedList<JavaMethod> methods = new LinkedList<>();
    while (true) {
      final DomElement parent = element.getParent();
      if (parent instanceof DomFileElement) {
        break;
      }
      final JavaMethod method = getGetterMethod(element, parent);
      if (method == null) {
        return null;
      }
      methods.addFirst(method);
      element = element.getParent();
    }
    return methods;
  }

  @Nullable
  private static JavaMethod getGetterMethod(final DomElement element, final DomElement parent) {
    final String xmlElementName = element.getXmlElementName();
    final String namespace = element.getXmlElementNamespaceKey();
    final DomGenericInfo genericInfo = parent.getGenericInfo();

    if (element instanceof GenericAttributeValue) {
      final DomAttributeChildDescription description = genericInfo.getAttributeChildDescription(xmlElementName, namespace);
      assert description != null;
      return description.getGetterMethod();
    }

    final DomFixedChildDescription description = genericInfo.getFixedChildDescription(xmlElementName, namespace);
    return description != null ? description.getGetterMethod(description.getValues(parent).indexOf(element)) : null;
  }

  public static Class<?> substituteGenericType(Type genericType, Type classType) {
    return ourVariableSubstitutions.get(Couple.of(genericType, classType));
  }

  @Nullable
  public static Class getGenericValueParameter(Type type) {
    final Class aClass = ourTypeParameters.get(type);
    return aClass == DUMMY ? null : aClass;
  }

  @Nullable
  public static XmlElement getValueElement(GenericDomValue domValue) {
    if (domValue instanceof GenericAttributeValue) {
      final GenericAttributeValue value = (GenericAttributeValue)domValue;
      final XmlAttributeValue attributeValue = value.getXmlAttributeValue();
      return attributeValue == null ? value.getXmlAttribute() : attributeValue;
    } else {
      return domValue.getXmlTag();
    }
  }

  public static List<? extends DomElement> getIdentitySiblings(DomElement element) {
    final GenericDomValue nameDomElement = element.getGenericInfo().getNameDomElement(element);
    if (nameDomElement == null) return Collections.emptyList();

    final NameValue nameValue = nameDomElement.getAnnotation(NameValue.class);
    if (nameValue == null || !nameValue.unique()) return Collections.emptyList();

    final String stringValue = ElementPresentationManager.getElementName(element);
    if (stringValue == null) return Collections.emptyList();

    final DomElement scope = element.getManager().getIdentityScope(element);
    if (scope == null) return Collections.emptyList();

    final DomGenericInfo domGenericInfo = scope.getGenericInfo();
    final String tagName = element.getXmlElementName();
    final DomCollectionChildDescription childDescription =
      domGenericInfo.getCollectionChildDescription(tagName, element.getXmlElementNamespaceKey());
    if (childDescription != null) {
      final ArrayList<DomElement> list = new ArrayList<>(childDescription.getValues(scope));
      list.remove(element);
      return list;
    }
    return Collections.emptyList();
  }

  public static <T> List<T> getChildrenOfType(@NotNull final DomElement parent, final Class<T> type) {
    final List<T> result = new SmartList<>();
    parent.acceptChildren(new DomElementVisitor() {
      @Override
      public void visitDomElement(final DomElement element) {
        if (type.isInstance(element)) {
          result.add((T)element);
        }
      }
    });
    return result;
  }

  public static List<DomElement> getDefinedChildren(@NotNull final DomElement parent, final boolean tags, final boolean attributes) {
    if (parent instanceof MergedObject) {
      final SmartList<DomElement> result = new SmartList<>();
      parent.acceptChildren(new DomElementVisitor() {
        @Override
        public void visitDomElement(final DomElement element) {
          if (hasXml(element)) {
            result.add(element);
          }
        }
      });
      return result;
    }

    ProgressManager.checkCanceled();

    if (parent instanceof GenericAttributeValue) return Collections.emptyList();

    if (parent instanceof DomFileElement) {
      final DomFileElement element = (DomFileElement)parent;
      return tags ? Arrays.asList(element.getRootElement()) : Collections.<DomElement>emptyList();
    }

    final XmlElement xmlElement = parent.getXmlElement();
    if (xmlElement instanceof XmlTag) {
      XmlTag tag = (XmlTag) xmlElement;
      final DomManager domManager = parent.getManager();
      final SmartList<DomElement> result = new SmartList<>();
      if (attributes) {
        for (final XmlAttribute attribute : tag.getAttributes()) {
          if (!attribute.isValid()) {
            LOG.error("Invalid attr: parent.valid=" + tag.isValid());
            continue;
          }
          GenericAttributeValue element = domManager.getDomElement(attribute);
          if (checkHasXml(attribute, element)) {
            ContainerUtil.addIfNotNull(result, element);
          }
        }
      }
      if (tags) {
        for (final XmlTag subTag : tag.getSubTags()) {
          if (!subTag.isValid()) {
            LOG.error("Invalid subtag: parent.valid=" + tag.isValid());
            continue;
          }
          DomElement element = domManager.getDomElement(subTag);
          if (checkHasXml(subTag, element)) {
            ContainerUtil.addIfNotNull(result, element);
          }
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  private static boolean checkHasXml(XmlElement psi, DomElement dom) {
    if (dom != null && !hasXml(dom)) {
      LOG.error("No xml for dom " + dom + "; attr=" + psi + ", physical=" + psi.isPhysical());
      return false;
    }
    return true;
  }

  public static <T> List<T> getDefinedChildrenOfType(@NotNull final DomElement parent, final Class<T> type, boolean tags, boolean attributes) {
    return ContainerUtil.findAll(getDefinedChildren(parent, tags, attributes), type);
  }

  public static <T> List<T> getDefinedChildrenOfType(@NotNull final DomElement parent, final Class<T> type) {
    return getDefinedChildrenOfType(parent, type, true, true);
  }

  @Nullable
  public static DomElement findDuplicateNamedValue(DomElement element, String newName) {
    return ElementPresentationManager.findByName(getIdentitySiblings(element), newName);
  }

  public static boolean isAncestor(@NotNull DomElement ancestor, @NotNull DomElement descendant, boolean strict) {
    if (!strict && ancestor.equals(descendant)) return true;
    final DomElement parent = descendant.getParent();
    return parent != null && isAncestor(ancestor, parent, false);
  }

  public static void acceptAvailableChildren(final DomElement element, final DomElementVisitor visitor) {
    final XmlTag tag = element.getXmlTag();
    if (tag != null) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        final DomElement childElement = element.getManager().getDomElement(xmlTag);
        if (childElement != null) {
          childElement.accept(visitor);
        }
      }
    }
  }

  public static Collection<Class> getAllInterfaces(final Class aClass, final Collection<Class> result) {
    final Class[] interfaces = aClass.getInterfaces();
    ContainerUtil.addAll(result, interfaces);
    if (aClass.getSuperclass() != null) {
      getAllInterfaces(aClass.getSuperclass(), result);
    }
    for (Class anInterface : interfaces) {
      getAllInterfaces(anInterface, result);
    }
    return result;
  }

  @Nullable
  public static <T> T getParentOfType(final DomElement element, final Class<T> requiredClass, final boolean strict) {
    for (DomElement curElement = strict && element != null? element.getParent() : element;
         curElement != null;
         curElement = curElement.getParent()) {
      if (requiredClass.isInstance(curElement)) {
        return (T)curElement;
      }
    }
    return null;
  }

  @Nullable
  public static <T> T getContextElement(@Nullable final Editor editor, Class<T> clazz) {
    final DomElement element = getContextElement(editor);
    return getParentOfType(element, clazz, false);
  }

  @Nullable
  public static DomElement getContextElement(@Nullable final Editor editor) {
    if(editor == null) return null;

    final Project project = editor.getProject();
    if (project == null) return null;

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (!(file instanceof XmlFile)) {
      return null;
    }

    return getDomElement(file.findElementAt(editor.getCaretModel().getOffset()));
  }

  @Nullable
  public static DomElement getDomElement(final Editor editor, final PsiFile file) {
     return getDomElement(file.findElementAt(editor.getCaretModel().getOffset()));
  }

  @Nullable
  public static DomElement getDomElement(@Nullable final PsiElement element) {
    if (element == null) return null;

    final Project project = element.getProject();
    final DomManager domManager = DomManager.getDomManager(project);
    final XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
    if (attr != null) {
      final GenericAttributeValue value = domManager.getDomElement(attr);
      if (value != null) return value;
    }

    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    while (tag != null) {
      final DomElement domElement = domManager.getDomElement(tag);
      if(domElement != null) return domElement;

      tag = tag.getParentTag();
    }
    return null;
  }

  @NotNull
  public static <T extends DomElement> T getOriginalElement(@NotNull final T domElement) {
    final XmlElement psiElement = domElement.getXmlElement();
    if (psiElement == null) return domElement;

    final PsiFile psiFile = psiElement.getContainingFile().getOriginalFile();
    final TextRange range = psiElement.getTextRange();
    final PsiElement element = psiFile.findElementAt(range.getStartOffset());
    final int maxLength = range.getLength();
    final boolean isAttribute = psiElement instanceof XmlAttribute;
    final Class<? extends XmlElement> clazz = isAttribute ? XmlAttribute.class : XmlTag.class;
    final DomManager domManager = domElement.getManager();
    DomElement current = null;
    for (XmlElement next = PsiTreeUtil.getParentOfType(element, clazz, false);
         next != null && next.getTextLength() <= maxLength;
         next = PsiTreeUtil.getParentOfType(next, clazz, true)) {
      current = isAttribute? domManager.getDomElement((XmlAttribute)next) : domManager.getDomElement((XmlTag)next);
      if (current != null && domElement.getClass() != current.getClass()) current = null;
    }
    return (T)current;
  }

  public static <T extends DomElement> T addElementAfter(@NotNull final T anchor) {
    final DomElement parent = anchor.getParent();
    final DomCollectionChildDescription childDescription = (DomCollectionChildDescription)anchor.getChildDescription();
    assert parent != null;
    final List<? extends DomElement> list = childDescription.getValues(parent);
    final int i = list.indexOf(anchor);
    assert i >= 0;
    return (T)childDescription.addValue(parent, i + 1);
  }

  @Nullable
  public static <T extends DomElement> T findDomElement(@Nullable final PsiElement element, final Class<T> beanClass) {
    return findDomElement(element, beanClass, true);
  }

  @Nullable
  public static <T extends DomElement> T findDomElement(@Nullable final PsiElement element, final Class<T> beanClass, boolean strict) {
    if (element == null) return null;

    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, strict);
    DomElement domElement;

    while (tag != null) {
      domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

      if (domElement != null) {
        return domElement.getParentOfType(beanClass, false);
      }
      tag = tag.getParentTag();
    }
    return null;
  }

  public static <T extends DomElement> DomFileElement<T> getFileElement(@NotNull DomElement element) {

    if (element instanceof DomFileElement) {
      return (DomFileElement)element;
    }
    DomFileElement fileElement = element.getUserData(FILE_ELEMENT_KEY);
    if (fileElement == null) {
      DomElement parent = element.getParent();
      if (parent != null) {
        fileElement = getFileElement(parent);
      }
      element.putUserData(FILE_ELEMENT_KEY, fileElement);
    }
    return fileElement;
  }

  @NotNull
  public static XmlFile getFile(@NotNull DomElement element) {
    return DomService.getInstance().getContainingFile(element);
  }

  /**
   * @param domElement DomElement to search root of
   * @return the topmost valid DomElement being a parent of the given one. May be and may be not DomFileElement.
   * If root tag has changed, file may lose its domness, so there will be no DomFileElement, but the inner DomElement's
   * will be still alive because the underlying XML tags are valid
   */
  @NotNull
  public static DomElement getRoot(@NotNull DomElement domElement) {
    while (true) {
      final DomElement parent = domElement.getParent();
      if (parent == null) {
        return domElement;
      }
      domElement = parent;
    }
  }

  public static boolean hasXml(@NotNull DomElement element) {
    return element.exists();
  }

  public static Pair<TextRange, PsiElement> getProblemRange(final XmlTag tag) {
    final PsiElement startToken = XmlTagUtil.getStartTagNameElement(tag);
    if (startToken == null) {
      return Pair.create(tag.getTextRange(), (PsiElement)tag);
    }

    return Pair.create(startToken.getTextRange().shiftRight(-tag.getTextRange().getStartOffset()), (PsiElement)tag);
  }

  @SuppressWarnings("ForLoopReplaceableByForEach")
  public static <T extends DomElement> List<T> getChildrenOf(DomElement parent, final Class<T> type) {
    final List<T> list = new SmartList<>();
    List<? extends AbstractDomChildrenDescription> descriptions = parent.getGenericInfo().getChildrenDescriptions();
    for (int i = 0, descriptionsSize = descriptions.size(); i < descriptionsSize; i++) {
      AbstractDomChildrenDescription description = descriptions.get(i);
      if (description.getType() instanceof Class && type.isAssignableFrom((Class<?>)description.getType())) {
        List<T> values = (List<T>)description.getValues(parent);
        for (int j = 0, valuesSize = values.size(); j < valuesSize; j++) {
          T value = values.get(j);
          if (value.exists()) {
            list.add(value);
          }
        }
      }
    }
    return list;
  }
}
