/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.fileTypes.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.ide.highlighter.*;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultFileTypeFactory extends FileTypeFactory {

  public void createFileTypes(final @NotNull PairConsumer<FileType, String> consumer) {
    consumer.consume(new ArchiveFileType(), "zip;jar;war;ear");
    consumer.consume(new JavaClassFileType(), "class");

    final FileType htmlFileType = new HtmlFileType();
    consumer.consume(htmlFileType, "html;htm;sht;shtm;shtml");
    CompletionUtil.registerCompletionData(htmlFileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return new HtmlCompletionData();
      }
    });

    final FileType xhtmlFileType = new XHtmlFileType();
    consumer.consume(xhtmlFileType, "xhtml");
    CompletionUtil.registerCompletionData(xhtmlFileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return new XHtmlCompletionData();
      }
    });

    final FileType dtdFileType = new DTDFileType();
    consumer.consume(dtdFileType, "dtd;ent;mod");
    CompletionUtil.registerCompletionData(dtdFileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return new DtdCompletionData();
      }
    });

    consumer.consume(new JavaFileType(), "java");
    consumer.consume(new PlainTextFileType(), "txt;sh;bat;cmd;policy;log;cgi;pl;MF;sql;jad;jam");

    final XmlFileType xmlFileType = new XmlFileType();
    consumer.consume(xmlFileType, "xml;xsd;tld;xsl;jnlp;wsdl;hs;jhm;ant;mxm;mxml");
    CompletionUtil.registerCompletionData(xmlFileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return new XmlCompletionData();
      }
    });

    consumer.consume(new GuiFormFileType(), "form");
    consumer.consume(new WorkspaceFileType(), "iws");
    consumer.consume(new ModuleFileType(), "iml");
    consumer.consume(new ProjectFileType(), "ipr");
    consumer.consume(new UnknownFileType(), null);
    consumer.consume(PropertiesFileType.FILE_TYPE, "properties");
    consumer.consume(new PatchFileType(), "patch;diff");
  }

}
