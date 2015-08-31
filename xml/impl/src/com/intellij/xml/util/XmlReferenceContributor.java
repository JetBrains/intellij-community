/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider;
import com.intellij.html.impl.providers.MicrodataReferenceProvider;
import com.intellij.html.impl.util.MicrodataUtil;
import com.intellij.openapi.paths.WebReference;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.impl.source.resolve.reference.ArbitraryPlaceUrlReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.XmlPatterns.*;

/**
 * @author peter
 */
public class XmlReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {

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
    registrar.registerReferenceProvider(xmlAttributeValue(), new ArbitraryPlaceUrlReferenceProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);


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
                                          @NotNull
                                          @Override
                                          public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                                                       @NotNull ProcessingContext context) {
                                            return new PsiReference[] { new WebReference(element) };
                                          }
                                        });
  }
}
