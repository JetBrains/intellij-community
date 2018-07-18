/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.javaee;

import com.intellij.xml.util.XmlUtil;

/**
 * @author Dmitry Avdeev
 */
public class InternalResourceProvider implements StandardResourceProvider{

  @Override
  public void registerResources(ResourceRegistrar registrar) {
    ResourceRegistrarImpl impl = (ResourceRegistrarImpl)registrar;

    impl.addInternalResource(XmlUtil.XSLT_URI,"xslt-1_0.xsd");
    impl.addInternalResource(XmlUtil.XSLT_URI,"2.0", "xslt-2_0.xsd");
    impl.addInternalResource(XmlUtil.XINCLUDE_URI,"xinclude.xsd");

    impl.addInternalResource(XmlUtil.XML_SCHEMA_URI, "XMLSchema.xsd");
    impl.addInternalResource(XmlUtil.XML_SCHEMA_URI + ".xsd", "XMLSchema.xsd");

    impl.addInternalResource("http://www.w3.org/2001/XMLSchema.dtd", "XMLSchema.dtd");
    impl.addInternalResource(XmlUtil.XML_SCHEMA_INSTANCE_URI, "XMLSchema-instance.xsd");
    impl.addInternalResource(XmlUtil.XML_SCHEMA_VERSIONING_URI, "XMLSchema-versioning.xsd");
    impl.addInternalResource("http://www.w3.org/2001/xml.xsd","xml.xsd");
    impl.addInternalResource(XmlUtil.XML_NAMESPACE_URI,"xml.xsd");
    impl.addInternalResource(XmlUtil.XHTML_URI,"xhtml1-transitional.xsd");
    impl.addInternalResource("http://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd","xhtml1-strict.xsd");

    impl.addInternalResource("http://www.w3.org/TR/html4/strict.dtd","xhtml1-strict.dtd");
    impl.addInternalResource(XmlUtil.HTML4_LOOSE_URI,"xhtml1-transitional.dtd");
    impl.addInternalResource("http://www.w3.org/TR/html4/frameset.dtd","xhtml1-frameset.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd","xhtml1-strict.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd","xhtml1-transitional.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd","xhtml1-frameset.dtd");
    impl.addInternalResource("http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd","xhtml11/xhtml11.dtd");

    impl.addInternalResource("urn:oasis:names:tc:entity:xmlns:xml:catalog", "catalog.xsd");

    // Plugins DTDs // stathik
    impl.addInternalResource("http://plugins.intellij.net/plugin.dtd", "plugin.dtd");
    impl.addInternalResource("http://plugins.intellij.net/plugin-repository.dtd", "plugin-repository.dtd");

    // mobile
    impl.addInternalResource("http://www.wapforum.org/DTD/xhtml-mobile10.dtd", "xhtml-mobile/xhtml-mobile10.dtd");
    impl.addInternalResource("http://www.wapforum.org/DTD/xhtml-mobile10-flat.dtd", "xhtml-mobile/xhtml-mobile10-flat.dtd");
    impl.addInternalResource("http://www.wapforum.org/DTD/xhtml-mobile12.dtd", "xhtml-mobile/xhtml-mobile12.dtd");

    impl.addInternalResource("http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd", "xhtml-mobile/xhtml-mobile12.dtd");

    impl.addInternalResource("http://www.w3.org/1999/xlink", "xlink.dtd");
  }
}
