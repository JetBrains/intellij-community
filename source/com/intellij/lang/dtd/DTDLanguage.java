package com.intellij.lang.dtd;

import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.xml.XmlCommenter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:53:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class DTDLanguage extends Language {
  public DTDLanguage() {
    super("DTD");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    //TODO: should be antoher?
    return new XmlFileHighlighter(true);
  }

  public ParserDefinition getParserDefinition(Project project) {
    return new DTDParserDefinition(project);
  }

  public FindUsagesProvider getFindUsagesProvider() {
    return new FindUsagesManager.HtmlFindUsagesHandler();
  }

  public Commenter getCommenter() {
    return new XmlCommenter();
  }
}
