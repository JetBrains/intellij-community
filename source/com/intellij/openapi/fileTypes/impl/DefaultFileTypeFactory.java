/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.*;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class DefaultFileTypeFactory implements NullableFunction<String, Pair<FileType,String>> {
  public Pair<FileType,String> fun(@NonNls final String s) {
    if ("ARCHIVE".equals(s)) return new Pair<FileType, String>(new ArchiveFileType(), "zip;jar;war;ear");
    if ("CLASS".equals(s)) return new Pair<FileType, String>(new JavaClassFileType(), "class");
    if ("HTML".equals(s)) return new Pair<FileType, String>(new HtmlFileType(), "html;htm;sht;shtm;shtml");
    if ("XHTML".equals(s)) return new Pair<FileType, String>(new XHtmlFileType(), "xhtml");
    if ("JAVA".equals(s)) return new Pair<FileType, String>(new JavaFileType(), "java");
    if ("PLAIN_TEXT".equals(s)) return new Pair<FileType, String>(new PlainTextFileType(), "txt;sh;bat;cmd;policy;log;cgi;pl;MF;sql;jad;jam");
    if ("XML".equals(s)) return new Pair<FileType, String>(new XmlFileType(), "xml;xsd;tld;xsl;jnlp;wsdl;hs;jhm;ant");
    if ("DTD".equals(s)) return new Pair<FileType, String>(new DTDFileType(), "dtd;ent;mod");
    if ("GUI_DESIGNER_FORM".equals(s)) return new Pair<FileType, String>(new GuiFormFileType(), "form");
    if ("IDEA_WORKSPACE".equals(s)) return new Pair<FileType, String>(new WorkspaceFileType(), "iws");
    if ("IDEA_PROJECT".equals(s)) return new Pair<FileType, String>(new ProjectFileType(), "ipr");
    if ("IDEA_MODULE".equals(s)) return new Pair<FileType, String>(new ModuleFileType(), "iml");
    if ("UNKNOWN".equals(s)) return new Pair<FileType, String>(new UnknownFileType(), null);
    if ("PROPERTIES".equals(s)) return new Pair<FileType, String>(PropertiesFileType.FILE_TYPE, "properties");
    if ("PATCH".equals(s)) return new Pair<FileType, String>(new PatchFileType(), "patch;diff");
    return null;
  }
}
