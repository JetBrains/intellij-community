// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * @author Maxim.Mossienko
 */
public class SchemaNSDescriptor extends XmlNSDescriptorImpl {
  private static final @NonNls String MIN_OCCURS_ATTR_NAME = "minOccurs";
  private static final @NonNls String MAX_OCCURS_ATTR_VALUE = "maxOccurs";
  private static final @NonNls String MAX_OCCURS_ATTR_NAME = MAX_OCCURS_ATTR_VALUE;
  private static final @NonNls String ID_ATTR_NAME = "id";
  private static final @NonNls String REF_ATTR_NAME = "ref";
  private static final @NonNls String DEFAULT_ATTR_NAME = "default";
  private static final @NonNls String FIXED_ATTR_NAME = "fixed";

  private static final @NonNls String NAME_ATTR_NAME = "name";

  private static final Validator<XmlTag> ELEMENT_VALIDATOR = new Validator<>() {
    @Override
    public void validate(final @NotNull XmlTag tag, @NotNull ValidationHost host) {
      if (!isFromSchemaNs(tag)) return;
      final boolean hasRefAttribute = tag.getAttributeValue(REF_ATTR_NAME) != null;

      if (hasRefAttribute) {
        for (XmlAttribute attr : tag.getAttributes()) {
          final String name = attr.getName();

          if (name.indexOf(':') == -1 &&
              !MIN_OCCURS_ATTR_NAME.equals(name) &&
              !MAX_OCCURS_ATTR_NAME.equals(name) &&
              !ID_ATTR_NAME.equals(name) &&
              !REF_ATTR_NAME.equals(name)) {
            host.addMessage(
              attr.getNameElement(),
              XmlPsiBundle.message("xml.schema.validation.attr.not.allowed.with.ref", name),
              ValidationHost.ErrorType.ERROR
            );
          }
        }
      }

      final String minOccursValue = tag.getAttributeValue("minOccurs");
      final String maxOccursValue = tag.getAttributeValue(MAX_OCCURS_ATTR_VALUE);

      if (minOccursValue != null && maxOccursValue != null) {
        try {
          final int minOccurs = Integer.parseInt(minOccursValue);
          final int maxOccurs = Integer.parseInt(maxOccursValue);
          if (maxOccurs < minOccurs) {
            host.addMessage(
              tag.getAttribute(MAX_OCCURS_ATTR_VALUE, null).getValueElement(),
              XmlPsiBundle.message("xml.schema.validation.max.occurs.should.be.not.less.than.min.occurs"),
              ValidationHost.ErrorType.ERROR
            );
          }
        }
        catch (NumberFormatException e) {
          // this schema will be reported by xerces validation
        }
      }

      if (!hasRefAttribute && tag.getAttributeValue(NAME_ATTR_NAME) == null) {
        host.addMessage(
          tag,
          XmlPsiBundle.message("xml.schema.validation.name.or.ref.should.present"),
          ValidationHost.ErrorType.ERROR
        );
      }
    }
  };

  private static final Validator<XmlTag> ATTRIBUTE_VALIDATOR = new Validator<>() {
    @Override
    public void validate(final @NotNull XmlTag tag, @NotNull ValidationHost host) {
      if (!isFromSchemaNs(tag)) return;

      if (tag.getAttributeValue(REF_ATTR_NAME) == null && tag.getAttributeValue(NAME_ATTR_NAME) == null) {
        host.addMessage(
          tag,
          XmlPsiBundle.message("xml.schema.validation.name.or.ref.should.present"),
          ValidationHost.ErrorType.ERROR
        );
      }

      if (tag.getAttributeValue(DEFAULT_ATTR_NAME) != null && tag.getAttributeValue(FIXED_ATTR_NAME) != null) {
        host.addMessage(
          tag.getAttribute(DEFAULT_ATTR_NAME, null).getNameElement(),
          XmlPsiBundle.message("xml.schema.validation.default.or.fixed.should.be.specified.but.not.both"),
          ValidationHost.ErrorType.ERROR
        );

        host.addMessage(
          tag.getAttribute(FIXED_ATTR_NAME, null).getNameElement(),
          XmlPsiBundle.message("xml.schema.validation.default.or.fixed.should.be.specified.but.not.both"),
          ValidationHost.ErrorType.ERROR
        );
      }
    }
  };

  private static final XmlUtil.DuplicationInfoProvider<XmlTag> SCHEMA_ATTR_DUP_INFO_PROVIDER = new XmlUtil.DuplicationInfoProvider<>() {
    @Override
    public String getName(final @NotNull XmlTag t) {
      return t.getAttributeValue(NAME_ATTR_NAME);
    }

    @Override
    public @NotNull String getNameKey(final @NotNull XmlTag t, @NotNull String name) {
      return name;
    }

    @Override
    public @NotNull PsiElement getNodeForMessage(final @NotNull XmlTag t) {
      return t.getAttribute(NAME_ATTR_NAME, null).getValueElement();
    }
  };

  private static final Validator<XmlTag> ELEMENT_AND_ATTR_VALIDATOR = new Validator<>() {
    @Override
    public void validate(final @NotNull XmlTag tag, @NotNull ValidationHost host) {
      if (!isFromSchemaNs(tag)) return;
      final String nsPrefix = tag.getNamespacePrefix();
      final XmlTag[] attrDeclTags = tag.findSubTags((!nsPrefix.isEmpty() ? nsPrefix + ":" : "") + "attribute");

      XmlUtil.doDuplicationCheckForElements(
        attrDeclTags,
        new HashMap<>(attrDeclTags.length),
        SCHEMA_ATTR_DUP_INFO_PROVIDER,
        host
      );

      final XmlTag[] elementDeclTags = tag.findSubTags((!nsPrefix.isEmpty() ? nsPrefix + ":" : "") + "element");

      XmlUtil.doDuplicationCheckForElements(
        elementDeclTags,
        new HashMap<>(elementDeclTags.length),
        SCHEMA_ATTR_DUP_INFO_PROVIDER,
        host
      );
    }
  };

  private static boolean isFromSchemaNs(final XmlTag tag) {
    return XmlUtil.XML_SCHEMA_URI.equals(tag.getNamespace());
  }

  @Override
  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    final XmlElementDescriptor descriptor = super.createElementDescriptor(tag);
    String localName = tag.getAttributeValue(NAME_ATTR_NAME);
    if (ELEMENT_TAG_NAME.equals(localName)) {
      ((XmlElementDescriptorImpl)descriptor).setValidator(ELEMENT_VALIDATOR);
    } else if (COMPLEX_TYPE_TAG_NAME.equals(localName) || SCHEMA_TAG_NAME.equals(localName) || SEQUENCE_TAG_NAME.equals(localName)) {
      ((XmlElementDescriptorImpl)descriptor).setValidator(ELEMENT_AND_ATTR_VALIDATOR);
    } else if (ATTRIBUTE_TAG_NAME.equals(localName)) {
      ((XmlElementDescriptorImpl)descriptor).setValidator(ATTRIBUTE_VALIDATOR);
    }
    return descriptor;
  }

  @Override
  public String toString() {
    return getDefaultNamespace();
  }
}
