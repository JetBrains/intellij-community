package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class RedefinedElementDescriptor extends XmlElementDescriptorImpl {

  private final XmlNSDescriptorImpl myRedefined;
  private final XmlNSDescriptorImpl myOriginalNsDescriptor;

  public RedefinedElementDescriptor(XmlElementDescriptorImpl original,
                                    XmlNSDescriptorImpl redefined, XmlNSDescriptorImpl originalNsDescriptor) {
    super(original.myDescriptorTag);
    myRedefined = redefined;
    myOriginalNsDescriptor = originalNsDescriptor;
  }

  @Override
  public TypeDescriptor getType(XmlElement context) {
    TypeDescriptor typeDescriptor = super.getType(context);
    return typeDescriptor instanceof ComplexTypeDescriptor ? new RedefinedTypeDescriptor((ComplexTypeDescriptor)typeDescriptor, myRedefined,
                                                                                         myOriginalNsDescriptor) : typeDescriptor;
  }
}

class RedefinedTypeDescriptor extends ComplexTypeDescriptor {
  private final XmlNSDescriptorImpl myRedefinedNSDescriptor;
  private final XmlNSDescriptorImpl myOriginalNsDescriptor;

  RedefinedTypeDescriptor(ComplexTypeDescriptor original,
                          XmlNSDescriptorImpl redefined,
                          XmlNSDescriptorImpl originalNsDescriptor) {
    super(original.getNsDescriptor(), original.getDeclaration());
    myRedefinedNSDescriptor = redefined;
    myOriginalNsDescriptor = originalNsDescriptor;
  }

  @Override
  protected void processElements(XmlSchemaTagsProcessor processor, Map<String, XmlElementDescriptor> map) {
    String typeName = getTypeName();
    if (typeName != null) {
      TypeDescriptor descriptor = myOriginalNsDescriptor.getTypeDescriptor(typeName, myOriginalNsDescriptor.getTag());
      if (descriptor instanceof ComplexTypeDescriptor) {
        ((ComplexTypeDescriptor)descriptor).processElements(super.createProcessor(map, myOriginalNsDescriptor), map);
      }
    }
    super.processElements(createProcessor(map, myDocumentDescriptor), map);
  }

  @Override
  protected XmlSchemaTagsProcessor createProcessor(final Map<String, XmlElementDescriptor> map, final XmlNSDescriptorImpl nsDescriptor) {
    return new XmlSchemaTagsProcessor(nsDescriptor) {
      @Override
      protected void tagStarted(XmlTag tag, String tagName, XmlTag context, XmlTag ref) {
        addElementDescriptor(tag, tagName, map, null, myDocumentDescriptor);
        if ("extension".equals(tagName)) {
          String base = tag.getAttributeValue("base");
          if (base != null) {
            TypeDescriptor descriptor = myRedefinedNSDescriptor.findTypeDescriptor(base);
            if (descriptor instanceof ComplexTypeDescriptor) {
              XmlElementDescriptor[] elements = ((ComplexTypeDescriptor)descriptor).getElements(null);
              for (XmlElementDescriptor element : elements) {
                addElementDescriptor(map, element, null);
              }
            }
          }
        }
      }
    };
  }
}
