package com.intellij.tasks.generic;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.xpath.XPathFileType;

/**
 * User: evgeny.zakrevsky
 * Date: 10/25/12
 */
public enum ResponseType {
  XML("application/xml", XmlFileType.INSTANCE, XPathFileType.XPATH2),
  JSON("application/json", PlainTextFileType.INSTANCE, PlainTextFileType.INSTANCE),
  // TODO: think about possible selector type if it needed at all (e.g. CSS selector)
  HTML("text/html", HtmlFileType.INSTANCE, PlainTextFileType.INSTANCE),
  TEXT("text/plain", PlainTextFileType.INSTANCE, RegExpFileType.INSTANCE);

  private String myMimeType;
  private FileType myContentFileType;
  private FileType mySelectorFileType;

  ResponseType(final String s, final FileType contentFileType, final FileType selectorFileType) {
    myMimeType = s;
    myContentFileType = contentFileType;
    mySelectorFileType = selectorFileType;
  }

  public String getMimeType() {
    return myMimeType;
  }

  public FileType getContentFileType() {
    return myContentFileType;
  }

  public FileType getSelectorFileType() {
    return mySelectorFileType;
  }
}
