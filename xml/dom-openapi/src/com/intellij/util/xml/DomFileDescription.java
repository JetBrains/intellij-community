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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ConstantFunction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentInstanceMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 *
 * @see com.intellij.util.xml.MergingFileDescription
 */
public class DomFileDescription<T> {
  public static final ExtensionPointName<DomFileDescription> EP_NAME = ExtensionPointName.create("com.intellij.dom.fileDescription");

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomFileDescription");
  private final ConcurrentInstanceMap<ScopeProvider> myScopeProviders = new ConcurrentInstanceMap<>();
  protected final Class<T> myRootElementClass;
  protected final String myRootTagName;
  private final String[] myAllPossibleRootTagNamespaces;
  private volatile boolean myInitialized;
  private final Map<Class<? extends DomElement>,Class<? extends DomElement>> myImplementations = new HashMap<>();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  private final List<DomReferenceInjector> myInjectors = new SmartList<>();
  private final Map<String, NotNullFunction<XmlTag, List<String>>> myNamespacePolicies =
    ContainerUtil.newConcurrentMap();

  public DomFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName, @NonNls final String... allPossibleRootTagNamespaces) {
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myAllPossibleRootTagNamespaces = allPossibleRootTagNamespaces;
  }

  public String[] getAllPossibleRootTagNamespaces() {
    return myAllPossibleRootTagNamespaces;
  }

  /**
   * Register an implementation class to provide additional functionality for DOM elements.
   *
   * @param domElementClass interface class.
   * @param implementationClass abstract implementation class.
   *
   * @deprecated use dom.implementation extension point instead
   * @see #initializeFileDescription()
   */
  public final <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    myImplementations.put(domElementClass, implementationClass);
  }

  /**
   * @param namespaceKey namespace identifier
   * @see com.intellij.util.xml.Namespace
   * @param policy function that takes XML file root tag and returns (maybe empty) list of possible namespace URLs or DTD public ids. This
   * function shouldn't use DOM since it may be not initialized for the file at the moment
   * @deprecated use {@link #registerNamespacePolicy(String, String...)} or override {@link #getAllowedNamespaces(String, com.intellij.psi.xml.XmlFile)} instead
   */
  protected final void registerNamespacePolicy(String namespaceKey, NotNullFunction<XmlTag,List<String>> policy) {
    myNamespacePolicies.put(namespaceKey, policy);
  }

  /**
   * @param namespaceKey namespace identifier
   * @see com.intellij.util.xml.Namespace
   * @param namespaces XML namespace or DTD public or system id value for the given namespaceKey
   */
  public final void registerNamespacePolicy(String namespaceKey, final String... namespaces) {
    registerNamespacePolicy(namespaceKey, new ConstantFunction<>(Arrays.asList(namespaces)));
  }

  /**
   * Consider using {@link DomService#getXmlFileHeader(com.intellij.psi.xml.XmlFile)} when implementing this.
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  @NotNull
  public List<String> getAllowedNamespaces(@NotNull String namespaceKey, @NotNull XmlFile file) {
    final NotNullFunction<XmlTag, List<String>> function = myNamespacePolicies.get(namespaceKey);
    if (function instanceof ConstantFunction) {
      return function.fun(null);
    }

    if (function != null) {
      final XmlDocument document = file.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return function.fun(tag);
        }
      }
    } else {
      return Collections.singletonList(namespaceKey);
    }
    return Collections.emptyList();
  }

  /**
   * @return some version. Override and change (e.g. <code>super.getVersion()+1</code>) when after some changes some files stopped being
   * described by this description or vice versa, so that the
   * {@link com.intellij.util.xml.DomService#getDomFileCandidates(Class, com.intellij.openapi.project.Project, com.intellij.psi.search.GlobalSearchScope)}
   * index is rebuilt correctly.
   */
  public int getVersion() {
    return myRootTagName.hashCode();
  }

  protected final void registerTypeChooser(final Type aClass, final TypeChooser typeChooser) {
    myTypeChooserManager.registerTypeChooser(aClass, typeChooser);
  }

  public final TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  protected final void registerReferenceInjector(DomReferenceInjector injector) {
    myInjectors.add(injector);
  }

  public List<DomReferenceInjector> getReferenceInjectors() {
    return myInjectors;
  }

  public boolean isAutomaticHighlightingEnabled() {
    return true;
  }

  @Nullable
  public Icon getFileIcon(@Iconable.IconFlags int flags) {
    return null;
  }

  /**
   * The right place to call
   * <ul>
   * <li>{@link #registerNamespacePolicy(String, String...)}</li>
   * <li>{@link #registerTypeChooser(java.lang.reflect.Type, TypeChooser)}</li>
   * <li>{@link #registerReferenceInjector(DomReferenceInjector)}</li>
   * </ul>
   */
  protected void initializeFileDescription() {}

  /**
   * Create custom DOM annotator that will be used when error-highlighting DOM. The results will be collected to
   * {@link com.intellij.util.xml.highlighting.DomElementsProblemsHolder}. The highlighting will be most probably done in an
   * {@link com.intellij.util.xml.highlighting.BasicDomElementsInspection} instance.
   * @return Annotator or null
   */
  @Nullable
  public DomElementsAnnotator createAnnotator() {
    return null;
  }

  public final Map<Class<? extends DomElement>,Class<? extends DomElement>> getImplementations() {
    if (!myInitialized) {
      initializeFileDescription();
      myInitialized = true;
    }
    return myImplementations;
  }

  @NotNull
  public final Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  public final String getRootTagName() {
    return myRootTagName;
  }

  public boolean isMyFile(@NotNull XmlFile file, @Nullable final Module module) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(myRootElementClass, Namespace.class);
    if (namespace != null) {
      final String key = namespace.value();
      Set<String> allNs = new HashSet<>(getAllowedNamespaces(key, file));
      if (allNs.isEmpty()) {
        return false;
      }

      XmlFileHeader header = DomService.getInstance().getXmlFileHeader(file);
      return allNs.contains(header.getPublicId()) || allNs.contains(header.getSystemId()) || allNs.contains(header.getRootTagNamespace());
    }

    return true;
  }

  public boolean acceptsOtherRootTagNames() {
    return false;
  }

  /**
   * Get dependency items (the same, as in {@link com.intellij.psi.util.CachedValue}) for file. On any dependency item change, the
   * {@link #isMyFile(com.intellij.psi.xml.XmlFile, Module)} method will be invoked once more to ensure that the file description still
   * accepts this file 
   * @param file XML file to get dependencies of
   * @return dependency item set 
   */
  @NotNull
  public Set<?> getDependencyItems(XmlFile file) {
    return Collections.emptySet();
  }

  /**
   * @param reference DOM reference
   * @return element, whose all children will be searched for declaration
   */
  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    return DomUtil.getRoot(reference);
  }

  /**
   * @param element DOM element
   * @return element, whose direct children names will be compared by name. Basically it's parameter element's parent (see {@link ParentScopeProvider}).
   */
  @NotNull
  public DomElement getIdentityScope(DomElement element) {
    final DomElement annotation = getScopeFromAnnotation(element);
    if (annotation != null) return annotation;

    return element.getParent();
  }

  @Nullable
  protected final DomElement getScopeFromAnnotation(final DomElement element) {
    final Scope scope = element.getAnnotation(Scope.class);
    if (scope != null) {
      return myScopeProviders.get(scope.value()).getScope(element);
    }
    return null;
  }

  /**
   * @see Stubbed
   * @return false
   */
  public boolean hasStubs() {
    return false;
  }

  public int getStubVersion() {
    return 0;
  }

  @Override
  public String toString() {
    return getRootElementClass() + " <" + getRootTagName() + "> \n" + StringUtil.join(getAllPossibleRootTagNamespaces());
  }
}
