package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 15, 2006
 * Time: 8:11:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class SchemaNSDescriptor extends XmlNSDescriptorImpl {
  @NonNls private static final String MIN_OCCURS_ATTR_NAME = "minOccurs";
  @NonNls private static final String MAX_OCCURS_ATTR_NAME = "maxOccurs";
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
    }
  };

  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    final XmlElementDescriptor descriptor = super.createElementDescriptor(tag);
    String localName = tag.getAttributeValue("name");
    if (ELEMENT_TAG_NAME.equals(localName)) {
      ((XmlElementDescriptorImpl)descriptor).setValidator(ELEMENT_VALIDATOR);
    }
    return descriptor;
  }
}
