package com.intellij.xml;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.highlighter.XmlFileType;

/**
 * @author yole
 */
public class XmlCoreEnvironment {
  public static void register(CoreApplicationEnvironment appEnvironment, CoreProjectEnvironment projectEnvironment) {
    //appEnvironment.registerFileType(HtmlFileType.INSTANCE, "html;htm;sht;shtm;shtml");
    //appEnvironment.registerFileType(XHtmlFileType.INSTANCE, "xhtml");
    //appEnvironment.registerFileType(DTDFileType.INSTANCE, "dtd;ent;mod;elt");
    //
    appEnvironment.registerFileType(XmlFileType.INSTANCE, "xml;xsd;tld;xsl;jnlp;wsdl;jhm;ant;xul;xslt;rng;fxml");
  }
}
