package com.intellij.lang.xhtml;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlLexer;
import com.intellij.openapi.project.Project;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 1:02:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class XHTMLParserDefinition extends XMLParserDefinition {

  @NotNull
  public Lexer createLexer(Project project) {
    return new XHtmlLexer();
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final Lexer lexer = createLexer(left.getPsi().getProject());
    return XmlUtil.canStickTokensTogetherByLexerInXml(left, right, lexer, 0);
  }
}
