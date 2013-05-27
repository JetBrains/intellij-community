package com.intellij.xml;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlChildRole;

/**
 * @author yole
 */
public class XmlCoreEnvironment {
  public static void register(CoreApplicationEnvironment appEnvironment, CoreProjectEnvironment projectEnvironment) {
    //appEnvironment.registerFileType(HtmlFileType.INSTANCE, "html;htm;sht;shtm;shtml");
    //appEnvironment.registerFileType(XHtmlFileType.INSTANCE, "xhtml");
    appEnvironment.registerFileType(DTDFileType.INSTANCE, "dtd;ent;mod;elt");

    appEnvironment.registerFileType(XmlFileType.INSTANCE, "xml;xsd;tld;xsl;jnlp;wsdl;jhm;ant;xul;xslt;rng;fxml");

    appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, XMLLanguage.INSTANCE, new XMLParserDefinition());

    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), XmlChildRole.EP_NAME, XmlChildRole.StartTagEndTokenProvider.class);
  }
}
