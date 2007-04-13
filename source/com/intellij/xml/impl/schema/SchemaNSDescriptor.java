package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 15, 2006
 * Time: 8:11:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class SchemaNSDescriptor extends XmlNSDescriptorImpl {
  @NonNls private static final String MIN_OCCURS_ATTR_NAME = "minOccurs";
  @NonNls private static final String MAX_OCCURS_ATTR_VALUE = "maxOccurs";
  @NonNls private static final String MAX_OCCURS_ATTR_NAME = MAX_OCCURS_ATTR_VALUE;
  @NonNls private static final String ID_ATTR_NAME = "id";
  @NonNls private static final String REF_ATTR_NAME = "ref";

  private static final Validator ELEMENT_VALIDATOR = new Validator() {
    public void validate(PsiElement context, ValidationHost host) {
      final XmlTag tag = ((XmlTag)context);

      if (tag.getAttributeValue(REF_ATTR_NAME) != null) {
        for(XmlAttribute attr:tag.getAttributes()) {
          final String name = attr.getName();

          if (name.indexOf(':') == -1 &&
              !MIN_OCCURS_ATTR_NAME.equals(name) &&
              !MAX_OCCURS_ATTR_NAME.equals(name) &&
              !ID_ATTR_NAME.equals(name) &&
              !REF_ATTR_NAME.equals(name)) {
            host.addMessage(
              attr,
              XmlBundle.message("xml.schema.validation.attr.not.allowed.with.ref", name),
              ValidationHost.ERROR
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
              XmlBundle.message("xml.schema.validation.max.occurs.should.be.not.less.than.min.occurs"),
              Validator.ValidationHost.ERROR
            );
          }
        }
        catch (NumberFormatException e) {
          // this schema will be reported by xerces validation
        }
      }
    }
  };

  private static XmlUtil.DuplicationInfoProvider<XmlTag> SCHEMA_ATTR_DUP_INFO_PROVIDER = new XmlUtil.DuplicationInfoProvider<XmlTag>() {
    public String getName(final XmlTag t) {
      return t.getAttributeValue("name");
    }

    public String getNameKey(final XmlTag t, String name) {
      return name;
    }

    @NotNull
    public PsiElement getNodeForMessage(final XmlTag t) {
      return t.getAttribute("name", null).getValueElement();
    }
  };

  private static final Validator ELEMENT_AND_ATTR_VALIDATOR = new Validator() {
    public void validate(PsiElement context, ValidationHost host) {
      final XmlTag tag = ((XmlTag)context);
      final String nsPrefix = tag.getNamespacePrefix();
      final XmlTag[] attrDeclTags = tag.findSubTags((nsPrefix.length() > 0 ? nsPrefix + ":" : "") + "attribute");

      XmlUtil.doDuplicationCheckForElements(
        attrDeclTags,
        new HashMap<String, XmlTag>(attrDeclTags.length),
        SCHEMA_ATTR_DUP_INFO_PROVIDER,
        host
      );

      final XmlTag[] elementDeclTags = tag.findSubTags((nsPrefix.length() > 0 ? nsPrefix + ":" : "") + "element");

      XmlUtil.doDuplicationCheckForElements(
        elementDeclTags,
        new HashMap<String, XmlTag>(elementDeclTags.length),
        SCHEMA_ATTR_DUP_INFO_PROVIDER,
        host
      );
    }
  };

  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    final XmlElementDescriptor descriptor = super.createElementDescriptor(tag);
    String localName = tag.getAttributeValue("name");
    if (ELEMENT_TAG_NAME.equals(localName)) {
      ((XmlElementDescriptorImpl)descriptor).setValidator(ELEMENT_VALIDATOR);
    } else if (COMPLEX_TYPE_TAG_NAME.equals(localName) || SCHEMA_TAG_NAME.equals(localName) || SEQUENCE_TAG_NAME.equals(localName)) {
      ((XmlElementDescriptorImpl)descriptor).setValidator(ELEMENT_AND_ATTR_VALIDATOR);
    }
    return descriptor;
  }
}
