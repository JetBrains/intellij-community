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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class DomManager extends CompositeModificationTracker implements ModificationTracker {
  public static final Key<Module> MOCK_ELEMENT_MODULE = Key.create("MockElementModule");

  private static final NotNullLazyKey<DomManager, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(DomManager.class);

  public static DomManager getDomManager(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  public DomManager(@NotNull Project project) {
    super(PsiManager.getInstance(project).getModificationTracker().getOutOfCodeBlockModificationTracker());
  }

  public abstract Project getProject();

  /**
   * @param file XML file
   * @param domClass desired DOM element class 
   * @return New or cached DOM file element for the given file. All registered {@link com.intellij.util.xml.DomFileDescription}s are
   * asked if they are responsible for the file {@link com.intellij.util.xml.DomFileDescription#isMyFile(com.intellij.psi.xml.XmlFile, com.intellij.openapi.module.Module)}.
   * If there is a {@link com.intellij.util.xml.DomFileDescription} that is responsible for the file, but its {@link DomFileDescription#getRootElementClass()}
   * result is incompatible with domClass parameter, null is returned
   */
  @Nullable
  public abstract <T extends DomElement> DomFileElement<T> getFileElement(XmlFile file, Class<T> domClass);

  @Nullable
  @Deprecated
  /**
   * @deprecated use {@link #getFileElement(XmlFile, Class)}
   */
  public abstract <T extends DomElement> DomFileElement<T> getFileElement(XmlFile file);

  @NotNull
  @Deprecated
  /**
   * @deprecated use {@link #getFileElement(XmlFile, Class)}
   */
  public abstract <T extends DomElement> DomFileElement<T> getFileElement(XmlFile file, Class<T> aClass, @NonNls String rootTagName);

  public abstract void addDomEventListener(DomEventListener listener, Disposable parentDisposable);

  /**
   * @param type Type. Only {@link Class} and {@link java.lang.reflect.ParameterizedType} are allowed
   * @return {@link com.intellij.util.xml.reflect.DomGenericInfo} instance for the desired type
   */
  public abstract DomGenericInfo getGenericInfo(Type type);

  /**
   * @param element tag
   * @return DOM element for the given tag. If DOM isn't initialized for the containing file, it will be initialized
   */
  @Nullable
  public abstract DomElement getDomElement(@Nullable final XmlTag element);

  /**
   * @param element attribute
   * @return DOM element for the given XML attribute. If DOM isn't initialized for the containing file, it will be initialized
   */
  @Nullable
  public abstract GenericAttributeValue getDomElement(final XmlAttribute element);

  /**
   * @param aClass Desired DOM element class
   * @param module One may wish the result to think that it is in a particular module
   * @param physical see {@link com.intellij.psi.PsiFile#isPhysical()}
   * @return DOM element which doesn't have any real file under itself. A mock file is created for it. See
   * {@link com.intellij.psi.PsiFileFactory#createFileFromText(String, com.intellij.openapi.fileTypes.FileType, CharSequence, long, boolean, boolean)}
   */
  public abstract <T extends DomElement> T createMockElement(Class<T> aClass, final Module module, final boolean physical);

  /**
   * @param element DOM element
   * @return true if this element was created by {@link #createMockElement(Class, com.intellij.openapi.module.Module, boolean)} method
   */
  public abstract boolean isMockElement(DomElement element);

  /**
   * Creates DOM element of needed type, that is wrapper around real DOM element. Once the wrapped element
   * becomes invalid, a new value is requested from provider parameter, so there's a possibility to
   * restore the functionality. The resulting element will also implement StableElement interface.
   *
   * @param provider provides values to be wrapped
   * @return stable DOM element
   */
  public abstract <T extends DomElement> T createStableValue(Factory<T> provider);

  public abstract <T> T createStableValue(final Factory<T> provider, final Condition<T> validator);

  /**
   * Registers a new {@link com.intellij.util.xml.DomFileDescription} within the manager. The description parameter describes some DOM
   * parameters and restrictions to the particular XML files, that need DOM support. Should be called on
   * {@link com.intellij.openapi.components.ProjectComponent} loading.
   * @param description The description in question
   * @deprecated Make your file description an extension (see {@link com.intellij.util.xml.DomFileDescription#EP_NAME})
   */
  @Deprecated
  public abstract void registerFileDescription(DomFileDescription description);

  /**
   * @return {@link com.intellij.util.xml.ConverterManager} instance
   * @deprecated This will be moved at the application level
   */
  @Deprecated
  public abstract ConverterManager getConverterManager();

  public abstract ModelMerger createModelMerger();


  /**
   * @param element reference element
   * @return element that represents the resolve scope for the given reference. {@link com.intellij.util.xml.DomResolveConverter} uses
   * this method to resolve DOM references. This result's subtree will be traversed recursively searching for the reference target. See
   * {@link com.intellij.util.xml.Resolve} annotation.
   */
  @NotNull
  public abstract DomElement getResolvingScope(GenericDomValue element);

  /**
   * @param element Named DOM element
   * @return The scope within which the element's name identity will be checked by
   * {@link com.intellij.util.xml.highlighting.DomHighlightingHelper#checkNameIdentity(DomElement, com.intellij.util.xml.highlighting.DomElementAnnotationHolder)}
   */
  @Nullable
  public abstract DomElement getIdentityScope(DomElement element);

  /**
   * @return {@link com.intellij.util.xml.TypeChooserManager} instance
   */
  public abstract TypeChooserManager getTypeChooserManager();

  @Nullable
  public abstract AbstractDomChildrenDescription findChildrenDescription(@NotNull XmlTag templateChildTag, @NotNull DomElement parent);

  @Nullable
  public final DomFileDescription<?> getDomFileDescription(final XmlFile xmlFile) {
    final DomFileElement<DomElement> element = getFileElement(xmlFile);
    return element != null ? element.getFileDescription() : null;
  }
}
