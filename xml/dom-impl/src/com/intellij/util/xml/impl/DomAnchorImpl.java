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
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class DomAnchorImpl<T extends DomElement> implements DomAnchor<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomAnchorImpl");

  public static <T extends DomElement> DomAnchor<T> createAnchor(@NotNull T t) {
    return createAnchor(t, true);
  }

  public static <T extends DomElement> DomAnchor<T> createAnchor(@NotNull T t, boolean usePsi) {
    DomInvocationHandler handler = DomManagerImpl.getNotNullHandler(t);
    if (handler.getStub() != null) {
      return new StubAnchor<>(handler);
    }

    if (usePsi) {
      final XmlElement element = t.getXmlElement();
      if (element != null) {
        return new PsiBasedDomAnchor<>(PsiAnchor.create(element), element.getProject());
      }
    }


    final DomElement parent = t.getParent();
    if (parent == null) {
      LOG.error("Parent null: " + t);
    }

    if (parent instanceof DomFileElementImpl) {
      final DomFileElementImpl fileElement = (DomFileElementImpl)parent;
      //noinspection unchecked
      return new RootAnchor<>(fileElement.getFile(), fileElement.getRootElementClass());
    }

    final DomAnchor<DomElement> parentAnchor = createAnchor(parent);
    final String name = t.getGenericInfo().getElementName(t);
    final AbstractDomChildrenDescription description = t.getChildDescription();
    final List<? extends DomElement> values = description.getValues(parent);
    if (name != null) {
      int i = 0;
      for (DomElement value : values) {
        if (value.equals(t)) {
          return new NamedAnchor<>(parentAnchor, description, name, i);
        }
        if (name.equals(value.getGenericInfo().getElementName(value))) {
          i++;
        }
      }
    }

    final int index = values.indexOf(t);
    if (index < 0) {
      diagnoseNegativeIndex2(t, parent, description, values);
    }
    return new IndexedAnchor<>(parentAnchor, description, index);
  }

  private static <T extends DomElement> void diagnoseNegativeIndex2(T t,
                                                                    DomElement parent,
                                                                    AbstractDomChildrenDescription description,
                                                                    List<? extends DomElement> values) {
    final XmlTag parentTag = parent.getXmlTag();
    StringBuilder diag = new StringBuilder("Index<0: description=" + description + "\nparent=" + parent + "\nt=" + t + "\nvalues=" + values + "\n");
    for (int i = 0, size = values.size(); i < size; i++) {
      DomElement value = values.get(i);
      if (value.toString().equals(t.toString())) {
        final XmlElement tElement = t.getXmlElement();
        final XmlElement valElement = value.getXmlElement();
        diag.append(" hasSame, i=" + i + 
                    "; same=" + (value == t) +
                    ", equal=" + value.equals(t) +
                    ", equal2=" + t.equals(value) +
                    ", t.physical=" + (tElement == null ? "null" : String.valueOf(tElement.isPhysical())) +
                    ", value.physical=" + (valElement == null ? "null" : String.valueOf(valElement.isPhysical())) +
                    ", sameElements=" + (tElement == value.getXmlElement()) +
                    "\n");
        if (tElement != null && valElement != null) {
          diag.append("  sameFile=" + (tElement.getContainingFile() == valElement.getContainingFile()) + 
                      ", sameParent=" + (tElement.getParent() == valElement.getParent()) +
                      "\n");
        }
      }
    }
    
    if (parentTag != null) {
      diag.append("Parent tag: ").append(parentTag.getName()).append("\n");
      if (t instanceof GenericAttributeValue) {
        for (XmlAttribute attribute : parentTag.getAttributes()) {
          diag.append(", attr: ").append(attribute.getName());
        }
        diag.append("\n");
      } else {
        for (XmlTag tag : parentTag.getSubTags()) {
          diag.append("\n subtag: ").append(tag.getName());
        }
        diag.append("\n");
      }
    }
    diag.append("Child name: ").append(t.getXmlElementName()).append(";").append(t.getXmlElementNamespaceKey());
    LOG.error(diag);
  }


  @Override
  public PsiElement getPsiElement() {
    T t = retrieveDomElement();
    return t == null ? null : t.getXmlElement();
  }

  @Override
  @Nullable
  public abstract T retrieveDomElement();

  @Override
  @NotNull
  public abstract XmlFile getContainingFile();

  private static class NamedAnchor<T extends DomElement> extends DomAnchorImpl<T> {
    private final DomAnchor myParent;
    private final AbstractDomChildrenDescription myDescr;
    private final String myName;
    private final int myIndex;

    private NamedAnchor(final DomAnchor parent, final AbstractDomChildrenDescription descr, final String id, int index) {
      myParent = parent;
      myDescr = descr;
      myName = id;
      myIndex = index;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof NamedAnchor)) return false;

      final NamedAnchor that = (NamedAnchor)o;

      if (myDescr != null ? !myDescr.equals(that.myDescr) : that.myDescr != null) return false;
      if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;
      if (myParent != null ? !myParent.equals(that.myParent) : that.myParent != null) return false;
      if (myIndex != that.myIndex) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = (myParent != null ? myParent.hashCode() : 0);
      result = 31 * result + (myDescr != null ? myDescr.hashCode() : 0);
      result = 31 * result + (myName != null ? myName.hashCode() : 0);
      result = 31 * result + myIndex;
      return result;
    }

    @Override
    public T retrieveDomElement() {
      final DomElement parent = myParent.retrieveDomElement();
      if (parent == null) return null;

      final List<? extends DomElement> list = myDescr.getValues(parent);
      int i = 0;
      for (final DomElement element : list) {
        final String s = element.getGenericInfo().getElementName(element);
        if (myName.equals(s)) {
          if (i == myIndex) {
            //noinspection unchecked
            return (T)element;
          }
          i++;
        }
      }
      return null;
    }

    @Override
    @NotNull
    public XmlFile getContainingFile() {
      return myParent.getContainingFile();
    }
  }

  private static class IndexedAnchor<T extends DomElement> extends DomAnchorImpl<T> {
    private final DomAnchor myParent;
    private final AbstractDomChildrenDescription myDescr;
    private final int myIndex;

    private IndexedAnchor(final DomAnchor parent, final AbstractDomChildrenDescription descr, final int index) {
      myParent = parent;
      myDescr = descr;
      myIndex = index;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof IndexedAnchor)) return false;

      final IndexedAnchor that = (IndexedAnchor)o;

      if (myIndex != that.myIndex) return false;
      if (myDescr != null ? !myDescr.equals(that.myDescr) : that.myDescr != null) return false;
      if (myParent != null ? !myParent.equals(that.myParent) : that.myParent != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = (myParent != null ? myParent.hashCode() : 0);
      result = 31 * result + (myDescr != null ? myDescr.hashCode() : 0);
      result = 31 * result + myIndex;
      return result;
    }

    @Override
    public T retrieveDomElement() {
      final DomElement parent = myParent.retrieveDomElement();
      if (parent == null) return null;

      final List<? extends DomElement> list = myDescr.getValues(parent);
      if (myIndex < 0 || myIndex >= list.size()) return null;

      //noinspection unchecked
      return (T)list.get(myIndex);
    }

    @Override
    @NotNull
    public XmlFile getContainingFile() {
      return myParent.getContainingFile();
    }
  }

  private static class RootAnchor<T extends DomElement> extends DomAnchorImpl<T> {
    private final XmlFile myFile;
    private final Class<T> myClass;

    private RootAnchor(final XmlFile file, final Class<T> aClass) {
      myFile = file;
      myClass = aClass;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof RootAnchor)) return false;

      final RootAnchor that = (RootAnchor)o;

      if (myClass != null ? !myClass.equals(that.myClass) : that.myClass != null) return false;
      if (myFile != null ? !myFile.equals(that.myFile) : that.myFile != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = (myFile != null ? myFile.hashCode() : 0);
      result = 31 * result + (myClass != null ? myClass.hashCode() : 0);
      return result;
    }

    @Override
    public T retrieveDomElement() {
      final DomFileElement<T> fileElement = DomManager.getDomManager(myFile.getProject()).getFileElement(myFile, myClass);
      return fileElement == null ? null : fileElement.getRootElement();
    }

    @Override
    @NotNull
    public XmlFile getContainingFile() {
      return myFile;
    }
  }


  private static class PsiBasedDomAnchor<T extends DomElement> extends DomAnchorImpl<T> {
    private final PsiAnchor myAnchor;
    private final Project myProject;

    public PsiBasedDomAnchor(PsiAnchor anchor, Project project) {
      myAnchor = anchor;
      myProject = project;
    }

    @Override
    public T retrieveDomElement() {
      PsiElement psi = myAnchor.retrieve();
      if (psi == null) return null;

      if (psi instanceof XmlTag) {
        return (T)DomManager.getDomManager(myProject).getDomElement((XmlTag)psi);
      }
      if (psi instanceof XmlAttribute) {
        return (T)DomManager.getDomManager(myProject).getDomElement((XmlAttribute)psi);
      }
      return null;
    }

    @NotNull
    @Override
    public XmlFile getContainingFile() {
      return (XmlFile)myAnchor.getFile();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PsiBasedDomAnchor anchor = (PsiBasedDomAnchor)o;

      if (myAnchor != null ? !myAnchor.equals(anchor.myAnchor) : anchor.myAnchor != null) return false;
      if (myProject != null ? !myProject.equals(anchor.myProject) : anchor.myProject != null) return false;

      return true;
    }


    @Override
    public int hashCode() {
      int result = myAnchor != null ? myAnchor.hashCode() : 0;
      result = 31 * result + (myProject != null ? myProject.hashCode() : 0);
      return result;
    }
  }

  private static class StubAnchor<T extends DomElement> implements DomAnchor<T> {

    private final DomInvocationHandler myHandler;

    private StubAnchor(DomInvocationHandler handler) {
      myHandler = handler;
    }

    @Nullable
    @Override
    public T retrieveDomElement() {
      return (T)myHandler.getProxy();
    }

    @NotNull
    @Override
    public XmlFile getContainingFile() {
      return myHandler.getFile();
    }

    @Nullable
    @Override
    public PsiElement getPsiElement() {
      return myHandler.getXmlElement();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StubAnchor anchor = (StubAnchor)o;

      if (myHandler != null ? !myHandler.equals(anchor.myHandler) : anchor.myHandler != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myHandler != null ? myHandler.hashCode() : 0;
    }
  }
}
