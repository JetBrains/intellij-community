// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.generic;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import org.intellij.lang.regexp.RegExpFileType;
import org.jetbrains.annotations.NotNull;


/**
 * ResponseType contains information about what selector types used
 * to extract information from server responses with specific content-type.
 *
 * @author evgeny.zakrevsky
 * @author Mikhail Golubev
 */
public enum ResponseType {

  XML("application/xml", XmlFileType.INSTANCE, findXPathFileType()),
  JSON("application/json", findFileTypePlainDefault("JSON"), PlainTextFileType.INSTANCE),
  // TODO: think about possible selector type if it needed at all (e.g. CSS selector)
  HTML("text/html", HtmlFileType.INSTANCE, PlainTextFileType.INSTANCE),
  TEXT("text/plain", PlainTextFileType.INSTANCE, RegExpFileType.INSTANCE);

  private final String myMimeType;
  private final FileType myContentFileType;
  private final FileType mySelectorFileType;

  private static Logger LOG = Logger.getInstance(ResponseType.class);


  ResponseType(@NotNull String s, @NotNull FileType contentFileType, @NotNull FileType selectorFileType) {
    myMimeType = s;
    myContentFileType = contentFileType;
    mySelectorFileType = selectorFileType;
  }

  /**
   * Unfortunately XPATH instance can't be received this way, because XPathSupportLoader
   * registers XPathFileType in FileTypeManager only in unit test and debug modes
   */
  private static @NotNull FileType findFileTypePlainDefault(final @NotNull String name) {
    FileType fileType = FileTypeManager.getInstance().findFileTypeByName(name);
    return fileType == null ? PlainTextFileType.INSTANCE : fileType;
  }

  /**
   * Temporary workaround for IDEA-112605
   */
  private static @NotNull FileType findXPathFileType() {
    if (LOG == null) {
      LOG = Logger.getInstance(ResponseType.class);
    }
    try {
      Class<?> xPathClass = Class.forName("org.intellij.lang.xpath.XPathFileType");
      LOG.debug("XPathFileType class loaded successfully");
      return (FileType)xPathClass.getField("XPATH").get(null);
    }
    catch (Exception e) {
      LOG.debug("XPathFileType class not found. Using PlainText.INSTANCE instead");
      return PlainTextFileType.INSTANCE;
    }
  }

  public @NotNull String getMimeType() {
    return myMimeType;
  }

  public @NotNull FileType getContentFileType() {
    return myContentFileType;
  }

  public @NotNull FileType getSelectorFileType() {
    return mySelectorFileType;
  }
}
