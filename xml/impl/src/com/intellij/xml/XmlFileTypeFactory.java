package com.intellij.xml;

import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class XmlFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(new HtmlFileType(), "html;htm;sht;shtm;shtml");
    consumer.consume(new XHtmlFileType(), "xhtml");
    consumer.consume(new DTDFileType(), "dtd;ent;mod");

    consumer.consume(new XmlFileType(), "xml;xsd;tld;xsl;jnlp;wsdl;hs;jhm;ant;mxm;mxml;xul");
  }
}
