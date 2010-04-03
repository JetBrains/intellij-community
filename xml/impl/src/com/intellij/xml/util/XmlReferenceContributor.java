/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider;
import com.intellij.patterns.PlatformPatterns;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.XmlPatterns.*;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DtdReferencesProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.IdReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.xml.*;

/**
 * @author peter
 */
public class XmlReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {

    final IdReferenceProvider idReferenceProvider = new IdReferenceProvider();

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, idReferenceProvider.getIdForAttributeNames(),
                                                       idReferenceProvider.getIdForFilter(), true, idReferenceProvider,
                                                       PsiReferenceRegistrar.DEFAULT_PRIORITY);

    final DtdReferencesProvider dtdReferencesProvider = new DtdReferencesProvider();
    //registerReferenceProvider(null, XmlEntityDecl.class,dtdReferencesProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlEntityRef.class), dtdReferencesProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlDoctype.class), dtdReferencesProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlElementDecl.class), dtdReferencesProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttlistDecl.class), dtdReferencesProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlElementContentSpec.class), dtdReferencesProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlToken.class), dtdReferencesProvider);

    URIReferenceProvider uriProvider = new URIReferenceProvider();
    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, null, dtdReferencesProvider.getSystemReferenceFilter(), uriProvider);


    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, new String[] { "href" }, new ScopeFilter(
      new ParentElementFilter(
        new AndFilter(
          new AndFilter(XmlTagFilter.INSTANCE, new XmlTextFilter("include")),
          new NamespaceFilter(XmlUtil.XINCLUDE_URI)
        ),
        2
      )
    ), true, uriProvider);

    final SchemaReferencesProvider schemaReferencesProvider = new SchemaReferencesProvider();

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar,
      schemaReferencesProvider.getCandidateAttributeNamesForSchemaReferences(),
      new ScopeFilter(
        new ParentElementFilter(
          new NamespaceFilter(XmlUtil.SCHEMA_URIS), 2
        )
      ),
      schemaReferencesProvider
    );

    registrar.registerReferenceProvider(xmlAttributeValue(xmlAttribute().withNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI)).
      withLocalName("type"), schemaReferencesProvider);

    registrar.registerReferenceProvider(xmlAttributeValue(xmlAttribute().withNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI)).
      withLocalName("noNamespaceSchemaLocation", "schemaLocation"), uriProvider);

    registrar.registerReferenceProvider(
      xmlAttributeValue().withLocalName("schemaLocation","namespace").
        withSuperParent(2,
                        xmlTag().withNamespace(XmlUtil.SCHEMA_URIS).withLocalName(string().oneOf("import", "include","redefine"))),
      uriProvider);

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, null, URIReferenceProvider.ELEMENT_FILTER, true, uriProvider);

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, new String[] {"encoding"}, new ScopeFilter(new ParentElementFilter(new ClassFilter(XmlProcessingInstruction.class))), true,
                                                       new XmlEncodingReferenceProvider());
  }
}
