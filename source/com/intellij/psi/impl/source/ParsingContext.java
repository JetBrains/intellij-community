package com.intellij.psi.impl.source;

import com.intellij.psi.impl.source.parsing.*;
import com.intellij.psi.impl.source.parsing.jsp.JspParsing;
import com.intellij.psi.impl.source.parsing.xml.XmlParsing;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 18:45:17
 * To change this template use File | Settings | File Templates.
 */
public class ParsingContext {
  private final CharTable myTable;
  private final DeclarationParsing myDeclarationParsing;
  private final ExpressionParsing myExpressionParsing;
  private final ClassBodyParsing myClassBodyParsing;
  private final StatementParsing myStatementParsing;
  private final ImportsTextParsing myImportsParsing;
  private final FileTextParsing myFileTextParsing;
  private final JspParsing myJspParsing;
  private final JavadocParsing myJavadocParsing;
  private final XmlParsing myXmlParsing;
  private TreeElement myCurrentReparseElement = null;

  public StatementParsing getStatementParsing() {
    return myStatementParsing;
  }

  public ParsingContext(CharTable table) {
    myTable = table;
    myStatementParsing = new StatementParsing(this);
    myDeclarationParsing = new DeclarationParsing(this);
    myExpressionParsing = new ExpressionParsing(this);
    myClassBodyParsing = new ClassBodyParsing(this);
    myImportsParsing = new ImportsTextParsing(this);
    myFileTextParsing = new FileTextParsing(this);
    myJspParsing = new JspParsing(this);
    myJavadocParsing = new JavadocParsing(this);
    myXmlParsing = new XmlParsing(this);
  }

  public CharTable getCharTable() {
    return myTable;
  }

  public DeclarationParsing getDeclarationParsing() {
    return myDeclarationParsing;
  }

  public ExpressionParsing getExpressionParsing() {
    return myExpressionParsing;
  }

  public ClassBodyParsing getClassBodyParsing() {
    return myClassBodyParsing;
  }

  public ImportsTextParsing getImportsTextParsing() {
    return myImportsParsing;
  }

  public FileTextParsing getFileTextParsing() {
    return myFileTextParsing;
  }

  public JspParsing getJspParsing() {
    return myJspParsing;
  }

  public JavadocParsing getJavadocParsing() {
    return myJavadocParsing;
  }

  public XmlParsing getXmlParsing(){
    return myXmlParsing;
  }

  public TreeElement getCurrentReparseElement(){
    return myCurrentReparseElement;
  }

  public void setCurrentReparseElement(TreeElement element){
    myCurrentReparseElement = element;
  }

}
