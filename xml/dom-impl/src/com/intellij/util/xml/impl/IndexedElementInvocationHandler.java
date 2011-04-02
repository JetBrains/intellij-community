package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler<FixedChildDescriptionImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.IndexedElementInvocationHandler");
  private final int myIndex;
  private String myNamespace;

  public IndexedElementInvocationHandler(final EvaluatedXmlName tagName,
                                         final FixedChildDescriptionImpl description,
                                         final int index,
                                         final DomParentStrategy strategy,
                                         final DomManagerImpl manager,
                                         final String namespace) {
    super(description.getType(), strategy, tagName, description, manager, strategy.getXmlElement() != null);
    myIndex = index;
    myNamespace = namespace;
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  public boolean equals(final Object obj) {
    return super.equals(obj) && myIndex == ((IndexedElementInvocationHandler)obj).myIndex;
  }

  public int hashCode() {
    return super.hashCode() * 239 + myIndex;
  }

  protected XmlElement recomputeXmlElement(@NotNull final DomInvocationHandler parentHandler) {
    final XmlTag tag = parentHandler.getXmlTag();
    if (tag == null) return null;

    final List<XmlTag> tags = DomImplUtil.findSubTags(tag, getXmlName(), parentHandler.getFile());
    if (tags.size() <= myIndex) return null;

    final XmlTag childTag = tags.get(myIndex);
    myNamespace = childTag.getNamespace();
    return childTag;
  }

  protected XmlTag setEmptyXmlTag() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    final FixedChildDescriptionImpl description = getChildDescription();
    final XmlFile xmlFile = getFile();
    parent.createFixedChildrenTags(getXmlName(), description, myIndex);
    final List<XmlTag> tags = DomImplUtil.findSubTags(parent.getXmlTag(), getXmlName(), xmlFile);
    if (tags.size() > myIndex) {
      final XmlTag tag = tags.get(myIndex);
      myNamespace = tag.getNamespace();
      return tag;
    }

    final XmlTag[] newTag = new XmlTag[1];
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          final XmlTag parentTag = parent.getXmlTag();
          newTag[0] = (XmlTag)parentTag.add(parent.createChildTag(getXmlName()));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    myNamespace = newTag[0].getNamespace();
    return newTag[0];
  }

  public void undefineInternal() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    final EvaluatedXmlName xmlElementName = getXmlName();
    final FixedChildDescriptionImpl description = getChildDescription();

    final int totalCount = description.getCount();

    final List<XmlTag> subTags = DomImplUtil.findSubTags(parentTag, xmlElementName, getFile());
    if (subTags.size() <= myIndex) {
      return;
    }

    XmlTag tag = getXmlTag();
    if (tag == null) return;

    final boolean changing = getManager().setChanging(true);
    try {
      detach();
      if (totalCount == myIndex + 1 && subTags.size() >= myIndex + 1) {
        for (int i = myIndex; i < subTags.size(); i++) {
          subTags.get(i).delete();
        }
      }
      else if (subTags.size() == myIndex + 1) {
        tag.delete();
      } else {
        setXmlElement((XmlTag) tag.replace(parent.createChildTag(getXmlName())));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    fireUndefinedEvent();
  }

  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final T annotation = getChildDescription().getAnnotation(myIndex, annotationClass);
    if (annotation != null) return annotation;

    return getClassAnnotation(annotationClass);
  }

  public final DomElement createPathStableCopy() {
    final DomFixedChildDescription description = getChildDescription();
    final DomElement parentCopy = getParent().createStableCopy();
    return getManager().createStableValue(new Factory<DomElement>() {
      public DomElement create() {
        return parentCopy.isValid() ? description.getValues(parentCopy).get(myIndex) : null;
      }
    });
  }

}
