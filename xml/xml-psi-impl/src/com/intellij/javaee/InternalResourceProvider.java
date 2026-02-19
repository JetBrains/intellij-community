// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.xml.util.XmlUtil;

/**
 * @author Dmitry Avdeev
 */
public final class InternalResourceProvider implements StandardResourceProvider {
  @Override
  public void registerResources(ResourceRegistrar registrar) {
    ResourceRegistrarImpl impl = (ResourceRegistrarImpl)registrar;

    ClassLoader classLoader = InternalResourceProvider.class.getClassLoader();

    impl.addInternalResource(XmlUtil.XSLT_URI, "xslt-1_0.xsd", classLoader);
    impl.addInternalResource(XmlUtil.XSLT_URI, "2.0", "xslt-2_0.xsd", classLoader);
    impl.addInternalResource(XmlUtil.XINCLUDE_URI, "xinclude.xsd", classLoader);

    impl.addInternalResource(XmlUtil.XML_SCHEMA_URI, "XMLSchema.xsd", classLoader);
    impl.addInternalResource(XmlUtil.XML_SCHEMA_URI + ".xsd", "XMLSchema.xsd", classLoader);

    impl.addInternalResource("http://www.w3.org/2001/XMLSchema.dtd", "XMLSchema.dtd", classLoader);
    impl.addInternalResource(XmlUtil.XML_SCHEMA_INSTANCE_URI, "XMLSchema-instance.xsd", classLoader);
    impl.addInternalResource(XmlUtil.XML_SCHEMA_VERSIONING_URI, "XMLSchema-versioning.xsd", classLoader);
    impl.addInternalResource("http://www.w3.org/2001/xml.xsd", "xml.xsd", classLoader);
    impl.addInternalResource(XmlUtil.XML_NAMESPACE_URI, "xml.xsd", classLoader);
    impl.addInternalResource(XmlUtil.XHTML_URI, "xhtml1-transitional.xsd", classLoader);
    impl.addInternalResource("http://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd", "xhtml1-strict.xsd", classLoader);

    impl.addInternalResource("http://www.w3.org/TR/html4/strict.dtd", "xhtml1-strict.dtd", classLoader);
    impl.addInternalResource(XmlUtil.HTML4_LOOSE_URI, "xhtml1-transitional.dtd", classLoader);
    impl.addInternalResource("http://www.w3.org/TR/html4/frameset.dtd", "xhtml1-frameset.dtd", classLoader);
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd", "xhtml1-strict.dtd", classLoader);
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd", "xhtml1-transitional.dtd", classLoader);
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd", "xhtml1-frameset.dtd", classLoader);
    impl.addInternalResource("http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd", "xhtml11/xhtml11.dtd", classLoader);

    impl.addInternalResource("urn:oasis:names:tc:entity:xmlns:xml:catalog", "catalog.xsd", classLoader);

    // Enterprise Plugin Repository
    impl.addInternalResource("http://plugins.intellij.net/plugin-repository.dtd", "plugin-repository.dtd", classLoader);

    // mobile
    impl.addInternalResource("http://www.wapforum.org/DTD/xhtml-mobile10.dtd", "xhtml-mobile/xhtml-mobile10.dtd", classLoader);
    impl.addInternalResource("http://www.wapforum.org/DTD/xhtml-mobile10-flat.dtd", "xhtml-mobile/xhtml-mobile10-flat.dtd", classLoader);
    impl.addInternalResource("http://www.wapforum.org/DTD/xhtml-mobile12.dtd", "xhtml-mobile/xhtml-mobile12.dtd", classLoader);

    impl.addInternalResource("http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd", "xhtml-mobile/xhtml-mobile12.dtd",
                             classLoader);

    impl.addInternalResource("http://www.w3.org/1999/xlink", "xlink.dtd", classLoader);
  }
}
