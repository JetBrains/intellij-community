package com.intellij.xml.util;

import com.intellij.html.impl.RelaxedHtmlFromSchemaNSDescriptor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.TargetNamespaceFilter;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.xml.*;
import com.intellij.xml.impl.schema.NamedObjectDescriptor;
import com.intellij.xml.impl.schema.SchemaNSDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class XmlApplicationComponent implements ApplicationComponent {

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
    {
      MetaRegistry.addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(XmlUtil.SCHEMA_URIS),
              new ClassFilter(XmlDocument.class)
          ),
          SchemaNSDescriptor.class
      );

      MetaRegistry.addMetadataBinding(
          new AndFilter(
            new ClassFilter(XmlTag.class),
            new NamespaceFilter(XmlUtil.SCHEMA_URIS),
           new XmlTextFilter("schema")
          ),
          SchemaNSDescriptor.class
      );
    }
    {
      MetaRegistry.addMetadataBinding(
          new OrFilter(
              new AndFilter(
                  new ContentFilter(
                    new OrFilter(
                      new ClassFilter(XmlElementDecl.class),
                      new ClassFilter(XmlConditionalSection.class),
                      new ClassFilter(XmlEntityRef.class)
                    )
                  ),
                  new ClassFilter(XmlDocument.class)
              ),
              new ClassFilter(XmlMarkupDecl.class)
          ),
          com.intellij.xml.impl.dtd.XmlNSDescriptorImpl.class
      );
    }

    {
      MetaRegistry.addMetadataBinding(new AndFilter(
          new ClassFilter(XmlTag.class),
          new NamespaceFilter(XmlUtil.SCHEMA_URIS),
          new XmlTextFilter("element")
      ),
                         XmlElementDescriptorImpl.class);
    }

    {
      MetaRegistry.addMetadataBinding(
          new AndFilter(
              new ClassFilter(XmlTag.class),
              new NamespaceFilter(XmlUtil.SCHEMA_URIS),
              new XmlTextFilter("attribute")
          ),
          XmlAttributeDescriptorImpl.class
      );
    }

    {
      MetaRegistry.addMetadataBinding(
          new ClassFilter(XmlElementDecl.class),
          com.intellij.xml.impl.dtd.XmlElementDescriptorImpl.class
      );
    }

    {
      MetaRegistry.addMetadataBinding(
          new ClassFilter(XmlAttributeDecl.class),
          com.intellij.xml.impl.dtd.XmlAttributeDescriptorImpl.class
      );
    }

    {
      MetaRegistry.addMetadataBinding(
          new AndFilter(
              new ClassFilter(XmlDocument.class),
              new TargetNamespaceFilter(XmlUtil.XHTML_URI),
              new NamespaceFilter(XmlUtil.SCHEMA_URIS)),
          RelaxedHtmlFromSchemaNSDescriptor.class
      );
    }

    {
      MetaRegistry.addMetadataBinding(new AndFilter(
          new ClassFilter(XmlTag.class),
          new NamespaceFilter(XmlUtil.SCHEMA_URIS),
          new XmlTextFilter("complexType","simpleType", "group","attributeGroup")
      ),
                         NamedObjectDescriptor.class);
    }
  }

  public void disposeComponent() {
  }

}
