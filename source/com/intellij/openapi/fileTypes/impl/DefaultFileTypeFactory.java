/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.*;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class DefaultFileTypeFactory implements NullableFunction<String, Pair<Factory<FileType>, String>> {
  public Pair<Factory<FileType>, String> fun(@NonNls final String s) {
    if ("ARCHIVE".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new ArchiveFileType();
        }
      }, "zip;jar;war;ear");
    }
    if ("CLASS".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new JavaClassFileType();
        }
      }, "class");
    }
    if ("HTML".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new HtmlFileType();
        }
      }, "html;htm;sht;shtm;shtml");
    }
    if ("XHTML".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new XHtmlFileType();
        }
      }, "xhtml");
    }
    if ("JAVA".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new JavaFileType();
        }
      }, "java");
    }
    if ("PLAIN_TEXT".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new PlainTextFileType();
        }
      }, "txt;sh;bat;cmd;policy;log;cgi;pl;MF;sql;jad;jam");
    }
    if ("XML".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new XmlFileType();
        }
      }, "xml;xsd;tld;xsl;jnlp;wsdl;hs;jhm;ant;mxm");
    }
    if ("DTD".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new DTDFileType();
        }
      }, "dtd;ent;mod");
    }
    if ("GUI_DESIGNER_FORM".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new GuiFormFileType();
        }
      }, "form");
    }
    if ("IDEA_WORKSPACE".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new WorkspaceFileType();
        }
      }, "iws");
    }
    if ("IDEA_PROJECT".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new ProjectFileType();
        }
      }, "ipr");
    }
    if ("IDEA_MODULE".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new ModuleFileType();
        }
      }, "iml");
    }
    if ("UNKNOWN".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new UnknownFileType();
        }
      }, null);
    }
    if ("PROPERTIES".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return PropertiesFileType.FILE_TYPE;
        }
      }, "properties");
    }
    if ("PATCH".equals(s)) {
      return new Pair<Factory<FileType>, String>(new Factory<FileType>() {
        public FileType create() {
          return new PatchFileType();
        }
      }, "patch;diff");
    }
    return null;
  }
}
