package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.stubs.ElementStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class DomRootInvocationHandler extends DomInvocationHandler<AbstractDomChildDescriptionImpl, ElementStub> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomRootInvocationHandler");
  private final DomFileElementImpl<?> myParent;

  public DomRootInvocationHandler(final Class aClass,
                                  final RootDomParentStrategy strategy,
                                  @NotNull final DomFileElementImpl fileElement,
                                  @NotNull final EvaluatedXmlName tagName,
                                  @Nullable ElementStub stub
  ) {
    super(aClass, strategy, tagName, new AbstractDomChildDescriptionImpl(aClass) {
      @NotNull
      public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
        throw new UnsupportedOperationException();
      }

      public int compareTo(final AbstractDomChildDescriptionImpl o) {
        throw new UnsupportedOperationException();
      }
    }, fileElement.getManager(), true, stub);
    myParent = fileElement;
  }

  public void undefineInternal() {
    try {
      final XmlTag tag = getXmlTag();
      if (tag != null) {
        deleteTag(tag);
        detach();
        fireUndefinedEvent();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public boolean equals(final Object obj) {
    if (!(obj instanceof DomRootInvocationHandler)) return false;

    final DomRootInvocationHandler handler = (DomRootInvocationHandler)obj;
    return myParent.equals(handler.myParent);
  }

  public int hashCode() {
    return myParent.hashCode();
  }

  @NotNull
  public String getXmlElementNamespace() {
    return getXmlName().getNamespace(getFile(), getFile());
  }

  @Override
  protected String checkValidity() {
    final XmlTag tag = (XmlTag)getXmlElement();
    if (tag != null && !tag.isValid()) {
      return "invalid root tag";
    }

    final String s = myParent.checkValidity();
    if (s != null) {
      return "root: " + s;
    }

    return null;
  }

  @NotNull
  public DomFileElementImpl getParent() {
    return myParent;
  }

  public DomElement createPathStableCopy() {
    final DomFileElement stableCopy = myParent.createStableCopy();
    return getManager().createStableValue(new NullableFactory<DomElement>() {
      public DomElement create() {
        return stableCopy.isValid() ? stableCopy.getRootElement() : null;
      }
    });
  }

  protected XmlTag setEmptyXmlTag() {
    final XmlTag[] result = new XmlTag[]{null};
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          final String namespace = getXmlElementNamespace();
          @NonNls final String nsDecl = StringUtil.isEmpty(namespace) ? "" : " xmlns=\"" + namespace + "\"";
          final XmlFile xmlFile = getFile();
          final XmlTag tag = XmlElementFactory.getInstance(xmlFile.getProject()).createTagFromText("<" + getXmlElementName() + nsDecl + "/>");
          result[0] = ((XmlDocument)xmlFile.getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    return result[0];
  }

  @NotNull
  public final DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    return DomNameStrategy.HYPHEN_STRATEGY;
  }


}
