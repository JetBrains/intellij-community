// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.stubs.*;
import com.intellij.xml.util.InclusionProvider;
import net.sf.cglib.proxy.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DomInvocationHandler extends UserDataHolderBase implements DomElement {
  private static final Logger LOG = Logger.getInstance(DomInvocationHandler.class);
  public static final Method ACCEPT_METHOD = ReflectionUtil.getMethod(DomElement.class, "accept", DomElementVisitor.class);
  public static final Method ACCEPT_CHILDREN_METHOD = ReflectionUtil.getMethod(DomElement.class, "acceptChildren", DomElementVisitor.class);
  private static final JavaMethod ourGetValue = JavaMethod.getMethod(GenericValue.class, new JavaMethodSignature("getValue"));

  private final Type myType;
  private final DomManagerImpl myManager;
  private final EvaluatedXmlName myTagName;
  private final AbstractDomChildDescriptionImpl myChildDescription;
  private DomParentStrategy myParentStrategy;
  private volatile long myLastModCount;

  private volatile DomElement myProxy;
  private DomGenericInfoEx myGenericInfo;
  private final InvocationCache myInvocationCache;
  private volatile Converter<?> myScalarConverter = null;
  private volatile SmartFMap<Method, Invocation> myAccessorInvocations = SmartFMap.emptyMap();
  protected @Nullable DomStub myStub;

  protected DomInvocationHandler(Type type, DomParentStrategy parentStrategy,
                                 final @NotNull EvaluatedXmlName tagName,
                                 AbstractDomChildDescriptionImpl childDescription,
                                 final DomManagerImpl manager,
                                 boolean dynamic,
                                 @Nullable DomStub stub) {
    myManager = manager;
    myParentStrategy = parentStrategy;
    myTagName = tagName;
    myChildDescription = childDescription;
    myStub = stub;
    myLastModCount = manager.getPsiModificationCount();

    myType = narrowType(type);

    myInvocationCache = manager.getApplicationComponent().getInvocationCache(getRawType());
    refreshGenericInfo(dynamic);
    if (stub != null) {
      stub.setHandler(this);
    }
  }

  protected Type narrowType(@NotNull Type nominalType) {
    return nominalType;
  }

  @Override
  public @Nullable DomElement getParent() {
    final DomInvocationHandler handler = getParentHandler();
    return handler == null ? null : handler.getProxy();
  }

  protected final void assertValid() {
    final String s = checkValidity();
    if (s != null) {
      throw new AssertionError(myType.toString() + " @" + hashCode() + "\nclass=" + getClass() + "\nxml=" + getXmlElement() + "; " + s);
    }
  }

  final @Nullable DomInvocationHandler getParentHandler() {
    return getParentStrategy().getParentHandler();
  }

  public @Nullable DomStub getStub() {
    return myStub;
  }

  @Override
  public final @NotNull Type getDomElementType() {
    return myType;
  }

  protected @Nullable String getValue() {
    final XmlTag tag = getXmlTag();
    return tag == null ? null : getTagValue(tag);
  }

  protected void setValue(final @Nullable String value) {
    final XmlTag tag = ensureTagExists();
    myManager.runChange(() -> setTagValue(tag, value));
    myManager.fireEvent(new DomEvent(getProxy(), false));
  }

  @Override
  public void copyFrom(final DomElement other) {
    if (other == getProxy()) return;
    assert other.getDomElementType().equals(myType) : "Can't copy from " + other.getDomElementType() + " to " + myType;

    if (other.getXmlElement() == null) {
      undefine();
      return;
    }

    myManager.performAtomicChange(() -> {
      ensureXmlElementExists();
      final DomInvocationHandler otherInvocationHandler = DomManagerImpl.getDomInvocationHandler(other);
      assert otherInvocationHandler != null : other;

      final DomGenericInfoEx genericInfo = otherInvocationHandler.getGenericInfo();
      for (final AttributeChildDescriptionImpl description : genericInfo.getAttributeChildrenDescriptions()) {
        description.getDomAttributeValue(this).setStringValue(description.getDomAttributeValue(other).getRawText());
      }
      for (final DomFixedChildDescription description : genericInfo.getFixedChildrenDescriptions()) {
        final List<? extends DomElement> list = description.getValues(getProxy());
        final List<? extends DomElement> otherValues = description.getValues(other);
        for (int i = 0; i < list.size(); i++) {
          final DomElement otherValue = otherValues.get(i);
          final DomElement value = list.get(i);
          if (!DomUtil.hasXml(otherValue)) {
            value.undefine();
          }
          else {
            value.copyFrom(otherValue);
          }
        }
      }
      for (final DomCollectionChildDescription description : genericInfo.getCollectionChildrenDescriptions()) {
        for (final DomElement value : description.getValues(getProxy())) {
          value.undefine();
        }
        for (final DomElement otherValue : description.getValues(other)) {
          description.addValue(getProxy(), otherValue.getDomElementType()).copyFrom(otherValue);
        }
      }

      final String stringValue = otherInvocationHandler.getValue();
      if (StringUtil.isNotEmpty(stringValue)) {
        setValue(stringValue);
      }
    });

    if (!myManager.isInsideAtomicChange()) {
      myManager.fireEvent(new DomEvent(getProxy(), false));
    }
  }

  @Override
  public <T extends DomElement> T createStableCopy() {
    XmlTag tag = getXmlTag();
    if (tag != null && tag.isPhysical()) {
      DomInvocationHandler existing = myManager.getDomHandler(tag);
      if (!equals(existing)) {
        throw new IllegalStateException(this + " != " + existing);
      }
      SmartPsiElementPointer<XmlTag> pointer =
        SmartPointerManager.getInstance(myManager.getProject()).createSmartPsiElementPointer(tag);
      return myManager.createStableValue(new StableCopyFactory<>(pointer, myType, getClass()));
    }
    return (T)createPathStableCopy();
  }

  protected DomElement createPathStableCopy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    final T copy = myManager.createMockElement((Class<? extends T>)getRawType(), getProxy().getModule(), physical);
    copy.copyFrom(getProxy());
    return copy;
  }

  @Override
  public @NotNull String getXmlElementNamespace() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "this operation should be performed on the DOM having a physical parent, your DOM may be not very fresh";
    final XmlElement element = parent.getXmlElement();
    assert element != null;
    return getXmlName().getNamespace(element, getFile());
  }

  @Override
  public @Nullable String getXmlElementNamespaceKey() {
    return getXmlName().getXmlName().getNamespaceKey();
  }

  @Override
  public final Module getModule() {
    final Module module = ModuleUtilCore.findModuleForPsiElement(getFile());
    return module != null ? module : DomUtil.getFile(this).getUserData(DomManager.MOCK_ELEMENT_MODULE);
  }

  @Override
  public XmlTag ensureTagExists() {
    assertValid();

    XmlTag tag = getXmlTag();
    if (tag != null) return tag;

    tag = setEmptyXmlTag();
    setXmlElement(tag);

    final DomElement element = getProxy();
    myManager.fireEvent(new DomEvent(element, true));
    addRequiredChildren();
    return getXmlTag();
  }

  @Override
  public XmlElement getXmlElement() {
    return getParentStrategy().getXmlElement();
  }

  @Override
  public boolean exists() {
    return getParentStrategy().isPhysical();
  }

  private DomParentStrategy getParentStrategy() {
    myParentStrategy = myParentStrategy.refreshStrategy(this);
    return myParentStrategy;
  }

  @Override
  public XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  protected final XmlTag createChildTag(final EvaluatedXmlName tagName) {
    final String localName = tagName.getXmlName().getLocalName();
    if (localName.contains(":")) {
      try {
        return XmlElementFactory.getInstance(myManager.getProject()).createTagFromText("<" + localName + "/>");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    final XmlElement element = getXmlElement();
    assert element != null;
    return getXmlTag().createChildTag(localName, tagName.getNamespace(element, getFile()), null, false);
  }

  @Override
  public final boolean isValid() {
    return checkValidity() == null;
  }

  String toStringEx() {
    return myType.toString() + " @" + hashCode() + "&handler=" + super.toString() + "&cd=" + myChildDescription + "&ps=" + myParentStrategy;
  }

  protected @Nullable String checkValidity() {
    ProgressManager.checkCanceled();
    final DomParentStrategy parentStrategy = getParentStrategy();
    String error = parentStrategy.checkValidity();
    if (error != null) {
      return "Strategy: " + error;
    }

    final long modCount = myManager.getPsiModificationCount();
    if (myLastModCount == modCount) {
      return null;
    }

    final XmlElement xmlElement = parentStrategy.getXmlElement();
    if (xmlElement != null) {
      final DomInvocationHandler actual = myManager.getDomHandler(xmlElement);
      if (!equals(actual)) {
        return "element changed: " + this.toStringEx() + "!=" + (actual == null ? null : actual.toStringEx());
      }
      myLastModCount = modCount;
      return null;
    }

    final DomInvocationHandler parent = getParentHandler();
    if (parent == null) {
      return "no parent: " + getDomElementType();
    }

    error = parent.checkValidity();
    if (error != null) {
      return "parent: " + error;
    }

    myLastModCount = modCount;
    return null;
  }


  @Override
  public final @NotNull DomGenericInfoEx getGenericInfo() {
    return myGenericInfo;
  }

  protected abstract void undefineInternal();

  @Override
  public final void undefine() {
    undefineInternal();
  }

  protected final void deleteTag(final XmlTag tag) {
    final boolean changing = myManager.setChanging(true);
    try {
      tag.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      myManager.setChanging(changing);
    }
  }

  protected final void fireUndefinedEvent() {
    myManager.fireEvent(new DomEvent(getProxy(), false));
  }

  protected abstract XmlTag setEmptyXmlTag();

  protected void addRequiredChildren() {
    for (final AbstractDomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
      if (description instanceof DomAttributeChildDescription) {
        final Required required = description.getAnnotation(Required.class);

        if (required != null && required.value()) {
          description.getValues(getProxy()).get(0).ensureXmlElementExists();
        }
      }
      else if (description instanceof DomFixedChildDescription childDescription) {
        List<? extends DomElement> values = null;
        final int count = childDescription.getCount();
        for (int i = 0; i < count; i++) {
          final Required required = childDescription.getAnnotation(i, Required.class);
          if (required != null && required.value()) {
            if (values == null) {
              values = description.getValues(getProxy());
            }
            values.get(i).ensureTagExists();
          }
        }
      }
    }
  }

  @Override
  public final @NotNull String getXmlElementName() {
    return myTagName.getXmlName().getLocalName();
  }

  public final @NotNull EvaluatedXmlName getXmlName() {
    return myTagName;
  }

  @Override
  public void accept(final DomElementVisitor visitor) {
    ProgressManager.checkCanceled();
    myManager.getApplicationComponent().getVisitorDescription(visitor.getClass()).acceptElement(visitor, getProxy());
  }

  @Override
  @SuppressWarnings("ForLoopReplaceableByForEach")
  public void acceptChildren(DomElementVisitor visitor) {
    ProgressManager.checkCanceled();
    final DomElement element = getProxy();
    List<? extends AbstractDomChildrenDescription> descriptions = getGenericInfo().getChildrenDescriptions();
    for (int i = 0, descriptionsSize = descriptions.size(); i < descriptionsSize; i++) {
      AbstractDomChildrenDescription description = descriptions.get(i);
      List<? extends DomElement> values = description.getValues(element);
      for (int j = 0, valuesSize = values.size(); j < valuesSize; j++) {
        DomElement value = values.get(j);
        value.accept(visitor);
      }
    }
  }

  protected final @NotNull Converter<?> getScalarConverter() {
    Converter<?> converter = myScalarConverter;
    if (converter == null) {
      myScalarConverter = converter = createConverter(ourGetValue);
    }
    return converter;
  }

  private @NotNull Converter<?> createConverter(final JavaMethod method) {
    final Type returnType = method.getGenericReturnType();
    final Type type = returnType == void.class ? method.getGenericParameterTypes()[0] : returnType;
    final Class parameter = DomUtil.substituteGenericType(type, myType);
    if (parameter == null) {
      LOG.error(type + " " + myType);
    }
    Converter<?> converter = getConverter(new AnnotatedElement() {
      @Override
      public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return myInvocationCache.getMethodAnnotation(method, annotationClass);
      }
    }, parameter);
    if (converter == null && type instanceof TypeVariable) {
      converter = getConverter(this, DomUtil.getGenericValueParameter(myType));
    }
    if (converter == null) {
      converter =  myManager.getConverterManager().getConverterByClass(parameter);
    }
    if (converter == null) {
      throw new AssertionError("No converter specified: String<->" + parameter.getName() + "; method=" + method + "; place=" + myChildDescription);
    }
    return converter;
  }

  @Override
  public AbstractDomChildDescriptionImpl getChildDescription() {
    return myChildDescription;
  }

  @Override
  public @Nullable <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    final AnnotatedElement childDescription = getChildDescription();
    if (childDescription != null) {
      final T annotation = childDescription.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    return getClassAnnotation(annotationClass);
  }

  protected <T extends Annotation> T getClassAnnotation(Class<T> annotationClass) {
    return myInvocationCache.getClassAnnotation(annotationClass);
  }

  private @Nullable Converter getConverter(final AnnotatedElement annotationProvider, Class parameter) {
    final Resolve resolveAnnotation = annotationProvider.getAnnotation(Resolve.class);
    if (resolveAnnotation != null) {
      final Class<? extends DomElement> aClass = resolveAnnotation.value();
      if (!DomElement.class.equals(aClass)) {
        return DomResolveConverter.createConverter(aClass);
      } else {
        LOG.assertTrue(parameter != null, "You should specify @Resolve#value() parameter");
        return DomResolveConverter.createConverter(parameter);
      }
    }

    final ConverterManager converterManager = myManager.getConverterManager();
    Convert convertAnnotation = annotationProvider.getAnnotation(Convert.class);
    if (convertAnnotation != null) {
      if (convertAnnotation instanceof ConvertAnnotationImpl annotation) {
        return annotation.getConverter();
      }
      return converterManager.getConverterInstance(convertAnnotation.value());
    }

    return null;
  }

  public final @NotNull DomElement getProxy() {
    DomElement proxy = myProxy;
    if (proxy == null) {
      Class<?> rawType = getRawType();
      Class<? extends DomElement> implementation = myManager.getApplicationComponent().getImplementation(rawType);
      final boolean isInterface = rawType.isInterface();
      if (implementation == null && !isInterface) {
        //noinspection unchecked
        implementation = (Class<? extends DomElement>)rawType;
      }
      myProxy = proxy = AdvancedProxy.createProxy(invocationHandler, implementation, isInterface ? new Class[]{rawType} : ArrayUtil.EMPTY_CLASS_ARRAY);
    }
    return proxy;
  }

  public final @NotNull XmlFile getFile() {
    return getParentStrategy().getContainingFile(this);
  }

  @Override
  public @NotNull DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    final DomInvocationHandler handler = getParentHandler();
    return handler == null ? DomNameStrategy.HYPHEN_STRATEGY : handler.getNameStrategy();
  }

  protected boolean isAttribute() {
    return false;
  }

  @Override
  public @NotNull ElementPresentation getPresentation() {
    ElementPresentationTemplate template = getChildDescription().getPresentationTemplate();
    if (template != null) {
      return template.createPresentation(getProxy());
    }
    return new ElementPresentation() {
      @Override
      public String getElementName() {
        return ElementPresentationManager.getElementName(getProxy());
      }

      @Override
      public String getTypeName() {
        return ElementPresentationManager.getTypeNameForObject(getProxy());
      }

      @Override
      public Icon getIcon() {
        return ElementPresentationManager.getIconOld(getProxy());
      }
    };
  }

  @Override
  public final GlobalSearchScope getResolveScope() {
    return DomUtil.getFile(this).getResolveScope();
  }

  private static <T extends DomElement> T _getParentOfType(Class<T> requiredClass, DomElement element) {
    while (element != null && !requiredClass.isInstance(element)) {
      element = element.getParent();
    }
    return (T)element;
  }

  @Override
  public final <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return _getParentOfType(requiredClass, strict ? getParent() : getProxy());
  }

  final @NotNull DomInvocationHandler getFixedChild(final Pair<? extends FixedChildDescriptionImpl, Integer> info) {
    final FixedChildDescriptionImpl description = info.first;
    XmlName xmlName = description.getXmlName();
    final EvaluatedXmlName evaluatedXmlName = createEvaluatedXmlName(xmlName);
    if (myStub != null && description.isStubbed()) {
      List<DomStub> stubs = myStub.getChildrenByName(xmlName);
      DomStub stub = stubs.isEmpty() ? null : stubs.get(0);
      DomParentStrategy strategy = stub == null ? new StubParentStrategy.Empty(myStub) : new StubParentStrategy(stub);
      return new IndexedElementInvocationHandler(evaluatedXmlName, description, 0, strategy, myManager, (ElementStub)stub);
    }
    final XmlTag tag = getXmlTag();
    final int index = info.second;
    if (tag != null) {
      List<XmlTag> tags = DomImplUtil.findSubTags(tag.getSubTags(), evaluatedXmlName, getFile());
      if (tags.size() > index) {
        final XmlTag child = tags.get(index);
        DomInvocationHandler handler = myManager.getDomHandler(child);
        if (!(handler instanceof IndexedElementInvocationHandler)) {
          DomInvocationHandler take2 = myManager.getDomHandler(child);
          throw new AssertionError("Expected indexed DOM, but got " + handler +
                                   ". Parent=" + tag + "; child=" + child + "; index=" + index+ "; second attempt=" + take2);

        }
        return handler;
      }
    }
    return new IndexedElementInvocationHandler(evaluatedXmlName, description, index, new VirtualDomParentStrategy(this), myManager, null);
  }

  final @NotNull DomInvocationHandler getAttributeChild(final AttributeChildDescriptionImpl description) {
    final EvaluatedXmlName evaluatedXmlName = createEvaluatedXmlName(description.getXmlName());
    if (myStub != null && description.isStubbed()) {
      AttributeStub stub = myStub.getAttributeStub(description.getXmlName());
      StubParentStrategy strategy = StubParentStrategy.createAttributeStrategy(stub, myStub);
      return new AttributeChildInvocationHandler(evaluatedXmlName, description, myManager, strategy, stub);
    }
    final XmlTag tag = getXmlTag();

    if (tag != null) {
      // TODO: this seems ugly
      String ns = evaluatedXmlName.getNamespace(tag, getFile());
      final XmlAttribute attribute = tag.getAttribute(description.getXmlName().getLocalName(), ns.equals(tag.getNamespace())? null:ns);

      if (attribute != null) {
        PsiUtilCore.ensureValid(attribute);
        DomInvocationHandler handler = myManager.getDomHandler(attribute);
        if (!(handler instanceof AttributeChildInvocationHandler)) {
          throw new AssertionError("Expected indexed DOM, but got " + handler +
                                   "; ns=" + ns +
                                   "; description=" + description +
                                   "; attribute=" + attribute.getName() +
                                   "; XML consistent=" + (PhysicalDomParentStrategy.getParentTag(attribute) == tag) +
                                   "; DOM consistent =" + equals(DomCreator.getParentDom(tag)) +
                                   "; re-creation=" + DomCreator.createAttributeHandler(attribute));
        }
        return handler;
      }
    }
    return new AttributeChildInvocationHandler(evaluatedXmlName, description, myManager, new VirtualDomParentStrategy(this), null);
  }

  private final InvocationHandler invocationHandler = new MyInvocationHandler();

  private @NotNull Invocation findInvocation(Method method) {
    Invocation invocation = myAccessorInvocations.get(method);
    if (invocation != null) return invocation;

    invocation = myInvocationCache.getInvocation(method);
    if (invocation != null) return invocation;

    invocation = createAccessorInvocation(method);
    myAccessorInvocations = myAccessorInvocations.plus(method, invocation);
    return invocation;
  }

  private @NotNull Invocation createAccessorInvocation(Method method) {
    Invocation invocation;
    JavaMethod javaMethod = myInvocationCache.getInternedMethod(method);
    if (myInvocationCache.isTagValueGetter(javaMethod)) {
      invocation = new GetInvocation(createConverter(javaMethod));
    }
    else if (myInvocationCache.isTagValueSetter(javaMethod)) {
      invocation = new SetInvocation(createConverter(javaMethod));
    }
    else {
      throw new RuntimeException("No implementation for method " + method.toString() + " in class " + myType);
    }
    return invocation;
  }

  private static void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  private static String getTagValue(final XmlTag tag) {
    return tag.getValue().getTrimmedText();
  }

  @Override
  public final String toString() {
    if (ReflectionUtil.isAssignable(GenericValue.class, getRawType())) {
      return ((GenericValue<?>)getProxy()).getStringValue();
    }
    return myType.toString() + " @" + hashCode();
  }

  public final Class<?> getRawType() {
    return ClassUtil.getRawType(myType);
  }

  @Override
  public @Nullable XmlTag getXmlTag() {
    return (XmlTag) getXmlElement();
  }

  protected @Nullable XmlElement recomputeXmlElement(final @NotNull DomInvocationHandler parentHandler) {
    return null;
  }

  protected final void detach() {
    setXmlElement(null);
  }

  protected final void setXmlElement(final XmlElement element) {
    refreshGenericInfo(element != null && !isAttribute());
    myStub = null;
    myParentStrategy = element == null ? myParentStrategy.clearXmlElement() : myParentStrategy.setXmlElement(element);
  }

  private void refreshGenericInfo(final boolean dynamic) {
    final StaticGenericInfo staticInfo = myManager.getApplicationComponent().getStaticGenericInfo(myType);
    myGenericInfo = dynamic ? new DynamicGenericInfo(this, staticInfo) : staticInfo;
  }

  @Override
  public final @NotNull DomManagerImpl getManager() {
    return myManager;
  }

  public final DomElement addCollectionChild(final CollectionChildDescriptionImpl description, final Type type, int index) throws IncorrectOperationException {
    final EvaluatedXmlName name = createEvaluatedXmlName(description.getXmlName());
    final XmlTag tag = addEmptyTag(name, index);
    final CollectionElementInvocationHandler handler = new CollectionElementInvocationHandler(type, tag, description, this, null);
    myManager.fireEvent(new DomEvent(getProxy(), false));
    getManager().getTypeChooserManager().getTypeChooser(description.getType()).distinguishTag(tag, type);
    handler.addRequiredChildren();
    return handler.getProxy();
  }

  protected final void createFixedChildrenTags(EvaluatedXmlName tagName, FixedChildDescriptionImpl description, int count) {
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, getFile());
    if (subTags.size() < count) {
      getFixedChild(Pair.create(description, count - 1)).ensureTagExists();
    }
  }

  private XmlTag addEmptyTag(final EvaluatedXmlName tagName, int index) throws IncorrectOperationException {
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, getFile());
    if (subTags.size() < index) {
      index = subTags.size();
    }
    final boolean changing = myManager.setChanging(true);
    try {
      XmlTag newTag = createChildTag(tagName);
      if (index == 0) {
        if (subTags.isEmpty()) {
          return (XmlTag)tag.add(newTag);
        }

        return (XmlTag)tag.addBefore(newTag, subTags.get(0));
      }

      return (XmlTag)tag.addAfter(newTag, subTags.get(index - 1));
    }
    finally {
      myManager.setChanging(changing);
    }
  }

  public final @NotNull EvaluatedXmlName createEvaluatedXmlName(final XmlName xmlName) {
    return getXmlName().evaluateChildName(xmlName);
  }

  public @Unmodifiable List<? extends DomElement> getCollectionChildren(final AbstractCollectionChildDescription description) {
    return getCollectionChildren(description, InclusionProvider.getInstance().shouldProcessIncludesNow());
  }

  public @Unmodifiable List<? extends DomElement> getCollectionChildren(final AbstractCollectionChildDescription description, boolean processIncludes) {
    if (myStub != null && description.isStubbed()) {
      if (description instanceof DomChildDescriptionImpl) {
        XmlName xmlName = ((DomChildDescriptionImpl)description).getXmlName();
        SmartList<DomElement> result = new SmartList<>();
        List<? extends Stub> stubs = myStub.getChildrenStubs();
        for (Stub stub : stubs) {
          if (stub instanceof DomStub && ((DomStub)stub).matches(xmlName)) {
            result.add(((DomStub)stub).getOrCreateHandler((DomChildDescriptionImpl)description, myManager).getProxy());
          }
          else if (processIncludes && stub instanceof XIncludeStub) {
            ((XIncludeStub)stub).resolve(this, result, xmlName);
          }
        }
        return result;
      }
      else if (description instanceof CustomDomChildrenDescriptionImpl) {
        List<? extends Stub> stubs = myStub.getChildrenStubs();
        return ContainerUtil.mapNotNull(stubs, (NullableFunction<Stub, DomElement>)stub -> {
          if (stub instanceof ElementStub && ((ElementStub)stub).isCustom()) {
            EvaluatedXmlName name = new DummyEvaluatedXmlName(((ElementStub)stub).getName(), "");
            return new CollectionElementInvocationHandler(name, (CustomDomChildrenDescriptionImpl)description, myManager, (ElementStub)stub).getProxy();
          }
          return null;
        });
      }
    }
    XmlTag tag = getXmlTag();
    if (tag == null) return Collections.emptyList();

    final List<XmlTag> subTags = getCollectionSubTags(description, tag, processIncludes);
    if (subTags.isEmpty()) return Collections.emptyList();

    List<DomElement> elements = new ArrayList<>(subTags.size());
    for (XmlTag subTag : subTags) {
      DomInvocationHandler handler = myManager.getDomHandler(subTag);
      if (handler == null) {
        String msg = "No child for subTag '" + subTag.getName() + "' in tag '" + tag.getName() + "'; subtag count=" + subTags.size() + ", description=" + description + ", subtag.class=" + subTag.getClass().getName();
        DomInvocationHandler anyDom = myManager.getDomHandler(subTag);
        if (anyDom != null) {
          msg += "\n sub-dom=" + anyDom + " with " + anyDom.getChildDescription();
        }
        throw new AssertionError(msg);
      }
      else {
        elements.add(handler.getProxy());
      }
    }
    return Collections.unmodifiableList(elements);
  }

  private @Unmodifiable List<XmlTag> getCollectionSubTags(@NotNull AbstractCollectionChildDescription description, @NotNull XmlTag tag, boolean processIncludes) {
    if (description instanceof CollectionChildDescriptionImpl) {
      return ((CollectionChildDescriptionImpl)description).getCollectionSubTags(this, tag, processIncludes);
    }
    return DomImplUtil.getCustomSubTags(this, tag.getSubTags(), getFile());
  }

  private static class StableCopyFactory<T extends DomElement> implements NullableFactory<T> {
    private final SmartPsiElementPointer<? extends XmlTag> myPointer;
    private final Type myType;
    private final Class<? extends DomInvocationHandler> myHandlerClass;

    StableCopyFactory(final SmartPsiElementPointer<? extends XmlTag> pointer,
                             final Type type, final Class<? extends DomInvocationHandler> aClass) {
      myPointer = pointer;
      myType = type;
      myHandlerClass = aClass;
    }

    @Override
    public T create() {
      final XmlTag tag = myPointer.getElement();
      if (tag == null || !tag.isValid()) return null;

      final DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (element == null || !element.getDomElementType().equals(myType)) return null;

      final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
      if (handler == null || !handler.getClass().equals(myHandlerClass)) return null;

      //noinspection unchecked
      return (T)element;
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || !o.getClass().equals(getClass())) return false;

    final DomInvocationHandler that = (DomInvocationHandler)o;
    if (!myChildDescription.equals(that.myChildDescription)) return false;
    if (!getParentStrategy().equals(that.getParentStrategy())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myChildDescription.hashCode();
  }

  final class MyInvocationHandler implements InvocationHandler {
    DomInvocationHandler getDomInvocationHandler() {
      return DomInvocationHandler.this;
    }

    @Override
    public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        return findInvocation(method).invoke(DomInvocationHandler.this, args);
      }
      catch (InvocationTargetException ex) {
        throw ex.getTargetException();
      }
    }
  }
}

