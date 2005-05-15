package com.intellij.psi.impl.source.parsing;

import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.util.CharTable;
import com.intellij.pom.java.LanguageLevel;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 18:45:17
 * To change this template use File | Settings | File Templates.
 */
public class JavaParsingContext extends ParsingContext {
  private final DeclarationParsing myDeclarationParsing;
  private final ExpressionParsing myExpressionParsing;
  private final ClassBodyParsing myClassBodyParsing;
  private final StatementParsing myStatementParsing;
  private final ImportsTextParsing myImportsParsing;
  private final FileTextParsing myFileTextParsing;
  private final JavadocParsing myJavadocParsing;
  private final LanguageLevel myLanguageLevel;

  public JavaParsingContext(CharTable table, LanguageLevel languageLevel) {
    super(table);
    myLanguageLevel = languageLevel;
    myStatementParsing = new StatementParsing(this);
    myDeclarationParsing = new DeclarationParsing(this);
    myExpressionParsing = new ExpressionParsing(this);
    myClassBodyParsing = new ClassBodyParsing(this);
    myImportsParsing = new ImportsTextParsing(this);
    myFileTextParsing = new FileTextParsing(this);
    myJavadocParsing = new JavadocParsing(this);
  }

  public StatementParsing getStatementParsing() {
    return myStatementParsing;
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

  public JavadocParsing getJavadocParsing() {
    return myJavadocParsing;
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }
}
