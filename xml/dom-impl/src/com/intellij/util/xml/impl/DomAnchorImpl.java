/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class DomAnchorImpl<T extends DomElement> implements DomAnchor<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomAnchorImpl");

  public static <T extends DomElement> DomAnchorImpl<T> createAnchor(@NotNull T t) {
    final DomElement parent = t.getParent();
    if (parent == null) {
      LOG.error("Parent null: " + t);
    }

    if (parent instanceof DomFileElementImpl) {
      final DomFileElementImpl fileElement = (DomFileElementImpl)parent;
      //noinspection unchecked
      return new RootAnchor<T>(fileElement.getFile(), fileElement.getRootElementClass());
    }

    final DomAnchorImpl<DomElement> parentAnchor = createAnchor(parent);
    final String name = t.getGenericInfo().getElementName(t);
    final AbstractDomChildrenDescription description = t.getChildDescription();
    final List<? extends DomElement> values = description.getValues(parent);
    if (name != null) {
      int i = 0;
      for (DomElement value : values) {
        if (value.equals(t)) {
          return new NamedAnchor<T>(parentAnchor, description, name, i);
        }
        if (name.equals(value.getGenericInfo().getElementName(value))) {
          i++;
        }
      }
    }

    final int index = values.indexOf(t);
    if (index < 0) {
      diagnoseNegativeIndex(t, parent, description, values);
    }
    return new IndexedAnchor<T>(parentAnchor, description, index);
  }

  private static <T extends DomElement> void diagnoseNegativeIndex(T t,
                                                                   DomElement parent,
                                                                   AbstractDomChildrenDescription description,
                                                                   List<? extends DomElement> values) {
    final XmlTag parentTag = parent.getXmlTag();
    StringBuilder diag = new StringBuilder("Index<0: description=" + description + "\nparent=" + parent + "\nt=" + t + "\nvalues=" + values + "\n");
    for (DomElement value : values) {
      if (value.toString().equals(t.toString())) {
        diag.append(" hasSame, same=" + (value == t) +
                    ", equal=" + value.equals(t) +
                    ", equal2=" + t.equals(value) +
                    ", sameElements=" + (t.getXmlElement() == value.getXmlElement()) +
                    "\n");
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
          diag.append(", subtag: ").append(tag.getName());
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

  @Nullable
  public abstract T retrieveDomElement();

  @NotNull
  public abstract XmlFile getContainingFile();

  private static class NamedAnchor<T extends DomElement> extends DomAnchorImpl<T> {
    private final DomAnchorImpl myParent;
    private final AbstractDomChildrenDescription myDescr;
    private final String myName;
    private final int myIndex;

    private NamedAnchor(final DomAnchorImpl parent, final AbstractDomChildrenDescription descr, final String id, int index) {
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

    @NotNull
    public XmlFile getContainingFile() {
      return myParent.getContainingFile();
    }
  }

  private static class IndexedAnchor<T extends DomElement> extends DomAnchorImpl<T> {
    private final DomAnchorImpl myParent;
    private final AbstractDomChildrenDescription myDescr;
    private final int myIndex;

    private IndexedAnchor(final DomAnchorImpl parent, final AbstractDomChildrenDescription descr, final int index) {
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

    public T retrieveDomElement() {
      final DomElement parent = myParent.retrieveDomElement();
      if (parent == null) return null;

      final List<? extends DomElement> list = myDescr.getValues(parent);
      if (myIndex < 0 || myIndex >= list.size()) return null;

      //noinspection unchecked
      return (T)list.get(myIndex);
    }

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

    public T retrieveDomElement() {
      final DomFileElement<T> fileElement = DomManager.getDomManager(myFile.getProject()).getFileElement(myFile, myClass);
      return fileElement == null ? null : fileElement.getRootElement();
    }

    @NotNull
    public XmlFile getContainingFile() {
      return myFile;
    }
  }


}
