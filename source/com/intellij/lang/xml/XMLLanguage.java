package com.intellij.lang.xml;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.xml.xml.XmlPseudoTextBuilder;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.java.JavaAdapter;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:59:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLLanguage extends Language {
  public XMLLanguage() {
    super("XML");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter();
  }

  public PseudoTextBuilder getFormatter() {
    if (CodeFormatterFacade.USE_NEW_CODE_FORMATTER <= 0) {
      return new JavaAdapter() {
        protected FileType getFileType() {
          return StdFileTypes.XML;
        }
      };
    }
    else {
      return new XmlPseudoTextBuilder();
    }
  }
}
