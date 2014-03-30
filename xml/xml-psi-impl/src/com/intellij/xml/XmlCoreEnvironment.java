package com.intellij.xml;

import com.intellij.codeInspection.XmlSuppressionProvider;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.*;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.dtd.DTDParserDefinition;
import com.intellij.lang.dtd.DtdSyntaxHighlighterFactory;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lang.html.HtmlSyntaxHighlighterFactory;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xhtml.XHTMLParserDefinition;
import com.intellij.lang.xhtml.XhtmlSyntaxHighlighterFactory;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.lang.xml.XmlSyntaxHighlighterFactory;
import com.intellij.lexer.HtmlEmbeddedTokenTypesProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.impl.cache.impl.id.IdIndexers;
import com.intellij.psi.impl.cache.impl.idCache.XmlIdIndexer;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.xml.StartTagEndTokenProvider;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.util.XmlApplicationComponent;

/**
 * @author yole
 */
public class XmlCoreEnvironment {
  public static class ApplicationEnvironment {
    public ApplicationEnvironment(CoreApplicationEnvironment appEnvironment) {
      appEnvironment.registerFileType(HtmlFileType.INSTANCE, "html;htm;sht;shtm;shtml");
      appEnvironment.registerFileType(XHtmlFileType.INSTANCE, "xhtml");
      appEnvironment.registerFileType(DTDFileType.INSTANCE, "dtd;ent;mod;elt");

      appEnvironment.registerFileType(XmlFileType.INSTANCE, "xml;xsd;tld;xsl;jnlp;wsdl;jhm;ant;xul;xslt;rng;fxml");

      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(XMLLanguage.INSTANCE, new XmlSyntaxHighlighterFactory());
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(DTDLanguage.INSTANCE, new DtdSyntaxHighlighterFactory());
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(HTMLLanguage.INSTANCE, new HtmlSyntaxHighlighterFactory());
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(XHTMLLanguage.INSTANCE, new XhtmlSyntaxHighlighterFactory());

      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, XMLLanguage.INSTANCE, new XMLParserDefinition());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, DTDLanguage.INSTANCE, new DTDParserDefinition());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, HTMLLanguage.INSTANCE, new HTMLParserDefinition());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, XHTMLLanguage.INSTANCE, new XHTMLParserDefinition());

      XmlASTFactory astFactory = new XmlASTFactory();
      appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, astFactory);
      appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, HTMLLanguage.INSTANCE, astFactory);
      appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, XHTMLLanguage.INSTANCE, astFactory);
      appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, DTDLanguage.INSTANCE, astFactory);

      appEnvironment.addExplicitExtension(IdIndexers.INSTANCE, XmlFileType.INSTANCE, new XmlIdIndexer());
      appEnvironment.addExplicitExtension(IdIndexers.INSTANCE, DTDFileType.INSTANCE, new XmlIdIndexer());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), StartTagEndTokenProvider.EP_NAME, StartTagEndTokenProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlSuppressionProvider.EP_NAME, XmlSuppressionProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlFileNSInfoProvider.EP_NAME, XmlFileNSInfoProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlSchemaProvider.EP_NAME, XmlSchemaProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ImplicitNamespaceDescriptorProvider.EP_NAME, ImplicitNamespaceDescriptorProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlElementDescriptorProvider.EP_NAME, XmlElementDescriptorProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), Html5SchemaProvider.EP_NAME, Html5SchemaProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlAttributeDescriptorsProvider.EP_NAME, XmlAttributeDescriptorsProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlExtension.EP_NAME, XmlExtension.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), HtmlEmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, HtmlEmbeddedTokenTypesProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), StandardResourceProvider.EP_NAME, StandardResourceProvider.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), StandardResourceEP.EP_NAME, StandardResourceEP.class);

      appEnvironment.addExtension(MetaDataContributor.EP_NAME, new XmlApplicationComponent());
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new XmlNamespaceIndex());
      appEnvironment.addExtension(StandardResourceProvider.EP_NAME, new InternalResourceProvider());

      appEnvironment.registerApplicationService(ExternalResourceManager.class, createExternalResourceManager());
    }

    protected ExternalResourceManagerEx createExternalResourceManager() {
      return new CoreExternalResourceManager();
    }
  }

  public static class ProjectEnvironment {
    public ProjectEnvironment(CoreProjectEnvironment projectEnvironment) {
    }
  }
}
