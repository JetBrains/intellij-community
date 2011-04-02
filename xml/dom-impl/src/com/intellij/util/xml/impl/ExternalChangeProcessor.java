package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.DomElement;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ExternalChangeProcessor implements XmlChangeVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.ExternalChangeProcessor");

  private final Map<XmlTag,DomInvocationHandler> myChangeSets = new HashMap<XmlTag, DomInvocationHandler>();
  private boolean myDocumentChanged;
  private final DomManagerImpl myDomManager;

  public ExternalChangeProcessor(DomManagerImpl domManager, XmlChangeSet changeSet) {
    myDomManager = domManager;
    for (XmlChange xmlChange : changeSet.getChanges()) {
      xmlChange.accept(this);
    }
  }

  public void processChanges() {
    if (myDocumentChanged) return;
    for (DomInvocationHandler handler : myChangeSets.values()) {
      myDomManager.fireEvent(new DomEvent(handler.getProxy(), false));
    }
  }

  private void addChange(XmlTag tag) {
    final PsiFile file = tag.getContainingFile();

    DomInvocationHandler handler = myChangeSets.get(tag);
    if (handler != null) return;

    while (tag != null) {
      final DomInvocationHandler data = tag.getUserData(DomManagerImpl.CACHED_DOM_HANDLER);
      if (data != null) {
        if (handler == null) {
          handler = data;
        }

        final Type chosenType = data.getDomElementType();
        final Type abstractType = data.getAbstractType();
        if (!chosenType.equals(abstractType) &&
            !chosenType.equals(myDomManager.getTypeChooserManager().getTypeChooser(chosenType).chooseType(tag))) {
          handler = data;
        }
      }
      tag = tag.getParentTag();
    }
    if (handler == null && file instanceof XmlFile) {
      final DomFileElementImpl<DomElement> element = DomManagerImpl.getCachedFileElement((XmlFile)file);
      if (element != null) {
        handler = element.getRootHandler();
      }
    }

    if (handler != null) {
      myChangeSets.put(tag, handler);
    }
  }

  public void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    addChange(xmlAttributeSet.getTag());
  }

  public void visitDocumentChanged(final XmlDocumentChanged change) {
    documentChanged((XmlFile)change.getDocument().getParent());
  }

  private void documentChanged(final XmlFile xmlFile) {
    myDocumentChanged = true;
    final DomFileElementImpl oldElement = DomManagerImpl.getCachedFileElement(xmlFile);
    if (oldElement != null) {
      final DomEvent[] events = myDomManager.recomputeFileElement(xmlFile);
      if (myDomManager.getFileElement(xmlFile) != oldElement) {
        for (final DomEvent event : events) {
          myDomManager.fireEvent(event);
        }
        return;
      }

      final DomInvocationHandler rootHandler = oldElement.getRootHandler();
      rootHandler.detach();
      final XmlTag rootTag = oldElement.getRootTag();
      if (rootTag != null) {
        LOG.assertTrue(rootTag.isValid());
        rootHandler.setXmlElement(rootTag);
      }
      myDomManager.fireEvent(new DomEvent(oldElement.getRootElement(), false));
    }
  }

  public void visitXmlElementChanged(final XmlElementChanged xmlElementChanged) {
    final XmlElement element = xmlElementChanged.getElement();
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlTag) {
      addChange((XmlTag)parent);
    }
  }

  public void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd) {
    addChange(xmlTagChildAdd.getTag());
  }

  public void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged) {
    addChange(xmlTagChildChanged.getTag());
  }

  public void visitXmlTagChildRemoved(final XmlTagChildRemoved xmlTagChildRemoved) {
    addChange(xmlTagChildRemoved.getTag());
  }

  public void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged) {
    final XmlTag tag = xmlTagNameChanged.getTag();
    final XmlTag parentTag = tag.getParentTag();
    if (parentTag != null) {
      addChange(parentTag);
    } else {
      final PsiFile file = tag.getContainingFile();
      if (file instanceof XmlFile) {
        documentChanged((XmlFile)file);
      }
    }
  }

  public void visitXmlTextChanged(final XmlTextChanged xmlTextChanged) {
    addChange(xmlTextChanged.getText().getParentTag());
  }
}
