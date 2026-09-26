// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider;
import com.intellij.html.impl.providers.MicrodataReferenceProvider;
import com.intellij.html.impl.util.MicrodataUtil;
import com.intellij.openapi.paths.WebReference;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ScopeFilter;
import com.intellij.psi.filters.XmlTagFilter;
import com.intellij.psi.filters.XmlTextFilter;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DtdReferencesProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.IdReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.XmlBaseReferenceProvider;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlElementContentSpec;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.XmlPatterns.xmlAttribute;
import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

public class XmlReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {

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
    ), true, new XmlBaseReferenceProvider(true));

    registrar.registerReferenceProvider(xmlAttributeValue().withLocalName("base").withNamespace(XmlUtil.XML_NAMESPACE_URI),
                                        new XmlBaseReferenceProvider(false));

    XmlUtil.registerXmlAttributeValueReferenceProvider(
      registrar,
      new String[]{MicrodataUtil.ITEM_TYPE},
      null,
      new MicrodataReferenceProvider()
    );

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
                        xmlTag().withNamespace(XmlUtil.SCHEMA_URIS).withLocalName("import", "include","redefine")),
      uriProvider);

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, null, URIReferenceProvider.ELEMENT_FILTER, true, uriProvider);

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, new String[] {"encoding"}, new ScopeFilter(new ParentElementFilter(new ClassFilter(XmlProcessingInstruction.class))), true,
                                                       new XmlEncodingReferenceProvider());

    registrar.registerReferenceProvider(xmlAttributeValue(), new XmlPrefixReferenceProvider());
    registrar.registerReferenceProvider(xmlAttributeValue(), new XmlEnumeratedValueReferenceProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);
    registrar.registerReferenceProvider(xmlTag(), XmlEnumeratedValueReferenceProvider.forTags(), PsiReferenceRegistrar.LOWER_PRIORITY);

    registrar.registerReferenceProvider(xmlAttributeValue().withLocalName("source")
                                          .withSuperParent(2, xmlTag().withLocalName("documentation").withNamespace(XmlUtil.SCHEMA_URIS)),
                                        new PsiReferenceProvider() {
                                          @Override
                                          public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                                                 @NotNull ProcessingContext context) {
                                            return new PsiReference[] { new WebReference(element) };
                                          }
                                        });
  }
}
