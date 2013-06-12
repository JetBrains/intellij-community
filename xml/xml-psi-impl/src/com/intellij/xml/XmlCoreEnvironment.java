package com.intellij.xml;

import com.intellij.codeInspection.XmlSuppressionProvider;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ImplicitNamespaceDescriptorProvider;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.dtd.DTDParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFileNSInfoProvider;

/**
 * @author yole
 */
public class XmlCoreEnvironment {
  public static void register(CoreApplicationEnvironment appEnvironment, CoreProjectEnvironment projectEnvironment) {
    appEnvironment.registerFileType(HtmlFileType.INSTANCE, "html;htm;sht;shtm;shtml");
    appEnvironment.registerFileType(XHtmlFileType.INSTANCE, "xhtml");
    appEnvironment.registerFileType(DTDFileType.INSTANCE, "dtd;ent;mod;elt");

    appEnvironment.registerFileType(XmlFileType.INSTANCE, "xml;xsd;tld;xsl;jnlp;wsdl;jhm;ant;xul;xslt;rng;fxml");

    appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, XMLLanguage.INSTANCE, new XMLParserDefinition());
    appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, DTDLanguage.INSTANCE, new DTDParserDefinition());

    XmlASTFactory astFactory = new XmlASTFactory();
    appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, astFactory);
    appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, HTMLLanguage.INSTANCE, astFactory);
    appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, XHTMLLanguage.INSTANCE, astFactory);
    appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, DTDLanguage.INSTANCE, astFactory);

    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlChildRole.EP_NAME, XmlChildRole.StartTagEndTokenProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlSuppressionProvider.EP_NAME, XmlSuppressionProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlFileNSInfoProvider.EP_NAME, XmlFileNSInfoProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlSchemaProvider.EP_NAME, XmlSchemaProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ImplicitNamespaceDescriptorProvider.EP_NAME, ImplicitNamespaceDescriptorProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlElementDescriptorProvider.EP_NAME, XmlElementDescriptorProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), Html5SchemaProvider.EP_NAME, Html5SchemaProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlAttributeDescriptorsProvider.EP_NAME, XmlAttributeDescriptorsProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlExtension.EP_NAME, XmlExtension.class);
  }
}
