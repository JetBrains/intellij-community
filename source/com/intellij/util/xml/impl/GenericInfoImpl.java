/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class GenericInfoImpl implements DomGenericInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.GenericInfoImpl");
  private final Class<? extends DomElement> myClass;
  private DomManagerImpl myDomManager;
  private final BidirectionalMap<JavaMethodSignature, Pair<String, Integer>> myFixedChildrenMethods =
    new BidirectionalMap<JavaMethodSignature, Pair<String, Integer>>();
  private final Map<String, Integer> myFixedChildrenCounts = new HashMap<String, Integer>();
  private final Map<JavaMethodSignature, String> myCollectionChildrenGetterMethods = new HashMap<JavaMethodSignature, String>();
  private final Map<JavaMethodSignature, String> myCollectionChildrenAdditionMethods = new HashMap<JavaMethodSignature, String>();
  private final Map<String, Type> myCollectionChildrenClasses = new HashMap<String, Type>();
  private final Map<JavaMethodSignature, String> myAttributeChildrenMethods = new HashMap<JavaMethodSignature, String>();
  private final Map<JavaMethodSignature, Set<String>> myCompositeChildrenMethods = new HashMap<JavaMethodSignature, Set<String>>();
  private final Map<JavaMethodSignature, Pair<String, Set<String>>> myCompositeCollectionAdditionMethods =
    new HashMap<JavaMethodSignature, Pair<String, Set<String>>>();
  private final Map<JavaMethodSignature,Required> myRequiredChildrenGetters = new HashMap<JavaMethodSignature, Required>();
  @Nullable private Method myNameValueGetter;
  private boolean myValueElement;
  private boolean myInitialized;
  private static final HashSet ADDER_PARAMETER_TYPES = new HashSet<Class>(Arrays.asList(Class.class, int.class));

  public GenericInfoImpl(final Class<? extends DomElement> aClass, final DomManagerImpl domManager) {
    myClass = aClass;
    myDomManager = domManager;
  }

  final int getFixedChildrenCount(String qname) {
    final Integer integer = myFixedChildrenCounts.get(qname);
    return integer == null ? 0 : integer;
  }

  final JavaMethodSignature getFixedChildGetter(final Pair<String, Integer> pair) {
    return myFixedChildrenMethods.getKeysByValue(pair).get(0);
  }

  final Set<Map.Entry<JavaMethodSignature, String>> getCollectionChildrenEntries() {
    return myCollectionChildrenGetterMethods.entrySet();
  }

  final Type getCollectionChildrenType(String tagName) {
    return myCollectionChildrenClasses.get(tagName);
  }

  final Set<Map.Entry<JavaMethodSignature, String>> getAttributeChildrenEntries() {
    return myAttributeChildrenMethods.entrySet();
  }

  final Set<String> getFixedChildrenNames() {
    return myFixedChildrenCounts.keySet();
  }

  final Set<String> getCollectionChildrenNames() {
    return myCollectionChildrenClasses.keySet();
  }

  final Collection<String> getAttributeChildrenNames() {
    return myAttributeChildrenMethods.values();
  }

  final Pair<String, Integer> getFixedChildInfo(JavaMethodSignature method) {
    return myFixedChildrenMethods.get(method);
  }

  final String getAttributeName(JavaMethodSignature method) {
    return myAttributeChildrenMethods.get(method);
  }

  private static boolean isCoreMethod(final Method method) {
    final Class<?> aClass = method.getDeclaringClass();
    return aClass.isAssignableFrom(DomElement.class) || aClass.equals(GenericAttributeValue.class);
  }

  @Nullable
  private String getSubTagName(final JavaMethodSignature method) {
    final SubTag subTagAnnotation = method.findAnnotation(SubTag.class, myClass);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method, false);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final JavaMethodSignature method) {
    final SubTagList subTagList = method.findAnnotation(SubTagList.class, myClass);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      return propertyName != null ? getNameStrategy(false).convertName(StringUtil.unpluralize(propertyName)) : null;
    }
    return subTagList.value();
  }

  @Nullable
  private String getNameFromMethod(final JavaMethodSignature method, boolean isAttribute) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy(isAttribute).convertName(propertyName);
  }

  private static String getPropertyName(JavaMethodSignature method) {
    return PropertyUtil.getPropertyName(method.getMethodName());
  }

  @NotNull
  private DomNameStrategy getNameStrategy(boolean isAttribute) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(DomUtil.getRawType(myClass), isAttribute);
    if (strategy != null) {
      return strategy;
    }
    else {
      return DomNameStrategy.HYPHEN_STRATEGY;
    }
  }

  public final synchronized void buildMethodMaps() {
    if (myInitialized) return;

    myInitialized = true;

    final Set<Method> methods = new HashSet<Method>(Arrays.asList(myClass.getMethods()));
    final Set<JavaMethodSignature> removedSignatures = new HashSet<JavaMethodSignature>();

    final Class implClass = myDomManager.getImplementation(myClass);
    if (implClass != null) {
      for (Method method : implClass.getMethods()) {
        if (!Modifier.isAbstract(method.getModifiers())) {
          removedSignatures.add(JavaMethodSignature.getSignature(method));
        }
      }
      for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext();) {
        final Method method = iterator.next();
        try {
          if (!Modifier.isAbstract(implClass.getMethod(method.getName(), method.getParameterTypes()).getModifiers())) {
            iterator.remove();
          }
        }
        catch (NoSuchMethodException e) {
        }
      }
    }

    for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext();) {
      final Method method = iterator.next();

      final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
      final Required required = DomUtil.findAnnotationDFS(method, Required.class);
      if (required != null) {
        myRequiredChildrenGetters.put(signature, required);
      }
      if (isCoreMethod(method) || DomImplUtil.isTagValueSetter(method) || isCustomMethod(signature)) {
        if (signature.findAnnotation(NameValue.class, myClass) != null) {
          myNameValueGetter = method;
        }
        removedSignatures.add(signature);
        iterator.remove();
      }
    }

    for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext();) {
      Method method = iterator.next();
      if (DomImplUtil.isGetter(method) && processGetterMethod(method)) {
        final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
        if (signature.findAnnotation(NameValue.class, myClass) != null) {
          myNameValueGetter = method;
        }
        removedSignatures.add(signature);
        iterator.remove();
      }
    }

    for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext();) {
      Method method = iterator.next();
      final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
      final SubTagsList subTagsList = signature.findAnnotation(SubTagsList.class, myClass);
      if (subTagsList != null && method.getName().startsWith("add")) {
        final String tagName = subTagsList.tagName();
        assert StringUtil.isNotEmpty(tagName);
        final Set<String> set = new HashSet<String>(Arrays.asList(subTagsList.value()));
        assert set.contains(tagName);
        myCompositeCollectionAdditionMethods.put(signature, Pair.create(tagName, set));
        iterator.remove();
      }
      else if (isAddMethod(method, signature)) {
        myCollectionChildrenAdditionMethods.put(signature, extractTagName(signature, "add"));
        removedSignatures.add(JavaMethodSignature.getSignature(method));
        iterator.remove();
      }
    }
    for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext();) {
      Method method = iterator.next();
      final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
      if (removedSignatures.contains(signature)) {
        iterator.remove();
      }
    }

    if (false) {
      if (!methods.isEmpty()) {
        StringBuilder sb = new StringBuilder(myClass + " should provide the following implementations:");
        for (Method method : methods) {
          sb.append("\n  " + method);
        }
        assert false : sb.toString();
        //System.out.println(sb.toString());
      }
    }

  }

  private boolean isAddMethod(Method method, JavaMethodSignature signature) {
    final String tagName = extractTagName(signature, "add");
    if (tagName == null) return false;

    final Type childrenClass = getCollectionChildrenType(tagName);
    if (childrenClass == null || !DomUtil.getRawType(childrenClass).isAssignableFrom(method.getReturnType())) return false;

    return ADDER_PARAMETER_TYPES.containsAll(Arrays.asList(method.getParameterTypes()));
  }

  @Nullable
  private String extractTagName(JavaMethodSignature method, @NonNls String prefix) {
    final String name = method.getMethodName();
    if (!name.startsWith(prefix)) return null;

    final SubTagList subTagAnnotation = method.findAnnotation(SubTagList.class, myClass);
    if (subTagAnnotation != null && !StringUtil.isEmpty(subTagAnnotation.value())) {
      return subTagAnnotation.value();
    }

    final String tagName = getNameStrategy(false).convertName(name.substring(prefix.length()));
    return StringUtil.isEmpty(tagName) ? null : tagName;
  }

  private boolean processGetterMethod(final Method method) {
    if (DomImplUtil.isTagValueGetter(method)) {
      myValueElement = true;
      return true;
    }

    final boolean isAttributeValueMethod = method.getReturnType().equals(GenericAttributeValue.class);
    final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    final Attribute annotation = signature.findAnnotation(Attribute.class, myClass);
    final boolean isAttributeMethod = annotation != null || isAttributeValueMethod;
    if (annotation != null) {
      assert
        isAttributeValueMethod || method.getReturnType().isAssignableFrom(GenericAttributeValue.class) :
        method + " should return " + GenericAttributeValue.class;
    }
    if (isAttributeMethod) {
      final String s = annotation == null ? null : annotation.value();
      String attributeName = StringUtil.isEmpty(s) ? getNameFromMethod(signature, true) : s;
      assert StringUtil.isNotEmpty(attributeName) : "Can't guess attribute name from method name: " + method.getName();
      myAttributeChildrenMethods.put(signature, attributeName);
      return true;
    }

    if (isDomElement(method.getReturnType())) {
      final String qname = getSubTagName(signature);
      if (qname != null) {
        assert!isCollectionChild(qname) : "Collection and fixed children cannot intersect: " + qname;
        int index = 0;
        final SubTag subTagAnnotation = signature.findAnnotation(SubTag.class, myClass);
        if (subTagAnnotation != null && subTagAnnotation.index() != 0) {
          index = subTagAnnotation.index();
        }
        myFixedChildrenMethods.put(signature, new Pair<String, Integer>(qname, index));
        final Integer integer = myFixedChildrenCounts.get(qname);
        if (integer == null || integer < index + 1) {
          myFixedChildrenCounts.put(qname, index + 1);
        }
        return true;
      }
    }

    final Type type = DomImplUtil.extractCollectionElementType(method.getGenericReturnType());
    if (isDomElement(type)) {
      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null) {
        myCompositeChildrenMethods.put(signature, new HashSet<String>(Arrays.asList(subTagsList.value())));
        return true;
      }

      final String qname = getSubTagNameForCollection(signature);
      if (qname != null) {
        assert!isFixedChild(qname) : "Collection and fixed children cannot intersect: " + qname;
        myCollectionChildrenClasses.put(qname, type);
        myCollectionChildrenGetterMethods.put(signature, qname);
        return true;
      }
    }

    return false;
  }

  private boolean isCustomMethod(final JavaMethodSignature method) {
    return method.findAnnotation(PropertyAccessor.class, myClass) != null;
  }

  public final Invocation createInvocation(final Method method) {
    buildMethodMaps();

    final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
    final PropertyAccessor accessor = signature.findAnnotation(PropertyAccessor.class, myClass);
    if (accessor != null) {
      return createPropertyAccessorInvocation(accessor);
    }

    if (myAttributeChildrenMethods.containsKey(signature)) {
      return new GetAttributeChildInvocation(signature);
    }

    if (myFixedChildrenMethods.containsKey(signature)) {
      return new GetFixedChildInvocation(signature);
    }

    final Set<String> qnames = myCompositeChildrenMethods.get(signature);
    if (qnames != null) {
      return new Invocation() {
        public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
          for (final String qname : qnames) {
            handler.checkInitialized(qname);
          }
          final XmlTag tag = handler.getXmlTag();
          if (tag == null) return Collections.emptyList();

          final List<DomElement> list = new ArrayList<DomElement>();
          for (final XmlTag subTag : tag.getSubTags()) {
            if (qnames.contains(subTag.getLocalName())) {
              final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
              if (element != null) {
                list.add(element.getProxy());
              }
            }
          }
          return list;
        }
      };
    }

    final Pair<String, Set<String>> pair = myCompositeCollectionAdditionMethods.get(signature);
    if (pair != null) {
      final Set<String> qnames1 = pair.second;
      final String tagName = pair.first;
      final Type type = method.getGenericReturnType();
      return new Invocation() {
        public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
          final VirtualFile virtualFile = handler.getFile().getVirtualFile();
          if (virtualFile != null && !virtualFile.isWritable()) {
            VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(virtualFile);
            return null;
          }

          for (final String qname : qnames1) {
            handler.checkInitialized(qname);
          }

          final XmlTag tag = handler.ensureTagExists();
          int index = args != null && args.length == 1 ? (Integer)args[0] : Integer.MAX_VALUE;

          XmlTag lastTag = null;
          int i = 0;
          final XmlTag[] tags = tag.getSubTags();
          for (final XmlTag subTag : tags) {
            if (i == index) break;
            if (qnames1.contains(subTag.getLocalName())) {
              final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
              if (element != null) {
                lastTag = subTag;
                i++;
              }
            }
          }
          final DomManagerImpl manager = handler.getManager();
          final boolean b = manager.setChanging(true);
          try {
            final XmlTag emptyTag = tag.getManager().getElementFactory().createTagFromText("<" + tagName + "/>");
            final XmlTag newTag;
            if (lastTag == null) {
              if (tags.length == 0) {
                newTag = (XmlTag)tag.add(emptyTag);
              }
              else {
                newTag = (XmlTag)tag.addBefore(emptyTag, tags[0]);
              }
            }
            else {
              newTag = (XmlTag)tag.addAfter(emptyTag, lastTag);
            }
            return handler.createCollectionElement(type, newTag);
          }
          finally {
            manager.setChanging(b);
          }
        }
      };
    }

    String qname = myCollectionChildrenGetterMethods.get(signature);
    if (qname != null) {
      return new GetCollectionChildInvocation(qname);
    }

    qname = myCollectionChildrenAdditionMethods.get(signature);
    if (qname != null) {
      return new AddChildInvocation(getTypeGetter(method), getIndexGetter(method), qname, myCollectionChildrenClasses.get(qname));
    }

    throw new UnsupportedOperationException("No implementation for method " + method.toString() + " in class " + myClass);
  }

  private Invocation createPropertyAccessorInvocation(final PropertyAccessor accessor) {
    final String[] names = accessor.value();
    final Method[] methods = new Method[names.length];
    Class aClass = myClass;
    for (int i = 0; i < names.length; i++) {
      final Method getter = findGetter(aClass, names[i]);
      assert getter != null : "Couldn't find getter for property " + names[i] + " in class " + aClass;
      methods[i] = getter;
      aClass = getter.getReturnType();
      if (List.class.isAssignableFrom(aClass)) {
        aClass = DomUtil.getRawType(DomImplUtil.extractCollectionElementType(getter.getGenericReturnType()));
      }
    }
    final int lastElement = methods.length - 1;
    return new Invocation() {
      public final Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
        return invoke(0, handler.getProxy());
      }

      private Object invoke(final int i, final Object object) throws IllegalAccessException, InvocationTargetException {
        final Object o = methods[i].invoke(object);
        if (i == lastElement) return o;

        if (o instanceof List) {
          List result = new ArrayList();
          for (Object o1 : (List)o) {
            result.add(invoke(i + 1, o1));
          }
          return result;
        }
        return invoke(i + 1, o);
      }
    };
  }

  @Nullable
  private static Method findGetter(Class aClass, String propertyName) {
    final String capitalized = StringUtil.capitalize(propertyName);
    try {
      return aClass.getMethod("get" + capitalized);
    }
    catch (NoSuchMethodException e) {
      final Method method;
      try {
        method = aClass.getMethod("is" + capitalized);
        return DomImplUtil.canHaveIsPropertyGetterPrefix(method.getGenericReturnType()) ? method : null;
      }
      catch (NoSuchMethodException e1) {
        return null;
      }
    }
  }

  private static Function<Object[], Type> getTypeGetter(final Method method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(Class.class)) {
      return new Function<Object[], Type>() {
        public Type fun(final Object[] s) {
          return (Type)s[0];
        }
      };
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(Class.class)) {
      return new Function<Object[], Type>() {
        public Type fun(final Object[] s) {
          return (Type)s[1];
        }
      };
    }

    return new Function<Object[], Type>() {
      public Type fun(final Object[] s) {
        return method.getGenericReturnType();
      }
    };
  }


  private static Function<Object[], Integer> getIndexGetter(final Method method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(int.class)) {
      return new Function<Object[], Integer>() {
        public Integer fun(final Object[] s) {
          return (Integer)s[0];
        }
      };
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(int.class)) {
      return new Function<Object[], Integer>() {
        public Integer fun(final Object[] s) {
          return (Integer)s[1];
        }
      };
    }

    return new Function<Object[], Integer>() {
      public Integer fun(final Object[] s) {
        return Integer.MAX_VALUE;
      }
    };
  }

  @Nullable
  private Method findGetterMethod(final Map<JavaMethodSignature, String> map, final String xmlElementName) {
    buildMethodMaps();
    for (Map.Entry<JavaMethodSignature, String> entry : map.entrySet()) {
      if (xmlElementName.equals(entry.getValue())) {
        return entry.getKey().findMethod(myClass);
      }
    }
    return null;
  }

  @Nullable
  private Method getCollectionAddMethod(final String tagName, Class... parameterTypes) {
    for (Map.Entry<JavaMethodSignature, String> entry : myCollectionChildrenAdditionMethods.entrySet()) {
      if (tagName.equals(entry.getValue())) {
        final JavaMethodSignature method = entry.getKey();
        if (Arrays.equals(parameterTypes, method.getParameterTypes())) {
          return method.findMethod(myClass);
        }
      }
    }
    return null;
  }

  private Method[] getFixedChildrenGetterMethods(String tagName) {
    final Method[] methods = new Method[getFixedChildrenCount(tagName)];
    for (Map.Entry<JavaMethodSignature, Pair<String, Integer>> entry : myFixedChildrenMethods.entrySet()) {
      final Pair<String, Integer> pair = entry.getValue();
      if (tagName.equals(pair.getFirst())) {
        methods[pair.getSecond()] = entry.getKey().findMethod(myClass);
      }
    }
    return methods;
  }

  @Nullable
  public String getElementName(DomElement element) {
    if (myNameValueGetter == null) {
      return null;
    }
    final Object o;
    try {
      o = myNameValueGetter.invoke(element);
      return o == null || o instanceof String ? (String)o : ((GenericValue)o).getStringValue();
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    return null;
  }

  @NotNull
  public List<DomChildrenDescription> getChildrenDescriptions() {
    final ArrayList<DomChildrenDescription> result = new ArrayList<DomChildrenDescription>();
    result.addAll(getAttributeChildrenDescriptions());
    result.addAll(getFixedChildrenDescriptions());
    result.addAll(getCollectionChildrenDescriptions());
    return result;
  }

  @NotNull
  public List<DomFixedChildDescription> getFixedChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<DomFixedChildDescription> result = new ArrayList<DomFixedChildDescription>();
    for (String s : myFixedChildrenCounts.keySet()) {
      result.add(getFixedChildDescription(s));
    }
    return result;
  }

  @NotNull
  public List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<DomCollectionChildDescription> result = new ArrayList<DomCollectionChildDescription>();
    for (String s : myCollectionChildrenClasses.keySet()) {
      result.add(getCollectionChildDescription(s));
    }
    return result;
  }

  @Nullable
  public DomFixedChildDescription getFixedChildDescription(String tagName) {
    buildMethodMaps();
    final Method[] getterMethods = getFixedChildrenGetterMethods(tagName);
    assert getterMethods.length > 0 : tagName + " " + myClass;
    return new FixedChildDescriptionImpl(tagName, getterMethods[0].getGenericReturnType(), getFixedChildrenCount(tagName), getterMethods,
                                         ContainerUtil.map2Array(getterMethods, Required.class, new Function<Method, Required>() {
                                           public Required fun(final Method s) {
                                             return isRequired(s);
                                           }
                                         }));
  }

  final Required isRequired(Method method) {
    return myRequiredChildrenGetters.get(JavaMethodSignature.getSignature(method));
  }

  @Nullable
  public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
    buildMethodMaps();
    final Method getter = findGetterMethod(myCollectionChildrenGetterMethods, tagName);
    return new CollectionChildDescriptionImpl(tagName, getCollectionChildrenType(tagName), getCollectionAddMethod(tagName),
                                              getCollectionAddMethod(tagName, Class.class), getter,
                                              getCollectionAddMethod(tagName, int.class),
                                              getCollectionAddMethod(tagName, Class.class, int.class),
                                              getCollectionAddMethod(tagName, int.class, Class.class), isRequired(getter) != null);
  }

  @Nullable
  public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
    final Method getter = findGetterMethod(myAttributeChildrenMethods, attributeName);
    if (getter == null) return null;
    return new AttributeChildDescriptionImpl(attributeName, getter, isRequired(getter));
  }

  public final Class[] getConcreteInterfaceVariants() {
    return ClassChooserManager.getClassChooser(myClass).getChooserClasses();
  }

  public boolean isTagValueElement() {
    buildMethodMaps();
    return myValueElement;
  }

  @NotNull
  public List<DomAttributeChildDescription> getAttributeChildrenDescriptions() {
    final ArrayList<DomAttributeChildDescription> result = new ArrayList<DomAttributeChildDescription>();
    for (Map.Entry<JavaMethodSignature, String> entry : myAttributeChildrenMethods.entrySet()) {
      final Method getter = entry.getKey().findMethod(myClass);
      result.add(new AttributeChildDescriptionImpl(entry.getValue(), getter, isRequired(getter)));
    }
    return result;
  }

  @Nullable
  public DomChildrenDescription getChildDescription(String tagName) {
    if (isCollectionChild(tagName)) {
      return getCollectionChildDescription(tagName);
    }
    if (isFixedChild(tagName)) {
      return getFixedChildDescription(tagName);
    }
    return null;
  }

  final boolean isFixedChild(final String qname) {
    return myFixedChildrenCounts.containsKey(qname);
  }

  final boolean isCollectionChild(final String qname) {
    return myCollectionChildrenClasses.containsKey(qname);
  }

  public static boolean isDomElement(final Type type) {
    return type != null && DomElement.class.isAssignableFrom(DomUtil.getRawType(type));
  }
}
