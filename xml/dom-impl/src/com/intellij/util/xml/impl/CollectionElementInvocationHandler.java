package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.stubs.ElementStub;
import com.intellij.util.xml.stubs.StubParentStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler extends DomInvocationHandler<AbstractDomChildDescriptionImpl, ElementStub>{

  public CollectionElementInvocationHandler(final Type type, @NotNull final XmlTag tag,
                                            final AbstractCollectionChildDescription description,
                                            final DomInvocationHandler parent,
                                            @Nullable ElementStub stub) {
    super(type, new PhysicalDomParentStrategy(tag, parent.getManager()), description.createEvaluatedXmlName(parent, tag),
          (AbstractDomChildDescriptionImpl)description, parent.getManager(), true, stub);
  }

  public CollectionElementInvocationHandler(@NotNull EvaluatedXmlName tagName,
                                            AbstractDomChildDescriptionImpl childDescription,
                                            DomManagerImpl manager,
                                            ElementStub stub) {
    super(childDescription.getType(), new StubParentStrategy(stub), tagName, childDescription, manager, true, stub);

  }

  protected Type narrowType(@NotNull final Type nominalType) {
    return getStub() == null ? getManager().getTypeChooserManager().getTypeChooser(nominalType).chooseType(getXmlTag()) : nominalType;
  }

  protected final XmlTag setEmptyXmlTag() {
    throw new UnsupportedOperationException("CollectionElementInvocationHandler.setXmlTag() shouldn't be called;" +
                                            "\nparent=" + getParent() + ";\n" +
                                            "xmlElementName=" + getXmlElementName());
  }

  @Override
  protected String checkValidity() {
    final String s = super.checkValidity();
    if (s != null) {
      return s;
    }

    if (getXmlTag() == null) {
      return "no XmlTag for collection element: " + getDomElementType();
    }

    return null;
  }

  public final void undefineInternal() {
    final DomElement parent = getParent();
    final XmlTag tag = getXmlTag();
    if (tag == null) return;

    getManager().cacheHandler(getCacheKey(), tag, null);
    deleteTag(tag);
    getManager().fireEvent(new DomEvent(parent, false));
  }

  public DomElement createPathStableCopy() {
    final AbstractDomChildDescriptionImpl description = getChildDescription();
    final DomElement parent = getParent();
    assert parent != null;
    final DomElement parentCopy = parent.createStableCopy();
    final int index = description.getValues(parent).indexOf(getProxy());
    return getManager().createStableValue(new Factory<DomElement>() {
      @Nullable
      public DomElement create() {
        if (parentCopy.isValid()) {
          final List<? extends DomElement> list = description.getValues(parentCopy);
          if (list.size() > index) {
            return list.get(index);
          }
        }
        return null;
      }
    });
  }

  @Override
  public int hashCode() {
    ElementStub stub = getStub();
    if (stub != null) {
      return stub.getName().hashCode() + stub.id;
    }
    final XmlElement element = getXmlElement();
    return element == null ? super.hashCode() : element.hashCode();
  }
}
