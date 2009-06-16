package com.intellij.javaee;

import com.intellij.xml.util.XmlUtil;

/**
 * @author Dmitry Avdeev
 */
public class InternalResourceProvider implements StandardResourceProvider{
  
  public void registerResources(ResourceRegistrar registrar) {
    ResourceRegistrarImpl impl = (ResourceRegistrarImpl)registrar;
    
    impl.addInternalResource(XmlUtil.XSLT_URI,"xslt-1_0.xsd");
    impl.addInternalResource(XmlUtil.XSLT_URI,"2.0", "xslt-2_0.xsd");
    impl.addInternalResource(XmlUtil.XINCLUDE_URI,"xinclude.xsd");
    impl.addInternalResource(XmlUtil.XML_SCHEMA_URI, "XMLSchema.xsd");
    impl.addInternalResource(XmlUtil.XML_SCHEMA_URI + ".xsd", "XMLSchema.xsd");
    impl.addInternalResource("http://www.w3.org/2001/XMLSchema.dtd", "XMLSchema.dtd");
    impl.addInternalResource(XmlUtil.XML_SCHEMA_INSTANCE_URI, "XMLSchema-instance.xsd");
    impl.addInternalResource("http://www.w3.org/2001/xml.xsd","xml.xsd");
    impl.addInternalResource(XmlUtil.XML_NAMESPACE_URI,"xml.xsd");
    impl.addInternalResource(XmlUtil.XHTML_URI,"xhtml1-transitional.xsd");
    impl.addInternalResource("http://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd","xhtml1-strict.xsd");

    impl.addInternalResource("http://www.w3.org/TR/html4/strict.dtd","xhtml1-strict.dtd");
    impl.addInternalResource("http://www.w3.org/TR/html4/loose.dtd","xhtml1-transitional.dtd");
    impl.addInternalResource("http://www.w3.org/TR/html4/frameset.dtd","xhtml1-frameset.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd","xhtml1-strict.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd","xhtml1-transitional.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd","xhtml1-frameset.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd","xhtml11/xhtml11.dtd");

    // Plugins DTDs // stathik
    impl.addInternalResource("http://plugins.intellij.net/plugin.dtd", "plugin.dtd");
    impl.addInternalResource("http://plugins.intellij.net/plugin-repository.dtd", "plugin-repository.dtd");
    
  }
}
