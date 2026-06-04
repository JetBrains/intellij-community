// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.mermaid.lang.parser.MermaidElements.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Directives.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Frontmatter.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Pie.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Journey.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Flowchart.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Sequence.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.ClassDiagram.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.StateDiagram.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Gantt.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Requirement.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.C4.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Mindmap.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Timeline.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Quadrant.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.ZenUML.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Sankey.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.XYChart.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Block.*;
import static com.intellij.mermaid.lang.parser.ParserUtils.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class _MermaidParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return file(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // complexAccDescrMultilineValue [accDescrMultilineValueLines]
  public static boolean accDescrMultilineValueLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accDescrMultilineValueLines")) return false;
    if (!nextTokenIs(builder_, "<acc descr multiline value lines>", ACC_DESCR_MULTILINE_VALUE, EOL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ACC_DESCR_MULTILINE_VALUE_LINES, "<acc descr multiline value lines>");
    result_ = complexAccDescrMultilineValue(builder_, level_ + 1);
    result_ = result_ && accDescrMultilineValueLines_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [accDescrMultilineValueLines]
  private static boolean accDescrMultilineValueLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accDescrMultilineValueLines_1")) return false;
    accDescrMultilineValueLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ACC_TITLE COLON complexAccTitleValue
  //   | ACC_DESCR COLON complexAccDescrValue
  //   | ACC_DESCR OPEN_CURLY EOL* accDescrMultilineValueLines CLOSE_CURLY
  public static boolean accStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accStatement")) return false;
    if (!nextTokenIs(builder_, "<acc statement>", ACC_DESCR, ACC_TITLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ACC_STATEMENT, "<acc statement>");
    result_ = accStatement_0(builder_, level_ + 1);
    if (!result_) result_ = accStatement_1(builder_, level_ + 1);
    if (!result_) result_ = accStatement_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ACC_TITLE COLON complexAccTitleValue
  private static boolean accStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ACC_TITLE, COLON);
    result_ = result_ && complexAccTitleValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ACC_DESCR COLON complexAccDescrValue
  private static boolean accStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ACC_DESCR, COLON);
    result_ = result_ && complexAccDescrValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ACC_DESCR OPEN_CURLY EOL* accDescrMultilineValueLines CLOSE_CURLY
  private static boolean accStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ACC_DESCR, OPEN_CURLY);
    result_ = result_ && accStatement_2_2(builder_, level_ + 1);
    result_ = result_ && accDescrMultilineValueLines(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean accStatement_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "accStatement_2_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "accStatement_2_2", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ACTIVATE complexIdentifier
  public static boolean activateStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "activateStatement")) return false;
    if (!nextTokenIs(builder_, ACTIVATE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ACTIVATE);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, ACTIVATE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // [CREATE] (PARTICIPANT | ACTOR) complexIdentifier [AS idAlias]
  //   | DESTROY complexIdentifier
  public static boolean actorStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ACTOR_STATEMENT, "<actor statement>");
    result_ = actorStatement_0(builder_, level_ + 1);
    if (!result_) result_ = actorStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [CREATE] (PARTICIPANT | ACTOR) complexIdentifier [AS idAlias]
  private static boolean actorStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = actorStatement_0_0(builder_, level_ + 1);
    result_ = result_ && actorStatement_0_1(builder_, level_ + 1);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    result_ = result_ && actorStatement_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [CREATE]
  private static boolean actorStatement_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement_0_0")) return false;
    consumeToken(builder_, CREATE);
    return true;
  }

  // PARTICIPANT | ACTOR
  private static boolean actorStatement_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement_0_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PARTICIPANT);
    if (!result_) result_ = consumeToken(builder_, ACTOR);
    return result_;
  }

  // [AS idAlias]
  private static boolean actorStatement_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement_0_3")) return false;
    actorStatement_0_3_0(builder_, level_ + 1);
    return true;
  }

  // AS idAlias
  private static boolean actorStatement_0_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement_0_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AS);
    result_ = result_ && idAlias(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DESTROY complexIdentifier
  private static boolean actorStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "actorStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DESTROY);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ALT [complexControlId]
  public static boolean altHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altHeader")) return false;
    if (!nextTokenIs(builder_, ALT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ALT);
    result_ = result_ && altHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, ALT_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean altHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // altHeader EOL+ [sequenceBody] [EOL* elseSections] END
  public static boolean altStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altStatement")) return false;
    if (!nextTokenIs(builder_, ALT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = altHeader(builder_, level_ + 1);
    result_ = result_ && altStatement_1(builder_, level_ + 1);
    result_ = result_ && altStatement_2(builder_, level_ + 1);
    result_ = result_ && altStatement_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, ALT_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean altStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "altStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean altStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  // [EOL* elseSections]
  private static boolean altStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altStatement_3")) return false;
    altStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // EOL* elseSections
  private static boolean altStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = altStatement_3_0_0(builder_, level_ + 1);
    result_ = result_ && elseSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean altStatement_3_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "altStatement_3_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "altStatement_3_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // AND [complexControlId]
  public static boolean andHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andHeader")) return false;
    if (!nextTokenIs(builder_, AND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND);
    result_ = result_ && andHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, AND_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean andHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // andHeader EOL+ [sequenceBody]
  static boolean andSection(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSection")) return false;
    if (!nextTokenIs(builder_, AND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = andHeader(builder_, level_ + 1);
    result_ = result_ && andSection_1(builder_, level_ + 1);
    result_ = result_ && andSection_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL+
  private static boolean andSection_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSection_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "andSection_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean andSection_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSection_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // andSection [EOL* andSections]
  static boolean andSections(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSections")) return false;
    if (!nextTokenIs(builder_, AND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = andSection(builder_, level_ + 1);
    result_ = result_ && andSections_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL* andSections]
  private static boolean andSections_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSections_1")) return false;
    andSections_1_0(builder_, level_ + 1);
    return true;
  }

  // EOL* andSections
  private static boolean andSections_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSections_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = andSections_1_0_0(builder_, level_ + 1);
    result_ = result_ && andSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean andSections_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andSections_1_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "andSections_1_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ANNOTATION_START ANNOTATION_VALUE ANNOTATION_END
  public static boolean annotation(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation")) return false;
    if (!nextTokenIs(builder_, ANNOTATION_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ANNOTATION_START, ANNOTATION_VALUE, ANNOTATION_END);
    exit_section_(builder_, marker_, ANNOTATION, result_);
    return result_;
  }

  /* ********************************************************** */
  // annotation classDiagramIdentifier [generic]
  public static boolean annotationStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotationStatement")) return false;
    if (!nextTokenIs(builder_, ANNOTATION_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = annotation(builder_, level_ + 1);
    result_ = result_ && classDiagramIdentifier(builder_, level_ + 1);
    result_ = result_ && annotationStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, ANNOTATION_STATEMENT, result_);
    return result_;
  }

  // [generic]
  private static boolean annotationStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotationStatement_2")) return false;
    generic(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NUM ARROW NUM
  public static boolean arrowData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowData")) return false;
    if (!nextTokenIs(builder_, NUM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, NUM, ARROW, NUM);
    exit_section_(builder_, marker_, ARROW_DATA, result_);
    return result_;
  }

  /* ********************************************************** */
  // ATTR_KEY (COMMA ATTR_KEY)*
  public static boolean attrKeys(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrKeys")) return false;
    if (!nextTokenIs(builder_, ATTR_KEY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ATTR_KEY);
    result_ = result_ && attrKeys_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, ATTR_KEYS, result_);
    return result_;
  }

  // (COMMA ATTR_KEY)*
  private static boolean attrKeys_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrKeys_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!attrKeys_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "attrKeys_1", pos_)) break;
    }
    return true;
  }

  // COMMA ATTR_KEY
  private static boolean attrKeys_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrKeys_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, ATTR_KEY);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ATTRIBUTE_WORD
  public static boolean attrName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrName")) return false;
    if (!nextTokenIs(builder_, ATTRIBUTE_WORD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ATTRIBUTE_WORD);
    exit_section_(builder_, marker_, ATTR_NAME, result_);
    return result_;
  }

  /* ********************************************************** */
  // ATTRIBUTE_WORD | COMMA
  public static boolean attrType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrType")) return false;
    if (!nextTokenIs(builder_, "<attr type>", ATTRIBUTE_WORD, COMMA)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ATTR_TYPE, "<attr type>");
    result_ = consumeToken(builder_, ATTRIBUTE_WORD);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // [attrType] ( genericTypeId | TILDA | COMMA )+
  public static boolean attrTypeWithGeneric(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrTypeWithGeneric")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ATTR_TYPE_WITH_GENERIC, "<attr type with generic>");
    result_ = attrTypeWithGeneric_0(builder_, level_ + 1);
    result_ = result_ && attrTypeWithGeneric_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [attrType]
  private static boolean attrTypeWithGeneric_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrTypeWithGeneric_0")) return false;
    attrType(builder_, level_ + 1);
    return true;
  }

  // ( genericTypeId | TILDA | COMMA )+
  private static boolean attrTypeWithGeneric_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrTypeWithGeneric_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = attrTypeWithGeneric_1_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!attrTypeWithGeneric_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "attrTypeWithGeneric_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // genericTypeId | TILDA | COMMA
  private static boolean attrTypeWithGeneric_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrTypeWithGeneric_1_0")) return false;
    boolean result_;
    result_ = genericTypeId(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, TILDA);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    return result_;
  }

  /* ********************************************************** */
  // ATTRIBUTE_WORD
  //   | genericTypeId
  //   | maybeEmptyString
  //   | OPEN_SQUARE | CLOSE_SQUARE
  //   | OPEN_ROUND | CLOSE_ROUND
  //   | OPEN_ANGLE | CLOSE_ANGLE
  //   | PLUS | MINUS | POUND | TILDA | STAR | DOLLAR | COMMA | DOT
  //   | COLON | SEMICOLON
  static boolean attrWord(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrWord")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ATTRIBUTE_WORD);
    if (!result_) result_ = genericTypeId(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyString(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, OPEN_SQUARE);
    if (!result_) result_ = consumeToken(builder_, CLOSE_SQUARE);
    if (!result_) result_ = consumeToken(builder_, OPEN_ROUND);
    if (!result_) result_ = consumeToken(builder_, CLOSE_ROUND);
    if (!result_) result_ = consumeToken(builder_, OPEN_ANGLE);
    if (!result_) result_ = consumeToken(builder_, CLOSE_ANGLE);
    if (!result_) result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, POUND);
    if (!result_) result_ = consumeToken(builder_, TILDA);
    if (!result_) result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOLLAR);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, DOT);
    if (!result_) result_ = consumeToken(builder_, COLON);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    return result_;
  }

  /* ********************************************************** */
  // attrWord+
  public static boolean attribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attribute")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ATTRIBUTE, "<attribute>");
    result_ = attrWord(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!attrWord(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "attribute", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // AUTONUMBER (OFF | [NUM] [NUM])
  public static boolean autonumberStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "autonumberStatement")) return false;
    if (!nextTokenIs(builder_, AUTONUMBER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AUTONUMBER);
    result_ = result_ && autonumberStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, AUTONUMBER_STATEMENT, result_);
    return result_;
  }

  // OFF | [NUM] [NUM]
  private static boolean autonumberStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "autonumberStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OFF);
    if (!result_) result_ = autonumberStatement_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [NUM] [NUM]
  private static boolean autonumberStatement_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "autonumberStatement_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = autonumberStatement_1_1_0(builder_, level_ + 1);
    result_ = result_ && autonumberStatement_1_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [NUM]
  private static boolean autonumberStatement_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "autonumberStatement_1_1_0")) return false;
    consumeToken(builder_, NUM);
    return true;
  }

  // [NUM]
  private static boolean autonumberStatement_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "autonumberStatement_1_1_1")) return false;
    consumeToken(builder_, NUM);
    return true;
  }

  /* ********************************************************** */
  // (X_AXIS | Y_AXIS) (quadrantComplexText) [ARROW quadrantComplexText?]
  public static boolean axisDetailsStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "axisDetailsStatement")) return false;
    if (!nextTokenIs(builder_, "<axis details statement>", X_AXIS, Y_AXIS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, AXIS_DETAILS_STATEMENT, "<axis details statement>");
    result_ = axisDetailsStatement_0(builder_, level_ + 1);
    result_ = result_ && axisDetailsStatement_1(builder_, level_ + 1);
    result_ = result_ && axisDetailsStatement_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // X_AXIS | Y_AXIS
  private static boolean axisDetailsStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "axisDetailsStatement_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, X_AXIS);
    if (!result_) result_ = consumeToken(builder_, Y_AXIS);
    return result_;
  }

  // (quadrantComplexText)
  private static boolean axisDetailsStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "axisDetailsStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantComplexText(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ARROW quadrantComplexText?]
  private static boolean axisDetailsStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "axisDetailsStatement_2")) return false;
    axisDetailsStatement_2_0(builder_, level_ + 1);
    return true;
  }

  // ARROW quadrantComplexText?
  private static boolean axisDetailsStatement_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "axisDetailsStatement_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ARROW);
    result_ = result_ && axisDetailsStatement_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // quadrantComplexText?
  private static boolean axisDetailsStatement_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "axisDetailsStatement_2_0_1")) return false;
    quadrantComplexText(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // OPEN_SQUARE (xyChartText (COMMA xyChartText)*) CLOSE_SQUARE
  public static boolean bandData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bandData")) return false;
    if (!nextTokenIs(builder_, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_SQUARE);
    result_ = result_ && bandData_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, BAND_DATA, result_);
    return result_;
  }

  // xyChartText (COMMA xyChartText)*
  private static boolean bandData_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bandData_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xyChartText(builder_, level_ + 1);
    result_ = result_ && bandData_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA xyChartText)*
  private static boolean bandData_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bandData_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bandData_1_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bandData_1_1", pos_)) break;
    }
    return true;
  }

  // COMMA xyChartText
  private static boolean bandData_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bandData_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && xyChartText(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // BAR_KEYWORD [xyChartText] plotData
  public static boolean barStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "barStatement")) return false;
    if (!nextTokenIs(builder_, BAR_KEYWORD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BAR_KEYWORD);
    result_ = result_ && barStatement_1(builder_, level_ + 1);
    result_ = result_ && plotData(builder_, level_ + 1);
    exit_section_(builder_, marker_, BAR_STATEMENT, result_);
    return result_;
  }

  // [xyChartText]
  private static boolean barStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "barStatement_1")) return false;
    xyChartText(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ARROW
  //   | START_ARROW string ARROW
  public static boolean blockDiagramArrow(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramArrow")) return false;
    if (!nextTokenIs(builder_, "<block diagram arrow>", ARROW, START_ARROW)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK_DIAGRAM_ARROW, "<block diagram arrow>");
    result_ = consumeToken(builder_, ARROW);
    if (!result_) result_ = blockDiagramArrow_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // START_ARROW string ARROW
  private static boolean blockDiagramArrow_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramArrow_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, START_ARROW);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // blockDiagramLines
  public static boolean blockDiagramBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK_DIAGRAM_BODY, "<block diagram body>");
    result_ = blockDiagramLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (blockDiagramNodeStatementInner | spaceStatementInner) (blockDiagramNodeStatementInner | spaceStatementInner)+
  public static boolean blockDiagramComplexStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramComplexStatement")) return false;
    if (!nextTokenIs(builder_, "<block diagram complex statement>", ID, SPACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK_DIAGRAM_COMPLEX_STATEMENT, "<block diagram complex statement>");
    result_ = blockDiagramComplexStatement_0(builder_, level_ + 1);
    result_ = result_ && blockDiagramComplexStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // blockDiagramNodeStatementInner | spaceStatementInner
  private static boolean blockDiagramComplexStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramComplexStatement_0")) return false;
    boolean result_;
    result_ = blockDiagramNodeStatementInner(builder_, level_ + 1);
    if (!result_) result_ = spaceStatementInner(builder_, level_ + 1);
    return result_;
  }

  // (blockDiagramNodeStatementInner | spaceStatementInner)+
  private static boolean blockDiagramComplexStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramComplexStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramComplexStatement_1_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!blockDiagramComplexStatement_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "blockDiagramComplexStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // blockDiagramNodeStatementInner | spaceStatementInner
  private static boolean blockDiagramComplexStatement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramComplexStatement_1_0")) return false;
    boolean result_;
    result_ = blockDiagramNodeStatementInner(builder_, level_ + 1);
    if (!result_) result_ = spaceStatementInner(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // BLOCK_DIAGRAM
  public static boolean blockDiagramHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramHeader")) return false;
    if (!nextTokenIs(builder_, BLOCK_DIAGRAM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BLOCK_DIAGRAM);
    exit_section_(builder_, marker_, BLOCK_DIAGRAM_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // blockDiagramStatement [separator] | separator
  static boolean blockDiagramLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // blockDiagramStatement [separator]
  private static boolean blockDiagramLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramStatement(builder_, level_ + 1);
    result_ = result_ && blockDiagramLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean blockDiagramLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // blockDiagramLine [blockDiagramLines]
  static boolean blockDiagramLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramLine(builder_, level_ + 1);
    result_ = result_ && blockDiagramLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [blockDiagramLines]
  private static boolean blockDiagramLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramLines_1")) return false;
    blockDiagramLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // identifier [blockDiagramNodeDescr]
  public static boolean blockDiagramNode(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNode")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = identifier(builder_, level_ + 1);
    result_ = result_ && blockDiagramNode_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, BLOCK_DIAGRAM_NODE, result_);
    return result_;
  }

  // [blockDiagramNodeDescr]
  private static boolean blockDiagramNode_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNode_1")) return false;
    blockDiagramNodeDescr(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NODE_DESCR_START string NODE_DESCR_END
  //   | ARROW_DESCR_START string ARROW_DESCR_END OPEN_ROUND directions CLOSE_ROUND
  public static boolean blockDiagramNodeDescr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeDescr")) return false;
    if (!nextTokenIs(builder_, "<block diagram node descr>", ARROW_DESCR_START, NODE_DESCR_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK_DIAGRAM_NODE_DESCR, "<block diagram node descr>");
    result_ = blockDiagramNodeDescr_0(builder_, level_ + 1);
    if (!result_) result_ = blockDiagramNodeDescr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // NODE_DESCR_START string NODE_DESCR_END
  private static boolean blockDiagramNodeDescr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeDescr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NODE_DESCR_START);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, NODE_DESCR_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ARROW_DESCR_START string ARROW_DESCR_END OPEN_ROUND directions CLOSE_ROUND
  private static boolean blockDiagramNodeDescr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeDescr_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ARROW_DESCR_START);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, ARROW_DESCR_END, OPEN_ROUND);
    result_ = result_ && directions(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_ROUND);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // blockDiagramNodeStatementInner
  public static boolean blockDiagramNodeStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeStatement")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramNodeStatementInner(builder_, level_ + 1);
    exit_section_(builder_, marker_, BLOCK_DIAGRAM_NODE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // blockDiagramNode [blockSize] (blockDiagramArrow blockDiagramNode)*
  public static boolean blockDiagramNodeStatementInner(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeStatementInner")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramNode(builder_, level_ + 1);
    result_ = result_ && blockDiagramNodeStatementInner_1(builder_, level_ + 1);
    result_ = result_ && blockDiagramNodeStatementInner_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, BLOCK_DIAGRAM_NODE_STATEMENT_INNER, result_);
    return result_;
  }

  // [blockSize]
  private static boolean blockDiagramNodeStatementInner_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeStatementInner_1")) return false;
    blockSize(builder_, level_ + 1);
    return true;
  }

  // (blockDiagramArrow blockDiagramNode)*
  private static boolean blockDiagramNodeStatementInner_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeStatementInner_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!blockDiagramNodeStatementInner_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "blockDiagramNodeStatementInner_2", pos_)) break;
    }
    return true;
  }

  // blockDiagramArrow blockDiagramNode
  private static boolean blockDiagramNodeStatementInner_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramNodeStatementInner_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramArrow(builder_, level_ + 1);
    result_ = result_ && blockDiagramNode(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // directive
  //   | accStatement
  //   | blockDiagramComplexStatement
  //   | blockDiagramNodeStatement
  //   | columnsStatement
  //   | blockStatement
  //   | spaceStatement
  //   | classDefStatement
  //   | flowchartClassStatement
  //   | styleStatement
  static boolean blockDiagramStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockDiagramStatement")) return false;
    boolean result_;
    result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    if (!result_) result_ = blockDiagramComplexStatement(builder_, level_ + 1);
    if (!result_) result_ = blockDiagramNodeStatement(builder_, level_ + 1);
    if (!result_) result_ = columnsStatement(builder_, level_ + 1);
    if (!result_) result_ = blockStatement(builder_, level_ + 1);
    if (!result_) result_ = spaceStatement(builder_, level_ + 1);
    if (!result_) result_ = classDefStatement(builder_, level_ + 1);
    if (!result_) result_ = flowchartClassStatement(builder_, level_ + 1);
    if (!result_) result_ = styleStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // COLON NUM
  public static boolean blockSize(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockSize")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COLON, NUM);
    exit_section_(builder_, marker_, BLOCK_SIZE, result_);
    return result_;
  }

  /* ********************************************************** */
  // blockStatementHeader EOL* blockDiagramBody END
  public static boolean blockStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockStatement")) return false;
    if (!nextTokenIs(builder_, BLOCK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockStatementHeader(builder_, level_ + 1);
    result_ = result_ && blockStatement_1(builder_, level_ + 1);
    result_ = result_ && blockDiagramBody(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, BLOCK_STATEMENT, result_);
    return result_;
  }

  // EOL*
  private static boolean blockStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockStatement_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "blockStatement_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // BLOCK [COLON blockDiagramNodeStatementInner | blockSize]
  public static boolean blockStatementHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockStatementHeader")) return false;
    if (!nextTokenIs(builder_, BLOCK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BLOCK);
    result_ = result_ && blockStatementHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, BLOCK_STATEMENT_HEADER, result_);
    return result_;
  }

  // [COLON blockDiagramNodeStatementInner | blockSize]
  private static boolean blockStatementHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockStatementHeader_1")) return false;
    blockStatementHeader_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON blockDiagramNodeStatementInner | blockSize
  private static boolean blockStatementHeader_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockStatementHeader_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockStatementHeader_1_0_0(builder_, level_ + 1);
    if (!result_) result_ = blockSize(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COLON blockDiagramNodeStatementInner
  private static boolean blockStatementHeader_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockStatementHeader_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && blockDiagramNodeStatementInner(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL* c4Lines? CLOSE_CURLY
  public static boolean boundaryBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && boundaryBlock_1(builder_, level_ + 1);
    result_ = result_ && boundaryBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, BOUNDARY_BLOCK, result_);
    return result_;
  }

  // EOL*
  private static boolean boundaryBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryBlock_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "boundaryBlock_1", pos_)) break;
    }
    return true;
  }

  // c4Lines?
  private static boolean boundaryBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryBlock_2")) return false;
    c4Lines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ENTERPRISE_BOUNDARY c4Attributes
  //   | SYSTEM_BOUNDARY c4Attributes
  //   | BOUNDARY c4Attributes
  //   | CONTAINER_BOUNDARY c4Attributes
  //   | NODE c4Attributes
  //   | NODE_L c4Attributes
  //   | NODE_R c4Attributes
  public static boolean boundaryHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BOUNDARY_HEADER, "<boundary header>");
    result_ = boundaryHeader_0(builder_, level_ + 1);
    if (!result_) result_ = boundaryHeader_1(builder_, level_ + 1);
    if (!result_) result_ = boundaryHeader_2(builder_, level_ + 1);
    if (!result_) result_ = boundaryHeader_3(builder_, level_ + 1);
    if (!result_) result_ = boundaryHeader_4(builder_, level_ + 1);
    if (!result_) result_ = boundaryHeader_5(builder_, level_ + 1);
    if (!result_) result_ = boundaryHeader_6(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ENTERPRISE_BOUNDARY c4Attributes
  private static boolean boundaryHeader_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ENTERPRISE_BOUNDARY);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM_BOUNDARY c4Attributes
  private static boolean boundaryHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM_BOUNDARY);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BOUNDARY c4Attributes
  private static boolean boundaryHeader_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BOUNDARY);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_BOUNDARY c4Attributes
  private static boolean boundaryHeader_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER_BOUNDARY);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NODE c4Attributes
  private static boolean boundaryHeader_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NODE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NODE_L c4Attributes
  private static boolean boundaryHeader_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NODE_L);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NODE_R c4Attributes
  private static boolean boundaryHeader_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryHeader_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NODE_R);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // boundaryHeader boundaryBlock
  public static boolean boundaryStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boundaryStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BOUNDARY_STATEMENT, "<boundary statement>");
    result_ = boundaryHeader(builder_, level_ + 1);
    result_ = result_ && boundaryBlock(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // boxLines
  public static boolean boxBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxBlock")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BOX_BLOCK, "<box block>");
    result_ = boxLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // BOX [complexControlId]
  public static boolean boxHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxHeader")) return false;
    if (!nextTokenIs(builder_, BOX)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BOX);
    result_ = result_ && boxHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, BOX_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean boxHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // actorStatement [separator] | separator
  static boolean boxLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = boxLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // actorStatement [separator]
  private static boolean boxLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = actorStatement(builder_, level_ + 1);
    result_ = result_ && boxLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean boxLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (boxLine | IGNORED) [boxLines]
  static boolean boxLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = boxLines_0(builder_, level_ + 1);
    result_ = result_ && boxLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // boxLine | IGNORED
  private static boolean boxLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxLines_0")) return false;
    boolean result_;
    result_ = boxLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [boxLines]
  private static boolean boxLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxLines_1")) return false;
    boxLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // boxHeader EOL+ [boxBlock] END
  public static boolean boxStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxStatement")) return false;
    if (!nextTokenIs(builder_, BOX)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = boxHeader(builder_, level_ + 1);
    result_ = result_ && boxStatement_1(builder_, level_ + 1);
    result_ = result_ && boxStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, BOX_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean boxStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "boxStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [boxBlock]
  private static boolean boxStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boxStatement_2")) return false;
    boxBlock(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ORDER COLON NUM
  public static boolean branchOrder(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "branchOrder")) return false;
    if (!nextTokenIs(builder_, ORDER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ORDER, COLON, NUM);
    exit_section_(builder_, marker_, BRANCH_ORDER, result_);
    return result_;
  }

  /* ********************************************************** */
  // BRANCH gitGraphBranchIdentifier [branchOrder]
  public static boolean branchStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "branchStatement")) return false;
    if (!nextTokenIs(builder_, BRANCH)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BRANCH);
    result_ = result_ && gitGraphBranchIdentifier(builder_, level_ + 1);
    result_ = result_ && branchStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, BRANCH_STATEMENT, result_);
    return result_;
  }

  // [branchOrder]
  private static boolean branchStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "branchStatement_2")) return false;
    branchOrder(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // BREAK [complexControlId]
  public static boolean breakHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakHeader")) return false;
    if (!nextTokenIs(builder_, BREAK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BREAK);
    result_ = result_ && breakHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, BREAK_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean breakHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // breakHeader EOL+ [sequenceBody] END
  public static boolean breakStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakStatement")) return false;
    if (!nextTokenIs(builder_, BREAK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = breakHeader(builder_, level_ + 1);
    result_ = result_ && breakStatement_1(builder_, level_ + 1);
    result_ = result_ && breakStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, BREAK_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean breakStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "breakStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean breakStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // DOLLAR C4_ATTRIBUTE EQUALITY string | c4AttrComplexName | maybeEmptyString
  public static boolean c4Attr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Attr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, C_4_ATTR, "<c 4 attr>");
    result_ = c4Attr_0(builder_, level_ + 1);
    if (!result_) result_ = c4AttrComplexName(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyString(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // DOLLAR C4_ATTRIBUTE EQUALITY string
  private static boolean c4Attr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Attr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOLLAR, C4_ATTRIBUTE, EQUALITY);
    result_ = result_ && string(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (C4_ATTRIBUTE | DOLLAR | EQUALITY)+
  public static boolean c4AttrComplexName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4AttrComplexName")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, C_4_ATTR_COMPLEX_NAME, "<c 4 attr complex name>");
    result_ = c4AttrComplexName_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!c4AttrComplexName_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "c4AttrComplexName", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // C4_ATTRIBUTE | DOLLAR | EQUALITY
  private static boolean c4AttrComplexName_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4AttrComplexName_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, C4_ATTRIBUTE);
    if (!result_) result_ = consumeToken(builder_, DOLLAR);
    if (!result_) result_ = consumeToken(builder_, EQUALITY);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_ROUND c4AttributesRec CLOSE_ROUND
  public static boolean c4Attributes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Attributes")) return false;
    if (!nextTokenIs(builder_, OPEN_ROUND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_ROUND);
    result_ = result_ && c4AttributesRec(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_ROUND);
    exit_section_(builder_, marker_, C_4_ATTRIBUTES, result_);
    return result_;
  }

  /* ********************************************************** */
  // [c4Attr] [COMMA c4AttributesRec]
  static boolean c4AttributesRec(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4AttributesRec")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = c4AttributesRec_0(builder_, level_ + 1);
    result_ = result_ && c4AttributesRec_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [c4Attr]
  private static boolean c4AttributesRec_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4AttributesRec_0")) return false;
    c4Attr(builder_, level_ + 1);
    return true;
  }

  // [COMMA c4AttributesRec]
  private static boolean c4AttributesRec_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4AttributesRec_1")) return false;
    c4AttributesRec_1_0(builder_, level_ + 1);
    return true;
  }

  // COMMA c4AttributesRec
  private static boolean c4AttributesRec_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4AttributesRec_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && c4AttributesRec(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // c4Lines
  public static boolean c4Body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, C_4_BODY, "<c 4 body>");
    result_ = c4Lines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // PERSON c4Attributes
  //   | PERSON_EXT c4Attributes
  //   | SYSTEM c4Attributes
  //   | SYSTEM_DB c4Attributes
  //   | SYSTEM_QUEUE c4Attributes
  //   | SYSTEM_EXT c4Attributes
  //   | SYSTEM_EXT_DB c4Attributes
  //   | SYSTEM_EXT_QUEUE c4Attributes
  //   | CONTAINER c4Attributes
  //   | CONTAINER_DB c4Attributes
  //   | CONTAINER_QUEUE c4Attributes
  //   | CONTAINER_EXT c4Attributes
  //   | CONTAINER_EXT_DB c4Attributes
  //   | CONTAINER_EXT_QUEUE c4Attributes
  //   | COMPONENT c4Attributes
  //   | COMPONENT_DB c4Attributes
  //   | COMPONENT_QUEUE c4Attributes
  //   | COMPONENT_EXT c4Attributes
  //   | COMPONENT_EXT_DB c4Attributes
  //   | COMPONENT_EXT_QUEUE c4Attributes
  //   | REL c4Attributes
  //   | BIREL c4Attributes
  //   | REL_U c4Attributes
  //   | REL_D c4Attributes
  //   | REL_L c4Attributes
  //   | REL_R c4Attributes
  //   | REL_B c4Attributes
  //   | REL_INDEX c4Attributes
  //   | UPDATE_EL_STYLE c4Attributes
  //   | UPDATE_REL_STYLE c4Attributes
  //   | UPDATE_LAYOUT_CONFIG c4Attributes
  public static boolean c4ComponentStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, C_4_COMPONENT_STATEMENT, "<c 4 component statement>");
    result_ = c4ComponentStatement_0(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_1(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_2(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_3(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_4(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_5(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_6(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_7(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_8(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_9(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_10(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_11(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_12(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_13(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_14(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_15(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_16(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_17(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_18(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_19(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_20(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_21(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_22(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_23(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_24(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_25(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_26(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_27(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_28(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_29(builder_, level_ + 1);
    if (!result_) result_ = c4ComponentStatement_30(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // PERSON c4Attributes
  private static boolean c4ComponentStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PERSON);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PERSON_EXT c4Attributes
  private static boolean c4ComponentStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PERSON_EXT);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM c4Attributes
  private static boolean c4ComponentStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM_DB c4Attributes
  private static boolean c4ComponentStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM_DB);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM_QUEUE c4Attributes
  private static boolean c4ComponentStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM_QUEUE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM_EXT c4Attributes
  private static boolean c4ComponentStatement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM_EXT);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM_EXT_DB c4Attributes
  private static boolean c4ComponentStatement_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM_EXT_DB);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SYSTEM_EXT_QUEUE c4Attributes
  private static boolean c4ComponentStatement_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYSTEM_EXT_QUEUE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER c4Attributes
  private static boolean c4ComponentStatement_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_DB c4Attributes
  private static boolean c4ComponentStatement_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_9")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER_DB);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_QUEUE c4Attributes
  private static boolean c4ComponentStatement_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_10")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER_QUEUE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_EXT c4Attributes
  private static boolean c4ComponentStatement_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_11")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER_EXT);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_EXT_DB c4Attributes
  private static boolean c4ComponentStatement_12(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_12")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER_EXT_DB);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_EXT_QUEUE c4Attributes
  private static boolean c4ComponentStatement_13(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_13")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTAINER_EXT_QUEUE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMPONENT c4Attributes
  private static boolean c4ComponentStatement_14(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_14")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMPONENT);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMPONENT_DB c4Attributes
  private static boolean c4ComponentStatement_15(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_15")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMPONENT_DB);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMPONENT_QUEUE c4Attributes
  private static boolean c4ComponentStatement_16(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_16")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMPONENT_QUEUE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMPONENT_EXT c4Attributes
  private static boolean c4ComponentStatement_17(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_17")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMPONENT_EXT);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMPONENT_EXT_DB c4Attributes
  private static boolean c4ComponentStatement_18(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_18")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMPONENT_EXT_DB);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMPONENT_EXT_QUEUE c4Attributes
  private static boolean c4ComponentStatement_19(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_19")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMPONENT_EXT_QUEUE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL c4Attributes
  private static boolean c4ComponentStatement_20(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_20")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BIREL c4Attributes
  private static boolean c4ComponentStatement_21(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_21")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BIREL);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL_U c4Attributes
  private static boolean c4ComponentStatement_22(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_22")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL_U);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL_D c4Attributes
  private static boolean c4ComponentStatement_23(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_23")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL_D);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL_L c4Attributes
  private static boolean c4ComponentStatement_24(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_24")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL_L);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL_R c4Attributes
  private static boolean c4ComponentStatement_25(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_25")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL_R);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL_B c4Attributes
  private static boolean c4ComponentStatement_26(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_26")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL_B);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // REL_INDEX c4Attributes
  private static boolean c4ComponentStatement_27(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_27")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REL_INDEX);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // UPDATE_EL_STYLE c4Attributes
  private static boolean c4ComponentStatement_28(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_28")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, UPDATE_EL_STYLE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // UPDATE_REL_STYLE c4Attributes
  private static boolean c4ComponentStatement_29(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_29")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, UPDATE_REL_STYLE);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // UPDATE_LAYOUT_CONFIG c4Attributes
  private static boolean c4ComponentStatement_30(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4ComponentStatement_30")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, UPDATE_LAYOUT_CONFIG);
    result_ = result_ && c4Attributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // C4_CONTEXT
  //   | C4_CONTAINER
  //   | C4_COMPONENT
  //   | C4_DYNAMIC
  //   | C4_DEPLOYMENT
  public static boolean c4Header(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Header")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, C_4_HEADER, "<c 4 header>");
    result_ = consumeToken(builder_, C4_CONTEXT);
    if (!result_) result_ = consumeToken(builder_, C4_CONTAINER);
    if (!result_) result_ = consumeToken(builder_, C4_COMPONENT);
    if (!result_) result_ = consumeToken(builder_, C4_DYNAMIC);
    if (!result_) result_ = consumeToken(builder_, C4_DEPLOYMENT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // c4Statement [EOL] | EOL
  static boolean c4Line(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Line")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = c4Line_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // c4Statement [EOL]
  private static boolean c4Line_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Line_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = c4Statement(builder_, level_ + 1);
    result_ = result_ && c4Line_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean c4Line_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Line_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // c4Line [c4Lines]
  static boolean c4Lines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Lines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = c4Line(builder_, level_ + 1);
    result_ = result_ && c4Lines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [c4Lines]
  private static boolean c4Lines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Lines_1")) return false;
    c4Lines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // c4ComponentStatement
  //   | boundaryStatement
  //   | titleStatement
  //   | directionStatement
  //   | accStatement
  static boolean c4Statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "c4Statement")) return false;
    boolean result_;
    result_ = c4ComponentStatement(builder_, level_ + 1);
    if (!result_) result_ = boundaryStatement(builder_, level_ + 1);
    if (!result_) result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = directionStatement(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // CLICK_DATA | string
  static boolean callbackArg(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callbackArg")) return false;
    if (!nextTokenIs(builder_, "", CLICK_DATA, DOUBLE_QUOTE)) return false;
    boolean result_;
    result_ = consumeToken(builder_, CLICK_DATA);
    if (!result_) result_ = string(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_ROUND [callbackArg] (COMMA callbackArg)* CLOSE_ROUND
  public static boolean callbackArgs(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callbackArgs")) return false;
    if (!nextTokenIs(builder_, OPEN_ROUND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_ROUND);
    result_ = result_ && callbackArgs_1(builder_, level_ + 1);
    result_ = result_ && callbackArgs_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_ROUND);
    exit_section_(builder_, marker_, CALLBACK_ARGS, result_);
    return result_;
  }

  // [callbackArg]
  private static boolean callbackArgs_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callbackArgs_1")) return false;
    callbackArg(builder_, level_ + 1);
    return true;
  }

  // (COMMA callbackArg)*
  private static boolean callbackArgs_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callbackArgs_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!callbackArgs_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "callbackArgs_2", pos_)) break;
    }
    return true;
  }

  // COMMA callbackArg
  private static boolean callbackArgs_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callbackArgs_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && callbackArg(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // maybeEmptyString
  public static boolean cardinality(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cardinality")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = maybeEmptyString(builder_, level_ + 1);
    exit_section_(builder_, marker_, CARDINALITY, result_);
    return result_;
  }

  /* ********************************************************** */
  // CHECKOUT gitGraphBranchIdentifier
  public static boolean checkoutStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "checkoutStatement")) return false;
    if (!nextTokenIs(builder_, CHECKOUT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CHECKOUT);
    result_ = result_ && gitGraphBranchIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, CHECKOUT_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // commitIdAttribute parentCommitIdAttribute commitTagAttribute
  //   | commitIdAttribute commitTagAttribute parentCommitIdAttribute
  //   | commitTagAttribute commitIdAttribute parentCommitIdAttribute
  //   | commitIdAttribute commitTagAttribute
  //   | commitTagAttribute commitIdAttribute
  //   | commitIdAttribute parentCommitIdAttribute
  //   | commitIdAttribute
  static boolean cherryPickAttributes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes")) return false;
    if (!nextTokenIs(builder_, "", ID_KEYWORD, TAG)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = cherryPickAttributes_0(builder_, level_ + 1);
    if (!result_) result_ = cherryPickAttributes_1(builder_, level_ + 1);
    if (!result_) result_ = cherryPickAttributes_2(builder_, level_ + 1);
    if (!result_) result_ = cherryPickAttributes_3(builder_, level_ + 1);
    if (!result_) result_ = cherryPickAttributes_4(builder_, level_ + 1);
    if (!result_) result_ = cherryPickAttributes_5(builder_, level_ + 1);
    if (!result_) result_ = commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute parentCommitIdAttribute commitTagAttribute
  private static boolean cherryPickAttributes_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && parentCommitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute parentCommitIdAttribute
  private static boolean cherryPickAttributes_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && parentCommitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute parentCommitIdAttribute
  private static boolean cherryPickAttributes_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && parentCommitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute
  private static boolean cherryPickAttributes_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute
  private static boolean cherryPickAttributes_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute parentCommitIdAttribute
  private static boolean cherryPickAttributes_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickAttributes_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && parentCommitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CHERRY_PICK cherryPickAttributes
  public static boolean cherryPickStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cherryPickStatement")) return false;
    if (!nextTokenIs(builder_, CHERRY_PICK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CHERRY_PICK);
    result_ = result_ && cherryPickAttributes(builder_, level_ + 1);
    exit_section_(builder_, marker_, CHERRY_PICK_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL* classMembers? CLOSE_CURLY
  public static boolean classBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && classBlock_1(builder_, level_ + 1);
    result_ = result_ && classBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, CLASS_BLOCK, result_);
    return result_;
  }

  // EOL*
  private static boolean classBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classBlock_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "classBlock_1", pos_)) break;
    }
    return true;
  }

  // classMembers?
  private static boolean classBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classBlock_2")) return false;
    classMembers(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // classLines
  public static boolean classBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_BODY, "<class body>");
    result_ = classLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // CLASS_DEF (STYLE_TARGET | DEFAULT) styleOptions
  public static boolean classDefStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefStatement")) return false;
    if (!nextTokenIs(builder_, CLASS_DEF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS_DEF);
    result_ = result_ && classDefStatement_1(builder_, level_ + 1);
    result_ = result_ && styleOptions(builder_, level_ + 1);
    exit_section_(builder_, marker_, CLASS_DEF_STATEMENT, result_);
    return result_;
  }

  // STYLE_TARGET | DEFAULT
  private static boolean classDefStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefStatement_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STYLE_TARGET);
    if (!result_) result_ = consumeToken(builder_, DEFAULT);
    return result_;
  }

  /* ********************************************************** */
  // CLICK CLICK_DATA CALL CLICK_DATA callbackArgs [string]
  //   | CLICK CLICK_DATA HREF string [string] [LINK_TARGET]
  //   | CALLBACK CLICK_DATA string [string]
  //   | LINK CLICK_DATA string [string] [LINK_TARGET]
  public static boolean classDiagramClickStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_DIAGRAM_CLICK_STATEMENT, "<class diagram click statement>");
    result_ = classDiagramClickStatement_0(builder_, level_ + 1);
    if (!result_) result_ = classDiagramClickStatement_1(builder_, level_ + 1);
    if (!result_) result_ = classDiagramClickStatement_2(builder_, level_ + 1);
    if (!result_) result_ = classDiagramClickStatement_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // CLICK CLICK_DATA CALL CLICK_DATA callbackArgs [string]
  private static boolean classDiagramClickStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CLICK, CLICK_DATA, CALL, CLICK_DATA);
    result_ = result_ && callbackArgs(builder_, level_ + 1);
    result_ = result_ && classDiagramClickStatement_0_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [string]
  private static boolean classDiagramClickStatement_0_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_0_5")) return false;
    string(builder_, level_ + 1);
    return true;
  }

  // CLICK CLICK_DATA HREF string [string] [LINK_TARGET]
  private static boolean classDiagramClickStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CLICK, CLICK_DATA, HREF);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && classDiagramClickStatement_1_4(builder_, level_ + 1);
    result_ = result_ && classDiagramClickStatement_1_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [string]
  private static boolean classDiagramClickStatement_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_1_4")) return false;
    string(builder_, level_ + 1);
    return true;
  }

  // [LINK_TARGET]
  private static boolean classDiagramClickStatement_1_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_1_5")) return false;
    consumeToken(builder_, LINK_TARGET);
    return true;
  }

  // CALLBACK CLICK_DATA string [string]
  private static boolean classDiagramClickStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CALLBACK, CLICK_DATA);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && classDiagramClickStatement_2_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [string]
  private static boolean classDiagramClickStatement_2_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_2_3")) return false;
    string(builder_, level_ + 1);
    return true;
  }

  // LINK CLICK_DATA string [string] [LINK_TARGET]
  private static boolean classDiagramClickStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LINK, CLICK_DATA);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && classDiagramClickStatement_3_3(builder_, level_ + 1);
    result_ = result_ && classDiagramClickStatement_3_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [string]
  private static boolean classDiagramClickStatement_3_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_3_3")) return false;
    string(builder_, level_ + 1);
    return true;
  }

  // [LINK_TARGET]
  private static boolean classDiagramClickStatement_3_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramClickStatement_3_4")) return false;
    consumeToken(builder_, LINK_TARGET);
    return true;
  }

  /* ********************************************************** */
  // CLASS_DIAGRAM
  public static boolean classDiagramHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramHeader")) return false;
    if (!nextTokenIs(builder_, CLASS_DIAGRAM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS_DIAGRAM);
    exit_section_(builder_, marker_, CLASS_DIAGRAM_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // CLASS_ID+ | BACK_QUOTE quotedClassIdentifier BACK_QUOTE
  public static boolean classDiagramIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramIdentifier")) return false;
    if (!nextTokenIs(builder_, "<class diagram identifier>", BACK_QUOTE, CLASS_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_DIAGRAM_IDENTIFIER, "<class diagram identifier>");
    result_ = classDiagramIdentifier_0(builder_, level_ + 1);
    if (!result_) result_ = classDiagramIdentifier_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // CLASS_ID+
  private static boolean classDiagramIdentifier_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramIdentifier_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS_ID);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, CLASS_ID)) break;
      if (!empty_element_parsed_guard_(builder_, "classDiagramIdentifier_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BACK_QUOTE quotedClassIdentifier BACK_QUOTE
  private static boolean classDiagramIdentifier_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramIdentifier_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BACK_QUOTE);
    result_ = result_ && quotedClassIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, BACK_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // NOTE_FOR classDiagramIdentifier classDiagramNoteText | NOTE classDiagramNoteText
  public static boolean classDiagramNoteStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramNoteStatement")) return false;
    if (!nextTokenIs(builder_, "<class diagram note statement>", NOTE, NOTE_FOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_DIAGRAM_NOTE_STATEMENT, "<class diagram note statement>");
    result_ = classDiagramNoteStatement_0(builder_, level_ + 1);
    if (!result_) result_ = classDiagramNoteStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // NOTE_FOR classDiagramIdentifier classDiagramNoteText
  private static boolean classDiagramNoteStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramNoteStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NOTE_FOR);
    result_ = result_ && classDiagramIdentifier(builder_, level_ + 1);
    result_ = result_ && classDiagramNoteText(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NOTE classDiagramNoteText
  private static boolean classDiagramNoteStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramNoteStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NOTE);
    result_ = result_ && classDiagramNoteText(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // string
  public static boolean classDiagramNoteText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramNoteText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = string(builder_, level_ + 1);
    exit_section_(builder_, marker_, CLASS_DIAGRAM_NOTE_TEXT, result_);
    return result_;
  }

  /* ********************************************************** */
  // directionStatement
  //   | classStatement
  //   | relationStatement
  //   | namespaceStatement
  //   | memberStatement
  //   | annotationStatement
  //   | classDiagramNoteStatement
  //   | classDiagramClickStatement
  //   | accStatement
  //   | styleStatement
  static boolean classDiagramStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDiagramStatement")) return false;
    boolean result_;
    result_ = directionStatement(builder_, level_ + 1);
    if (!result_) result_ = classStatement(builder_, level_ + 1);
    if (!result_) result_ = relationStatement(builder_, level_ + 1);
    if (!result_) result_ = namespaceStatement(builder_, level_ + 1);
    if (!result_) result_ = memberStatement(builder_, level_ + 1);
    if (!result_) result_ = annotationStatement(builder_, level_ + 1);
    if (!result_) result_ = classDiagramNoteStatement(builder_, level_ + 1);
    if (!result_) result_ = classDiagramClickStatement(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    if (!result_) result_ = styleStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // CLASS classDiagramIdentifier [generic] [classLabel] [STYLE_SEPARATOR ID]
  public static boolean classHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classHeader")) return false;
    if (!nextTokenIs(builder_, CLASS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS);
    result_ = result_ && classDiagramIdentifier(builder_, level_ + 1);
    result_ = result_ && classHeader_2(builder_, level_ + 1);
    result_ = result_ && classHeader_3(builder_, level_ + 1);
    result_ = result_ && classHeader_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, CLASS_HEADER, result_);
    return result_;
  }

  // [generic]
  private static boolean classHeader_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classHeader_2")) return false;
    generic(builder_, level_ + 1);
    return true;
  }

  // [classLabel]
  private static boolean classHeader_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classHeader_3")) return false;
    classLabel(builder_, level_ + 1);
    return true;
  }

  // [STYLE_SEPARATOR ID]
  private static boolean classHeader_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classHeader_4")) return false;
    parseTokens(builder_, 0, STYLE_SEPARATOR, ID);
    return true;
  }

  /* ********************************************************** */
  // OPEN_SQUARE maybeEmptyString CLOSE_SQUARE
  public static boolean classLabel(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classLabel")) return false;
    if (!nextTokenIs(builder_, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_SQUARE);
    result_ = result_ && maybeEmptyString(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, CLASS_LABEL, result_);
    return result_;
  }

  /* ********************************************************** */
  // classDiagramStatement [EOL] | EOL
  static boolean classLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // classDiagramStatement [EOL]
  private static boolean classLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classDiagramStatement(builder_, level_ + 1);
    result_ = result_ && classLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean classLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // classLine [classLines]
  static boolean classLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classLine(builder_, level_ + 1);
    result_ = result_ && classLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [classLines]
  private static boolean classLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classLines_1")) return false;
    classLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // attribute | annotation | directive
  static boolean classMemberStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classMemberStatement")) return false;
    boolean result_;
    result_ = attribute(builder_, level_ + 1);
    if (!result_) result_ = annotation(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // memberLines
  static boolean classMembers(PsiBuilder builder_, int level_) {
    return memberLines(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // classHeader [classBlock]
  public static boolean classStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classStatement")) return false;
    if (!nextTokenIs(builder_, CLASS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classHeader(builder_, level_ + 1);
    result_ = result_ && classStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, CLASS_STATEMENT, result_);
    return result_;
  }

  // [classBlock]
  private static boolean classStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classStatement_1")) return false;
    classBlock(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // COLUMNS (AUTO | NUM)
  public static boolean columnsStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "columnsStatement")) return false;
    if (!nextTokenIs(builder_, COLUMNS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLUMNS);
    result_ = result_ && columnsStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, COLUMNS_STATEMENT, result_);
    return result_;
  }

  // AUTO | NUM
  private static boolean columnsStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "columnsStatement_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, AUTO);
    if (!result_) result_ = consumeToken(builder_, NUM);
    return result_;
  }

  /* ********************************************************** */
  // commitIdAttribute commitTypeAttribute commitTagAttribute
  //   | commitIdAttribute commitTagAttribute commitTypeAttribute
  //   | commitTypeAttribute commitIdAttribute commitTagAttribute
  //   | commitTypeAttribute commitTagAttribute commitIdAttribute
  //   | commitTagAttribute commitTypeAttribute commitIdAttribute
  //   | commitTagAttribute commitIdAttribute commitTypeAttribute
  //   | commitTagAttribute commitTypeAttribute
  //   | commitTypeAttribute commitTagAttribute
  //   | commitIdAttribute commitTagAttribute
  //   | commitTagAttribute commitIdAttribute
  //   | commitIdAttribute commitTypeAttribute
  //   | commitTypeAttribute commitIdAttribute
  //   | commitTagAttribute
  //   | commitTypeAttribute
  //   | commitIdAttribute
  static boolean commitAttributes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitAttributes_0(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_1(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_2(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_3(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_4(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_5(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_6(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_7(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_8(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_9(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_10(builder_, level_ + 1);
    if (!result_) result_ = commitAttributes_11(builder_, level_ + 1);
    if (!result_) result_ = commitTagAttribute(builder_, level_ + 1);
    if (!result_) result_ = commitTypeAttribute(builder_, level_ + 1);
    if (!result_) result_ = commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute commitTagAttribute
  private static boolean commitAttributes_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute commitTypeAttribute
  private static boolean commitAttributes_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute commitTagAttribute
  private static boolean commitAttributes_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute commitIdAttribute
  private static boolean commitAttributes_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute commitIdAttribute
  private static boolean commitAttributes_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute commitTypeAttribute
  private static boolean commitAttributes_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute
  private static boolean commitAttributes_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute
  private static boolean commitAttributes_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute
  private static boolean commitAttributes_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute
  private static boolean commitAttributes_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_9")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute
  private static boolean commitAttributes_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_10")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute
  private static boolean commitAttributes_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributes_11")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // commitMsgAttribute commitIdAttribute commitTypeAttribute commitTagAttribute
  //   | commitMsgAttribute commitIdAttribute commitTagAttribute commitTypeAttribute
  //   | commitMsgAttribute commitTypeAttribute commitIdAttribute commitTagAttribute
  //   | commitMsgAttribute commitTypeAttribute commitTagAttribute commitIdAttribute
  //   | commitMsgAttribute commitTagAttribute commitIdAttribute commitTypeAttribute
  //   | commitMsgAttribute commitTagAttribute commitTypeAttribute commitIdAttribute
  //   | commitIdAttribute commitMsgAttribute commitTypeAttribute commitTagAttribute
  //   | commitIdAttribute commitMsgAttribute commitTagAttribute commitTypeAttribute
  //   | commitIdAttribute commitTypeAttribute commitMsgAttribute commitTagAttribute
  //   | commitIdAttribute commitTypeAttribute commitTagAttribute commitMsgAttribute
  //   | commitIdAttribute commitTagAttribute commitMsgAttribute commitTypeAttribute
  //   | commitIdAttribute commitTagAttribute commitTypeAttribute commitMsgAttribute
  //   | commitTagAttribute commitIdAttribute commitTypeAttribute commitMsgAttribute
  //   | commitTagAttribute commitIdAttribute commitMsgAttribute commitTypeAttribute
  //   | commitTagAttribute commitTypeAttribute commitIdAttribute commitMsgAttribute
  //   | commitTagAttribute commitTypeAttribute commitMsgAttribute commitIdAttribute
  //   | commitTagAttribute commitMsgAttribute commitIdAttribute commitTypeAttribute
  //   | commitTagAttribute commitMsgAttribute commitTypeAttribute commitIdAttribute
  //   | commitTypeAttribute commitIdAttribute commitMsgAttribute commitTagAttribute
  //   | commitTypeAttribute commitIdAttribute commitTagAttribute commitMsgAttribute
  //   | commitTypeAttribute commitTagAttribute commitMsgAttribute commitIdAttribute
  //   | commitTypeAttribute commitTagAttribute commitIdAttribute commitMsgAttribute
  //   | commitTypeAttribute commitMsgAttribute commitIdAttribute commitTagAttribute
  //   | commitTypeAttribute commitMsgAttribute commitTagAttribute commitIdAttribute
  //   | commitMsgAttribute commitTypeAttribute commitTagAttribute
  //   | commitMsgAttribute commitTagAttribute commitTypeAttribute
  //   | commitTypeAttribute commitMsgAttribute commitTagAttribute
  //   | commitTypeAttribute commitTagAttribute commitMsgAttribute
  //   | commitTagAttribute commitTypeAttribute commitMsgAttribute
  //   | commitTagAttribute commitMsgAttribute commitTypeAttribute
  //   | commitMsgAttribute commitTypeAttribute commitIdAttribute
  //   | commitMsgAttribute commitIdAttribute commitTypeAttribute
  //   | commitTypeAttribute commitMsgAttribute commitIdAttribute
  //   | commitTypeAttribute commitIdAttribute commitMsgAttribute
  //   | commitIdAttribute commitTypeAttribute commitMsgAttribute
  //   | commitIdAttribute commitMsgAttribute commitTypeAttribute
  //   | commitMsgAttribute commitTagAttribute commitIdAttribute
  //   | commitMsgAttribute commitIdAttribute commitTagAttribute
  //   | commitTagAttribute commitMsgAttribute commitIdAttribute
  //   | commitTagAttribute commitIdAttribute commitMsgAttribute
  //   | commitIdAttribute commitTagAttribute commitMsgAttribute
  //   | commitIdAttribute commitMsgAttribute commitTagAttribute
  //   | commitIdAttribute commitTypeAttribute commitTagAttribute
  //   | commitIdAttribute commitTagAttribute commitTypeAttribute
  //   | commitTypeAttribute commitIdAttribute commitTagAttribute
  //   | commitTypeAttribute commitTagAttribute commitIdAttribute
  //   | commitTagAttribute commitTypeAttribute commitIdAttribute
  //   | commitTagAttribute commitIdAttribute commitTypeAttribute
  //   | commitTagAttribute commitMsgAttribute
  //   | commitMsgAttribute commitTagAttribute
  //   | commitTypeAttribute commitMsgAttribute
  //   | commitMsgAttribute commitTypeAttribute
  //   | commitIdAttribute commitMsgAttribute
  //   | commitMsgAttribute commitIdAttribute
  //   | commitTagAttribute commitTypeAttribute
  //   | commitTypeAttribute commitTagAttribute
  //   | commitIdAttribute commitTagAttribute
  //   | commitTagAttribute commitIdAttribute
  //   | commitIdAttribute commitTypeAttribute
  //   | commitTypeAttribute commitIdAttribute
  //   | commitMsgAttribute
  //   | commitTagAttribute
  //   | commitTypeAttribute
  //   | commitIdAttribute
  static boolean commitAttributesWithMsg(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitAttributesWithMsg_0(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_1(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_2(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_3(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_4(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_5(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_6(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_7(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_8(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_9(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_10(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_11(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_12(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_13(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_14(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_15(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_16(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_17(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_18(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_19(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_20(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_21(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_22(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_23(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_24(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_25(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_26(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_27(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_28(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_29(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_30(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_31(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_32(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_33(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_34(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_35(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_36(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_37(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_38(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_39(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_40(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_41(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_42(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_43(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_44(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_45(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_46(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_47(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_48(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_49(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_50(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_51(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_52(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_53(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_54(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_55(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_56(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_57(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_58(builder_, level_ + 1);
    if (!result_) result_ = commitAttributesWithMsg_59(builder_, level_ + 1);
    if (!result_) result_ = commitMsgAttribute(builder_, level_ + 1);
    if (!result_) result_ = commitTagAttribute(builder_, level_ + 1);
    if (!result_) result_ = commitTypeAttribute(builder_, level_ + 1);
    if (!result_) result_ = commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitIdAttribute commitTypeAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitIdAttribute commitTagAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTypeAttribute commitIdAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTypeAttribute commitTagAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTagAttribute commitIdAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTagAttribute commitTypeAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitMsgAttribute commitTypeAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitMsgAttribute commitTagAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute commitMsgAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute commitTagAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_9")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute commitMsgAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_10")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute commitTypeAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_11")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute commitTypeAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_12(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_12")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute commitMsgAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_13(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_13")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute commitIdAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_14(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_14")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute commitMsgAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_15(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_15")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitMsgAttribute commitIdAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_16(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_16")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitMsgAttribute commitTypeAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_17(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_17")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute commitMsgAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_18(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_18")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute commitTagAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_19(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_19")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute commitMsgAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_20(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_20")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute commitIdAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_21(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_21")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitMsgAttribute commitIdAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_22(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_22")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitMsgAttribute commitTagAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_23(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_23")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTypeAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_24(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_24")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTagAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_25(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_25")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitMsgAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_26(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_26")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_27(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_27")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_28(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_28")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitMsgAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_29(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_29")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTypeAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_30(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_30")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitIdAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_31(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_31")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitMsgAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_32(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_32")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_33(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_33")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_34(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_34")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitMsgAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_35(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_35")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTagAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_36(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_36")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitIdAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_37(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_37")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitMsgAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_38(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_38")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_39(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_39")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_40(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_40")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitMsgAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_41(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_41")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_42(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_42")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_43(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_43")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_44(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_44")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_45(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_45")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_46(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_46")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_47(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_47")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_48(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_48")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_49(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_49")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_50(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_50")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_51(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_51")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitMsgAttribute
  private static boolean commitAttributesWithMsg_52(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_52")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitMsgAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitMsgAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_53(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_53")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitMsgAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_54(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_54")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_55(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_55")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTagAttribute
  private static boolean commitAttributesWithMsg_56(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_56")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTagAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTagAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_57(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_57")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTagAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitIdAttribute commitTypeAttribute
  private static boolean commitAttributesWithMsg_58(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_58")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitIdAttribute(builder_, level_ + 1);
    result_ = result_ && commitTypeAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // commitTypeAttribute commitIdAttribute
  private static boolean commitAttributesWithMsg_59(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitAttributesWithMsg_59")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitTypeAttribute(builder_, level_ + 1);
    result_ = result_ && commitIdAttribute(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ID_KEYWORD COLON DOUBLE_QUOTE commitIdValue DOUBLE_QUOTE
  public static boolean commitIdAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitIdAttribute")) return false;
    if (!nextTokenIs(builder_, ID_KEYWORD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ID_KEYWORD, COLON, DOUBLE_QUOTE);
    result_ = result_ && commitIdValue(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, COMMIT_ID_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // STRING_VALUE
  public static boolean commitIdValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitIdValue")) return false;
    if (!nextTokenIs(builder_, STRING_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING_VALUE);
    exit_section_(builder_, marker_, COMMIT_ID_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // MSG COLON maybeEmptyString
  public static boolean commitMsgAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitMsgAttribute")) return false;
    if (!nextTokenIs(builder_, MSG)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, MSG, COLON);
    result_ = result_ && maybeEmptyString(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMMIT_MSG_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // COMMIT commitAttributesWithMsg
  //   | COMMIT [commit_arg]
  public static boolean commitStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitStatement")) return false;
    if (!nextTokenIs(builder_, COMMIT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = commitStatement_0(builder_, level_ + 1);
    if (!result_) result_ = commitStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMMIT_STATEMENT, result_);
    return result_;
  }

  // COMMIT commitAttributesWithMsg
  private static boolean commitStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMIT);
    result_ = result_ && commitAttributesWithMsg(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMIT [commit_arg]
  private static boolean commitStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMIT);
    result_ = result_ && commitStatement_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [commit_arg]
  private static boolean commitStatement_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitStatement_1_1")) return false;
    commit_arg(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // TAG COLON maybeEmptyString
  public static boolean commitTagAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitTagAttribute")) return false;
    if (!nextTokenIs(builder_, TAG)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, TAG, COLON);
    result_ = result_ && maybeEmptyString(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMMIT_TAG_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // NORMAL | REVERSE | HIGHLIGHT
  static boolean commitType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitType")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NORMAL);
    if (!result_) result_ = consumeToken(builder_, REVERSE);
    if (!result_) result_ = consumeToken(builder_, HIGHLIGHT);
    return result_;
  }

  /* ********************************************************** */
  // TYPE COLON commitType
  public static boolean commitTypeAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commitTypeAttribute")) return false;
    if (!nextTokenIs(builder_, TYPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, TYPE, COLON);
    result_ = result_ && commitType(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMMIT_TYPE_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // string
  public static boolean commit_arg(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commit_arg")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = string(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMMIT_ARG, result_);
    return result_;
  }

  /* ********************************************************** */
  // ACC_DESCR_MULTILINE_VALUE+ [EOL] | EOL
  public static boolean complexAccDescrMultilineValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexAccDescrMultilineValue")) return false;
    if (!nextTokenIs(builder_, "<complex acc descr multiline value>", ACC_DESCR_MULTILINE_VALUE, EOL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPLEX_ACC_DESCR_MULTILINE_VALUE, "<complex acc descr multiline value>");
    result_ = complexAccDescrMultilineValue_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ACC_DESCR_MULTILINE_VALUE+ [EOL]
  private static boolean complexAccDescrMultilineValue_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexAccDescrMultilineValue_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexAccDescrMultilineValue_0_0(builder_, level_ + 1);
    result_ = result_ && complexAccDescrMultilineValue_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ACC_DESCR_MULTILINE_VALUE+
  private static boolean complexAccDescrMultilineValue_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexAccDescrMultilineValue_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ACC_DESCR_MULTILINE_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ACC_DESCR_MULTILINE_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "complexAccDescrMultilineValue_0_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean complexAccDescrMultilineValue_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexAccDescrMultilineValue_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // ACC_DESCR_VALUE+
  public static boolean complexAccDescrValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexAccDescrValue")) return false;
    if (!nextTokenIs(builder_, ACC_DESCR_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ACC_DESCR_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ACC_DESCR_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "complexAccDescrValue", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_ACC_DESCR_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // ACC_TITLE_VALUE+
  public static boolean complexAccTitleValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexAccTitleValue")) return false;
    if (!nextTokenIs(builder_, ACC_TITLE_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ACC_TITLE_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ACC_TITLE_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "complexAccTitleValue", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_ACC_TITLE_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONTROL_ID+
  public static boolean complexControlId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexControlId")) return false;
    if (!nextTokenIs(builder_, CONTROL_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTROL_ID);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, CONTROL_ID)) break;
      if (!empty_element_parsed_guard_(builder_, "complexControlId", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_CONTROL_ID, result_);
    return result_;
  }

  /* ********************************************************** */
  // ID+
  public static boolean complexIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexIdentifier")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ID);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ID)) break;
      if (!empty_element_parsed_guard_(builder_, "complexIdentifier", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_IDENTIFIER, result_);
    return result_;
  }

  /* ********************************************************** */
  // LABEL+
  public static boolean complexLabel(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLabel")) return false;
    if (!nextTokenIs(builder_, LABEL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LABEL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LABEL)) break;
      if (!empty_element_parsed_guard_(builder_, "complexLabel", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_LABEL, result_);
    return result_;
  }

  /* ********************************************************** */
  // (maybeEmptyQuotedLinkText | maybeEmptyMdText) LINK_TEXT+
  //   | (quotedLinkText | mdText) LINK_TEXT*
  //   | LINK_TEXT+
  public static boolean complexLinkText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText")) return false;
    if (!nextTokenIs(builder_, "<complex link text>", DOUBLE_QUOTE, LINK_TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPLEX_LINK_TEXT, "<complex link text>");
    result_ = complexLinkText_0(builder_, level_ + 1);
    if (!result_) result_ = complexLinkText_1(builder_, level_ + 1);
    if (!result_) result_ = complexLinkText_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (maybeEmptyQuotedLinkText | maybeEmptyMdText) LINK_TEXT+
  private static boolean complexLinkText_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexLinkText_0_0(builder_, level_ + 1);
    result_ = result_ && complexLinkText_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // maybeEmptyQuotedLinkText | maybeEmptyMdText
  private static boolean complexLinkText_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_0_0")) return false;
    boolean result_;
    result_ = maybeEmptyQuotedLinkText(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyMdText(builder_, level_ + 1);
    return result_;
  }

  // LINK_TEXT+
  private static boolean complexLinkText_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINK_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LINK_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "complexLinkText_0_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (quotedLinkText | mdText) LINK_TEXT*
  private static boolean complexLinkText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexLinkText_1_0(builder_, level_ + 1);
    result_ = result_ && complexLinkText_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // quotedLinkText | mdText
  private static boolean complexLinkText_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_1_0")) return false;
    boolean result_;
    result_ = quotedLinkText(builder_, level_ + 1);
    if (!result_) result_ = mdText(builder_, level_ + 1);
    return result_;
  }

  // LINK_TEXT*
  private static boolean complexLinkText_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LINK_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "complexLinkText_1_1", pos_)) break;
    }
    return true;
  }

  // LINK_TEXT+
  private static boolean complexLinkText_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexLinkText_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINK_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LINK_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "complexLinkText_2", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // MESSAGE+
  public static boolean complexMessage(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexMessage")) return false;
    if (!nextTokenIs(builder_, MESSAGE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MESSAGE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, MESSAGE)) break;
      if (!empty_element_parsed_guard_(builder_, "complexMessage", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_MESSAGE, result_);
    return result_;
  }

  /* ********************************************************** */
  // TASK_DATA*
  public static boolean complexNamedData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexNamedData")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPLEX_NAMED_DATA, "<complex named data>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TASK_DATA)) break;
      if (!empty_element_parsed_guard_(builder_, "complexNamedData", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // noteLines
  public static boolean complexNoteContent(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexNoteContent")) return false;
    if (!nextTokenIs(builder_, "<complex note content>", EOL, NOTE_CONTENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPLEX_NOTE_CONTENT, "<complex note content>");
    result_ = noteLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SANKEY_TEXT+
  public static boolean complexSankeyText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexSankeyText")) return false;
    if (!nextTokenIs(builder_, SANKEY_TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SANKEY_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, SANKEY_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "complexSankeyText", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_SANKEY_TEXT, result_);
    return result_;
  }

  /* ********************************************************** */
  // SECTION_TITLE+
  public static boolean complexSectionTitle(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexSectionTitle")) return false;
    if (!nextTokenIs(builder_, SECTION_TITLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SECTION_TITLE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, SECTION_TITLE)) break;
      if (!empty_element_parsed_guard_(builder_, "complexSectionTitle", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_SECTION_TITLE, result_);
    return result_;
  }

  /* ********************************************************** */
  // TASK_DATA*
  public static boolean complexTaskData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexTaskData")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPLEX_TASK_DATA, "<complex task data>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TASK_DATA)) break;
      if (!empty_element_parsed_guard_(builder_, "complexTaskData", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // TASK_NAME+
  public static boolean complexTaskName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexTaskName")) return false;
    if (!nextTokenIs(builder_, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TASK_NAME);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TASK_NAME)) break;
      if (!empty_element_parsed_guard_(builder_, "complexTaskName", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_TASK_NAME, result_);
    return result_;
  }

  /* ********************************************************** */
  // TITLE_VALUE+
  public static boolean complexTitleValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "complexTitleValue")) return false;
    if (!nextTokenIs(builder_, TITLE_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TITLE_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TITLE_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "complexTitleValue", pos_)) break;
    }
    exit_section_(builder_, marker_, COMPLEX_TITLE_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // stateDeclarationHeader stateBlock
  public static boolean compositeStateDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "compositeStateDeclaration")) return false;
    if (!nextTokenIs(builder_, STATE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateDeclarationHeader(builder_, level_ + 1);
    result_ = result_ && stateBlock(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMPOSITE_STATE_DECLARATION, result_);
    return result_;
  }

  /* ********************************************************** */
  // CRITICAL [complexControlId]
  public static boolean criticalHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalHeader")) return false;
    if (!nextTokenIs(builder_, CRITICAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CRITICAL);
    result_ = result_ && criticalHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, CRITICAL_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean criticalHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // criticalHeader EOL+ [sequenceBody] [EOL* optionSections] END
  public static boolean criticalStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalStatement")) return false;
    if (!nextTokenIs(builder_, CRITICAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = criticalHeader(builder_, level_ + 1);
    result_ = result_ && criticalStatement_1(builder_, level_ + 1);
    result_ = result_ && criticalStatement_2(builder_, level_ + 1);
    result_ = result_ && criticalStatement_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, CRITICAL_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean criticalStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "criticalStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean criticalStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  // [EOL* optionSections]
  private static boolean criticalStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalStatement_3")) return false;
    criticalStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // EOL* optionSections
  private static boolean criticalStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = criticalStatement_3_0_0(builder_, level_ + 1);
    result_ = result_ && optionSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean criticalStatement_3_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "criticalStatement_3_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "criticalStatement_3_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // CLASS CLASS_ENTITY_IDS STYLE_CLASS
  public static boolean cssClassStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cssClassStatement")) return false;
    if (!nextTokenIs(builder_, CLASS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CLASS, CLASS_ENTITY_IDS, STYLE_CLASS);
    exit_section_(builder_, marker_, CSS_CLASS_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // DEACTIVATE complexIdentifier
  public static boolean deactivateStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "deactivateStatement")) return false;
    if (!nextTokenIs(builder_, DEACTIVATE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DEACTIVATE);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, DEACTIVATE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // string
  public static boolean description(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "description")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = string(builder_, level_ + 1);
    exit_section_(builder_, marker_, DESCRIPTION, result_);
    return result_;
  }

  /* ********************************************************** */
  // DIRECTION DIR
  public static boolean directionStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directionStatement")) return false;
    if (!nextTokenIs(builder_, DIRECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DIRECTION, DIR);
    exit_section_(builder_, marker_, DIRECTION_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // [COMMA] ARROW_DIR (COMMA ARROW_DIR)*
  public static boolean directions(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directions")) return false;
    if (!nextTokenIs(builder_, "<directions>", ARROW_DIR, COMMA)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DIRECTIONS, "<directions>");
    result_ = directions_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW_DIR);
    result_ = result_ && directions_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [COMMA]
  private static boolean directions_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directions_0")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // (COMMA ARROW_DIR)*
  private static boolean directions_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directions_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!directions_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "directions_2", pos_)) break;
    }
    return true;
  }

  // COMMA ARROW_DIR
  private static boolean directions_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directions_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, ARROW_DIR);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_DIRECTIVE directiveValue? CLOSE_DIRECTIVE
  public static boolean directive(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directive")) return false;
    if (!nextTokenIs(builder_, OPEN_DIRECTIVE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_DIRECTIVE);
    result_ = result_ && directive_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_DIRECTIVE);
    exit_section_(builder_, marker_, DIRECTIVE, result_);
    return result_;
  }

  // directiveValue?
  private static boolean directive_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "directive_1")) return false;
    directiveParser(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // DIVIDER
  public static boolean dividerStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dividerStatement")) return false;
    if (!nextTokenIs(builder_, DIVIDER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DIVIDER);
    exit_section_(builder_, marker_, DIVIDER_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL+ elementBlockLines? CLOSE_CURLY
  public static boolean elementBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && elementBlock_1(builder_, level_ + 1);
    result_ = result_ && elementBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, ELEMENT_BLOCK, result_);
    return result_;
  }

  // EOL+
  private static boolean elementBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlock_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "elementBlock_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // elementBlockLines?
  private static boolean elementBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlock_2")) return false;
    elementBlockLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // elementBlockStatement [EOL] | EOL
  static boolean elementBlockLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlockLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elementBlockLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // elementBlockStatement [EOL]
  private static boolean elementBlockLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlockLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elementBlockStatement(builder_, level_ + 1);
    result_ = result_ && elementBlockLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean elementBlockLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlockLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // elementBlockLine [elementBlockLines]
  static boolean elementBlockLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlockLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elementBlockLine(builder_, level_ + 1);
    result_ = result_ && elementBlockLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [elementBlockLines]
  private static boolean elementBlockLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlockLines_1")) return false;
    elementBlockLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // elementTypeAttribute
  //   | elementDocRefAttribute
  static boolean elementBlockStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementBlockStatement")) return false;
    if (!nextTokenIs(builder_, "", DOCREF, TYPE)) return false;
    boolean result_;
    result_ = elementTypeAttribute(builder_, level_ + 1);
    if (!result_) result_ = elementDocRefAttribute(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // elementHeader elementBlock
  public static boolean elementDef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementDef")) return false;
    if (!nextTokenIs(builder_, ELEMENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elementHeader(builder_, level_ + 1);
    result_ = result_ && elementBlock(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELEMENT_DEF, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOCREF COLON requirementValue
  public static boolean elementDocRefAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementDocRefAttribute")) return false;
    if (!nextTokenIs(builder_, DOCREF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOCREF, COLON);
    result_ = result_ && requirementValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELEMENT_DOC_REF_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // ELEMENT identifier
  public static boolean elementHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementHeader")) return false;
    if (!nextTokenIs(builder_, ELEMENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELEMENT);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELEMENT_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // TYPE COLON requirementValue
  public static boolean elementTypeAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elementTypeAttribute")) return false;
    if (!nextTokenIs(builder_, TYPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, TYPE, COLON);
    result_ = result_ && requirementValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELEMENT_TYPE_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // ELSE [complexControlId]
  public static boolean elseHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseHeader")) return false;
    if (!nextTokenIs(builder_, ELSE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSE);
    result_ = result_ && elseHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELSE_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean elseHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // elseHeader EOL+ [sequenceBody]
  static boolean elseSection(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSection")) return false;
    if (!nextTokenIs(builder_, ELSE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elseHeader(builder_, level_ + 1);
    result_ = result_ && elseSection_1(builder_, level_ + 1);
    result_ = result_ && elseSection_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL+
  private static boolean elseSection_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSection_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "elseSection_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean elseSection_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSection_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // elseSection [EOL* elseSections]
  static boolean elseSections(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSections")) return false;
    if (!nextTokenIs(builder_, ELSE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elseSection(builder_, level_ + 1);
    result_ = result_ && elseSections_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL* elseSections]
  private static boolean elseSections_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSections_1")) return false;
    elseSections_1_0(builder_, level_ + 1);
    return true;
  }

  // EOL* elseSections
  private static boolean elseSections_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSections_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elseSections_1_0_0(builder_, level_ + 1);
    result_ = result_ && elseSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean elseSections_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elseSections_1_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "elseSections_1_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // erIdentifier [erIdentifierAlias] erEntityBlock
  public static boolean entityDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "entityDeclaration")) return false;
    if (!nextTokenIs(builder_, "<entity declaration>", DOUBLE_QUOTE, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ENTITY_DECLARATION, "<entity declaration>");
    result_ = erIdentifier(builder_, level_ + 1);
    result_ = result_ && entityDeclaration_1(builder_, level_ + 1);
    result_ = result_ && erEntityBlock(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [erIdentifierAlias]
  private static boolean entityDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "entityDeclaration_1")) return false;
    erIdentifierAlias(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (attrTypeWithGeneric | attrType) attrName [attrKeys] [maybeEmptyString]
  public static boolean erAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttribute")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ER_ATTRIBUTE, "<er attribute>");
    result_ = erAttribute_0(builder_, level_ + 1);
    result_ = result_ && attrName(builder_, level_ + 1);
    result_ = result_ && erAttribute_2(builder_, level_ + 1);
    result_ = result_ && erAttribute_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // attrTypeWithGeneric | attrType
  private static boolean erAttribute_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttribute_0")) return false;
    boolean result_;
    result_ = attrTypeWithGeneric(builder_, level_ + 1);
    if (!result_) result_ = attrType(builder_, level_ + 1);
    return result_;
  }

  // [attrKeys]
  private static boolean erAttribute_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttribute_2")) return false;
    attrKeys(builder_, level_ + 1);
    return true;
  }

  // [maybeEmptyString]
  private static boolean erAttribute_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttribute_3")) return false;
    maybeEmptyString(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // erAttribute [EOL] | EOL
  static boolean erAttributeLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttributeLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erAttributeLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // erAttribute [EOL]
  private static boolean erAttributeLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttributeLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erAttribute(builder_, level_ + 1);
    result_ = result_ && erAttributeLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean erAttributeLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttributeLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // erAttributesLines
  static boolean erAttributes(PsiBuilder builder_, int level_) {
    return erAttributesLines(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // erAttributeLine [erAttributesLines]
  static boolean erAttributesLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttributesLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erAttributeLine(builder_, level_ + 1);
    result_ = result_ && erAttributesLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [erAttributesLines]
  private static boolean erAttributesLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erAttributesLines_1")) return false;
    erAttributesLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // erLines
  public static boolean erBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ER_BODY, "<er body>");
    result_ = erLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ZERO_OR_ONE | ONE_OR_MORE | ZERO_OR_MORE | ONLY_ONE | MD_PARENT
  static boolean erCardinality(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erCardinality")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ZERO_OR_ONE);
    if (!result_) result_ = consumeToken(builder_, ONE_OR_MORE);
    if (!result_) result_ = consumeToken(builder_, ZERO_OR_MORE);
    if (!result_) result_ = consumeToken(builder_, ONLY_ONE);
    if (!result_) result_ = consumeToken(builder_, MD_PARENT);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL* erAttributes? CLOSE_CURLY
  public static boolean erEntityBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erEntityBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && erEntityBlock_1(builder_, level_ + 1);
    result_ = result_ && erEntityBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, ER_ENTITY_BLOCK, result_);
    return result_;
  }

  // EOL*
  private static boolean erEntityBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erEntityBlock_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "erEntityBlock_1", pos_)) break;
    }
    return true;
  }

  // erAttributes?
  private static boolean erEntityBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erEntityBlock_2")) return false;
    erAttributes(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ENTITY_RELATIONSHIP
  public static boolean erHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erHeader")) return false;
    if (!nextTokenIs(builder_, ENTITY_RELATIONSHIP)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ENTITY_RELATIONSHIP);
    exit_section_(builder_, marker_, ER_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // ID | string
  public static boolean erIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erIdentifier")) return false;
    if (!nextTokenIs(builder_, "<er identifier>", DOUBLE_QUOTE, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ER_IDENTIFIER, "<er identifier>");
    result_ = consumeToken(builder_, ID);
    if (!result_) result_ = string(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_SQUARE (ID | string) CLOSE_SQUARE
  public static boolean erIdentifierAlias(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erIdentifierAlias")) return false;
    if (!nextTokenIs(builder_, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_SQUARE);
    result_ = result_ && erIdentifierAlias_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, ER_IDENTIFIER_ALIAS, result_);
    return result_;
  }

  // ID | string
  private static boolean erIdentifierAlias_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erIdentifierAlias_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ID);
    if (!result_) result_ = string(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // erStatement [EOL] | EOL
  static boolean erLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // erStatement [EOL]
  private static boolean erLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erStatement(builder_, level_ + 1);
    result_ = result_ && erLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean erLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // erLine [erLines]
  static boolean erLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erLine(builder_, level_ + 1);
    result_ = result_ && erLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [erLines]
  private static boolean erLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erLines_1")) return false;
    erLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // erIdentifier relationship erIdentifier COLON (complexLabel | maybeEmptyString)
  public static boolean erRelationStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erRelationStatement")) return false;
    if (!nextTokenIs(builder_, "<er relation statement>", DOUBLE_QUOTE, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ER_RELATION_STATEMENT, "<er relation statement>");
    result_ = erIdentifier(builder_, level_ + 1);
    result_ = result_ && relationship(builder_, level_ + 1);
    result_ = result_ && erIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && erRelationStatement_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // complexLabel | maybeEmptyString
  private static boolean erRelationStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erRelationStatement_4")) return false;
    boolean result_;
    result_ = complexLabel(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyString(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // erRelationStatement
  //   | entityDeclaration
  //   | erIdentifier [erIdentifierAlias]
  //   | directive
  //   | accStatement
  static boolean erStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erRelationStatement(builder_, level_ + 1);
    if (!result_) result_ = entityDeclaration(builder_, level_ + 1);
    if (!result_) result_ = erStatement_2(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // erIdentifier [erIdentifierAlias]
  private static boolean erStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erIdentifier(builder_, level_ + 1);
    result_ = result_ && erStatement_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [erIdentifierAlias]
  private static boolean erStatement_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "erStatement_2_1")) return false;
    erIdentifierAlias(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // [frontmatter EOL] (oldStart | newStart)
  static boolean file(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "file")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = file_0(builder_, level_ + 1);
    result_ = result_ && file_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [frontmatter EOL]
  private static boolean file_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "file_0")) return false;
    file_0_0(builder_, level_ + 1);
    return true;
  }

  // frontmatter EOL
  private static boolean file_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "file_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = frontmatter(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // oldStart | newStart
  private static boolean file_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "file_1")) return false;
    boolean result_;
    result_ = oldStart(builder_, level_ + 1);
    if (!result_) result_ = newStart(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // flowchartLines
  public static boolean flowchartBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FLOWCHART_BODY, "<flowchart body>");
    result_ = flowchartLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // CLASS identifier (COMMA identifier)* STYLE_TARGET
  public static boolean flowchartClassStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClassStatement")) return false;
    if (!nextTokenIs(builder_, CLASS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS);
    result_ = result_ && identifier(builder_, level_ + 1);
    result_ = result_ && flowchartClassStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, STYLE_TARGET);
    exit_section_(builder_, marker_, FLOWCHART_CLASS_STATEMENT, result_);
    return result_;
  }

  // (COMMA identifier)*
  private static boolean flowchartClassStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClassStatement_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!flowchartClassStatement_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "flowchartClassStatement_2", pos_)) break;
    }
    return true;
  }

  // COMMA identifier
  private static boolean flowchartClassStatement_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClassStatement_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CLICK [CLICK_DATA] [HREF] string [string] [LINK_TARGET]
  //   | CLICK [CALL] CLICK_DATA [callbackArgs] string
  //   | CLICK CLICK_DATA [CALL? CLICK_DATA callbackArgs?] [string]
  public static boolean flowchartClickStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement")) return false;
    if (!nextTokenIs(builder_, CLICK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartClickStatement_0(builder_, level_ + 1);
    if (!result_) result_ = flowchartClickStatement_1(builder_, level_ + 1);
    if (!result_) result_ = flowchartClickStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, FLOWCHART_CLICK_STATEMENT, result_);
    return result_;
  }

  // CLICK [CLICK_DATA] [HREF] string [string] [LINK_TARGET]
  private static boolean flowchartClickStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLICK);
    result_ = result_ && flowchartClickStatement_0_1(builder_, level_ + 1);
    result_ = result_ && flowchartClickStatement_0_2(builder_, level_ + 1);
    result_ = result_ && string(builder_, level_ + 1);
    result_ = result_ && flowchartClickStatement_0_4(builder_, level_ + 1);
    result_ = result_ && flowchartClickStatement_0_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [CLICK_DATA]
  private static boolean flowchartClickStatement_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_0_1")) return false;
    consumeToken(builder_, CLICK_DATA);
    return true;
  }

  // [HREF]
  private static boolean flowchartClickStatement_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_0_2")) return false;
    consumeToken(builder_, HREF);
    return true;
  }

  // [string]
  private static boolean flowchartClickStatement_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_0_4")) return false;
    string(builder_, level_ + 1);
    return true;
  }

  // [LINK_TARGET]
  private static boolean flowchartClickStatement_0_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_0_5")) return false;
    consumeToken(builder_, LINK_TARGET);
    return true;
  }

  // CLICK [CALL] CLICK_DATA [callbackArgs] string
  private static boolean flowchartClickStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLICK);
    result_ = result_ && flowchartClickStatement_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLICK_DATA);
    result_ = result_ && flowchartClickStatement_1_3(builder_, level_ + 1);
    result_ = result_ && string(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [CALL]
  private static boolean flowchartClickStatement_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_1_1")) return false;
    consumeToken(builder_, CALL);
    return true;
  }

  // [callbackArgs]
  private static boolean flowchartClickStatement_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_1_3")) return false;
    callbackArgs(builder_, level_ + 1);
    return true;
  }

  // CLICK CLICK_DATA [CALL? CLICK_DATA callbackArgs?] [string]
  private static boolean flowchartClickStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CLICK, CLICK_DATA);
    result_ = result_ && flowchartClickStatement_2_2(builder_, level_ + 1);
    result_ = result_ && flowchartClickStatement_2_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [CALL? CLICK_DATA callbackArgs?]
  private static boolean flowchartClickStatement_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_2_2")) return false;
    flowchartClickStatement_2_2_0(builder_, level_ + 1);
    return true;
  }

  // CALL? CLICK_DATA callbackArgs?
  private static boolean flowchartClickStatement_2_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_2_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartClickStatement_2_2_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLICK_DATA);
    result_ = result_ && flowchartClickStatement_2_2_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CALL?
  private static boolean flowchartClickStatement_2_2_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_2_2_0_0")) return false;
    consumeToken(builder_, CALL);
    return true;
  }

  // callbackArgs?
  private static boolean flowchartClickStatement_2_2_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_2_2_0_2")) return false;
    callbackArgs(builder_, level_ + 1);
    return true;
  }

  // [string]
  private static boolean flowchartClickStatement_2_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartClickStatement_2_3")) return false;
    string(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // FLOWCHART [DIR]
  public static boolean flowchartHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartHeader")) return false;
    if (!nextTokenIs(builder_, FLOWCHART)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, FLOWCHART);
    result_ = result_ && flowchartHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, FLOWCHART_HEADER, result_);
    return result_;
  }

  // [DIR]
  private static boolean flowchartHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartHeader_1")) return false;
    consumeToken(builder_, DIR);
    return true;
  }

  /* ********************************************************** */
  // flowchartStatement [separator] | separator
  static boolean flowchartLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // flowchartStatement [separator]
  private static boolean flowchartLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartStatement(builder_, level_ + 1);
    result_ = result_ && flowchartLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean flowchartLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (flowchartLine | IGNORED) [flowchartLines]
  static boolean flowchartLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartLines_0(builder_, level_ + 1);
    result_ = result_ && flowchartLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // flowchartLine | IGNORED
  private static boolean flowchartLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLines_0")) return false;
    boolean result_;
    result_ = flowchartLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [flowchartLines]
  private static boolean flowchartLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLines_1")) return false;
    flowchartLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // START_ARROW complexLinkText ARROW | ARROW [SEP complexLinkText SEP]
  public static boolean flowchartLinkStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLinkStatement")) return false;
    if (!nextTokenIs(builder_, "<flowchart link statement>", ARROW, START_ARROW)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FLOWCHART_LINK_STATEMENT, "<flowchart link statement>");
    result_ = flowchartLinkStatement_0(builder_, level_ + 1);
    if (!result_) result_ = flowchartLinkStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // START_ARROW complexLinkText ARROW
  private static boolean flowchartLinkStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLinkStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, START_ARROW);
    result_ = result_ && complexLinkText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ARROW [SEP complexLinkText SEP]
  private static boolean flowchartLinkStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLinkStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ARROW);
    result_ = result_ && flowchartLinkStatement_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [SEP complexLinkText SEP]
  private static boolean flowchartLinkStatement_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLinkStatement_1_1")) return false;
    flowchartLinkStatement_1_1_0(builder_, level_ + 1);
    return true;
  }

  // SEP complexLinkText SEP
  private static boolean flowchartLinkStatement_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartLinkStatement_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEP);
    result_ = result_ && complexLinkText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SEP);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // vertexStatement (SEMICOLON vertexStatement)*
  //   | subgraphStatement
  //   | styleStatement
  //   | linkStyleStatement
  //   | classDefStatement
  //   | flowchartClassStatement
  //   | flowchartClickStatement
  // //	| directive
  //   | accStatement
  static boolean flowchartStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartStatement_0(builder_, level_ + 1);
    if (!result_) result_ = subgraphStatement(builder_, level_ + 1);
    if (!result_) result_ = styleStatement(builder_, level_ + 1);
    if (!result_) result_ = linkStyleStatement(builder_, level_ + 1);
    if (!result_) result_ = classDefStatement(builder_, level_ + 1);
    if (!result_) result_ = flowchartClassStatement(builder_, level_ + 1);
    if (!result_) result_ = flowchartClickStatement(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // vertexStatement (SEMICOLON vertexStatement)*
  private static boolean flowchartStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = vertexStatement(builder_, level_ + 1);
    result_ = result_ && flowchartStatement_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (SEMICOLON vertexStatement)*
  private static boolean flowchartStatement_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartStatement_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!flowchartStatement_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "flowchartStatement_0_1", pos_)) break;
    }
    return true;
  }

  // SEMICOLON vertexStatement
  private static boolean flowchartStatement_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "flowchartStatement_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && vertexStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // FRONTMATTER_START EOL frontmatterContent FRONTMATTER_END
  public static boolean frontmatter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "frontmatter")) return false;
    if (!nextTokenIs(builder_, FRONTMATTER_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, FRONTMATTER_START, EOL);
    result_ = result_ && frontmatterParser(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FRONTMATTER_END);
    exit_section_(builder_, marker_, FRONTMATTER, result_);
    return result_;
  }

  /* ********************************************************** */
  // AXIS_FORMAT GANTT_VALUE+
  public static boolean ganttAxisFormatStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttAxisFormatStatement")) return false;
    if (!nextTokenIs(builder_, AXIS_FORMAT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AXIS_FORMAT);
    result_ = result_ && ganttAxisFormatStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_AXIS_FORMAT_STATEMENT, result_);
    return result_;
  }

  // GANTT_VALUE+
  private static boolean ganttAxisFormatStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttAxisFormatStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GANTT_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttAxisFormatStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ganttLines
  public static boolean ganttBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GANTT_BODY, "<gantt body>");
    result_ = ganttLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // CLICK CLICK_DATA (CALL CLICK_DATA callbackArgs | HREF string)
  public static boolean ganttClickStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttClickStatement")) return false;
    if (!nextTokenIs(builder_, CLICK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CLICK, CLICK_DATA);
    result_ = result_ && ganttClickStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_CLICK_STATEMENT, result_);
    return result_;
  }

  // CALL CLICK_DATA callbackArgs | HREF string
  private static boolean ganttClickStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttClickStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttClickStatement_2_0(builder_, level_ + 1);
    if (!result_) result_ = ganttClickStatement_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CALL CLICK_DATA callbackArgs
  private static boolean ganttClickStatement_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttClickStatement_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CALL, CLICK_DATA);
    result_ = result_ && callbackArgs(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // HREF string
  private static boolean ganttClickStatement_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttClickStatement_2_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, HREF);
    result_ = result_ && string(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // complexTaskName COLON recTaskData
  public static boolean ganttDataStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttDataStatement")) return false;
    if (!nextTokenIs(builder_, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexTaskName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && recTaskData(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_DATA_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // DATE_FORMAT GANTT_VALUE+
  public static boolean ganttDateFormatStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttDateFormatStatement")) return false;
    if (!nextTokenIs(builder_, DATE_FORMAT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DATE_FORMAT);
    result_ = result_ && ganttDateFormatStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_DATE_FORMAT_STATEMENT, result_);
    return result_;
  }

  // GANTT_VALUE+
  private static boolean ganttDateFormatStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttDateFormatStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GANTT_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttDateFormatStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // EXCLUDES GANTT_VALUE+
  public static boolean ganttExcludesStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttExcludesStatement")) return false;
    if (!nextTokenIs(builder_, EXCLUDES)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EXCLUDES);
    result_ = result_ && ganttExcludesStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_EXCLUDES_STATEMENT, result_);
    return result_;
  }

  // GANTT_VALUE+
  private static boolean ganttExcludesStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttExcludesStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GANTT_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttExcludesStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // GANTT
  public static boolean ganttHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttHeader")) return false;
    if (!nextTokenIs(builder_, GANTT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT);
    exit_section_(builder_, marker_, GANTT_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // INCLUDES GANTT_VALUE+
  public static boolean ganttIncludesStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttIncludesStatement")) return false;
    if (!nextTokenIs(builder_, INCLUDES)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, INCLUDES);
    result_ = result_ && ganttIncludesStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_INCLUDES_STATEMENT, result_);
    return result_;
  }

  // GANTT_VALUE+
  private static boolean ganttIncludesStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttIncludesStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GANTT_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttIncludesStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // INCLUSIVE_END_DATES
  public static boolean ganttInclusiveEndDatesStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttInclusiveEndDatesStatement")) return false;
    if (!nextTokenIs(builder_, INCLUSIVE_END_DATES)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, INCLUSIVE_END_DATES);
    exit_section_(builder_, marker_, GANTT_INCLUSIVE_END_DATES_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // ganttStatement [IGNORED] [EOL] | [IGNORED] EOL
  static boolean ganttLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttLine_0(builder_, level_ + 1);
    if (!result_) result_ = ganttLine_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ganttStatement [IGNORED] [EOL]
  private static boolean ganttLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttStatement(builder_, level_ + 1);
    result_ = result_ && ganttLine_0_1(builder_, level_ + 1);
    result_ = result_ && ganttLine_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IGNORED]
  private static boolean ganttLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLine_0_1")) return false;
    consumeToken(builder_, IGNORED);
    return true;
  }

  // [EOL]
  private static boolean ganttLine_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLine_0_2")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  // [IGNORED] EOL
  private static boolean ganttLine_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLine_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttLine_1_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IGNORED]
  private static boolean ganttLine_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLine_1_0")) return false;
    consumeToken(builder_, IGNORED);
    return true;
  }

  /* ********************************************************** */
  // ganttLine [ganttLines]
  static boolean ganttLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttLine(builder_, level_ + 1);
    result_ = result_ && ganttLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ganttLines]
  private static boolean ganttLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttLines_1")) return false;
    ganttLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ganttSectionLines
  public static boolean ganttSectionBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionBlock")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GANTT_SECTION_BLOCK, "<gantt section block>");
    result_ = ganttSectionLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SECTION complexSectionTitle
  public static boolean ganttSectionHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionHeader")) return false;
    if (!nextTokenIs(builder_, SECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SECTION);
    result_ = result_ && complexSectionTitle(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_SECTION_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // ganttDataStatement
  static boolean ganttSectionInnerStatement(PsiBuilder builder_, int level_) {
    return ganttDataStatement(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // ganttSectionInnerStatement [EOL] | EOL
  static boolean ganttSectionLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionLine")) return false;
    if (!nextTokenIs(builder_, "", EOL, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttSectionLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ganttSectionInnerStatement [EOL]
  private static boolean ganttSectionLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttSectionInnerStatement(builder_, level_ + 1);
    result_ = result_ && ganttSectionLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean ganttSectionLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // (ganttSectionLine | IGNORED) [ganttSectionLines]
  static boolean ganttSectionLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttSectionLines_0(builder_, level_ + 1);
    result_ = result_ && ganttSectionLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ganttSectionLine | IGNORED
  private static boolean ganttSectionLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionLines_0")) return false;
    boolean result_;
    result_ = ganttSectionLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [ganttSectionLines]
  private static boolean ganttSectionLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionLines_1")) return false;
    ganttSectionLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ganttSectionHeader [EOL+ ganttSectionBlock]
  public static boolean ganttSectionStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionStatement")) return false;
    if (!nextTokenIs(builder_, SECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttSectionHeader(builder_, level_ + 1);
    result_ = result_ && ganttSectionStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_SECTION_STATEMENT, result_);
    return result_;
  }

  // [EOL+ ganttSectionBlock]
  private static boolean ganttSectionStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionStatement_1")) return false;
    ganttSectionStatement_1_0(builder_, level_ + 1);
    return true;
  }

  // EOL+ ganttSectionBlock
  private static boolean ganttSectionStatement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionStatement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttSectionStatement_1_0_0(builder_, level_ + 1);
    result_ = result_ && ganttSectionBlock(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL+
  private static boolean ganttSectionStatement_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttSectionStatement_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttSectionStatement_1_0_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ganttDateFormatStatement
  //   | titleStatement
  //   | ganttExcludesStatement
  //   | ganttIncludesStatement
  //   | ganttAxisFormatStatement
  //   | ganttTodayMarkerStatement
  //   | ganttTickIntervalStatement
  //   | ganttSectionStatement
  //   | ganttDataStatement
  //   | ganttInclusiveEndDatesStatement
  //   | ganttTopAxisStatement
  //   | ganttWeekdayStatement
  //   | directive
  //   | ganttClickStatement
  //   | accStatement
  static boolean ganttStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttStatement")) return false;
    boolean result_;
    result_ = ganttDateFormatStatement(builder_, level_ + 1);
    if (!result_) result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttExcludesStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttIncludesStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttAxisFormatStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttTodayMarkerStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttTickIntervalStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttSectionStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttDataStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttInclusiveEndDatesStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttTopAxisStatement(builder_, level_ + 1);
    if (!result_) result_ = ganttWeekdayStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = ganttClickStatement(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // TICK_INTERVAL GANTT_VALUE+
  public static boolean ganttTickIntervalStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttTickIntervalStatement")) return false;
    if (!nextTokenIs(builder_, TICK_INTERVAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TICK_INTERVAL);
    result_ = result_ && ganttTickIntervalStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_TICK_INTERVAL_STATEMENT, result_);
    return result_;
  }

  // GANTT_VALUE+
  private static boolean ganttTickIntervalStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttTickIntervalStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GANTT_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttTickIntervalStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // TODAY_MARKER GANTT_VALUE+
  public static boolean ganttTodayMarkerStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttTodayMarkerStatement")) return false;
    if (!nextTokenIs(builder_, TODAY_MARKER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TODAY_MARKER);
    result_ = result_ && ganttTodayMarkerStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_TODAY_MARKER_STATEMENT, result_);
    return result_;
  }

  // GANTT_VALUE+
  private static boolean ganttTodayMarkerStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttTodayMarkerStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GANTT_VALUE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GANTT_VALUE)) break;
      if (!empty_element_parsed_guard_(builder_, "ganttTodayMarkerStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // TOP_AXIS
  public static boolean ganttTopAxisStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttTopAxisStatement")) return false;
    if (!nextTokenIs(builder_, TOP_AXIS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TOP_AXIS);
    exit_section_(builder_, marker_, GANTT_TOP_AXIS_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // WEEKDAY (MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY)
  public static boolean ganttWeekdayStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttWeekdayStatement")) return false;
    if (!nextTokenIs(builder_, WEEKDAY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WEEKDAY);
    result_ = result_ && ganttWeekdayStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GANTT_WEEKDAY_STATEMENT, result_);
    return result_;
  }

  // MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY
  private static boolean ganttWeekdayStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ganttWeekdayStatement_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, MONDAY);
    if (!result_) result_ = consumeToken(builder_, TUESDAY);
    if (!result_) result_ = consumeToken(builder_, WEDNESDAY);
    if (!result_) result_ = consumeToken(builder_, THURSDAY);
    if (!result_) result_ = consumeToken(builder_, FRIDAY);
    if (!result_) result_ = consumeToken(builder_, SATURDAY);
    if (!result_) result_ = consumeToken(builder_, SUNDAY);
    return result_;
  }

  /* ********************************************************** */
  // TILDA genericTypeId [generic] TILDA
  public static boolean generic(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "generic")) return false;
    if (!nextTokenIs(builder_, TILDA)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TILDA);
    result_ = result_ && genericTypeId(builder_, level_ + 1);
    result_ = result_ && generic_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, TILDA);
    exit_section_(builder_, marker_, GENERIC, result_);
    return result_;
  }

  // [generic]
  private static boolean generic_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "generic_2")) return false;
    generic(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // GENERIC_TYPE+
  public static boolean genericTypeId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeId")) return false;
    if (!nextTokenIs(builder_, GENERIC_TYPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GENERIC_TYPE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, GENERIC_TYPE)) break;
      if (!empty_element_parsed_guard_(builder_, "genericTypeId", pos_)) break;
    }
    exit_section_(builder_, marker_, GENERIC_TYPE_ID, result_);
    return result_;
  }

  /* ********************************************************** */
  // gitGraphLines
  public static boolean gitGraphBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GIT_GRAPH_BODY, "<git graph body>");
    result_ = gitGraphLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // identifier | DOUBLE_QUOTE quotedBranchIdentifier DOUBLE_QUOTE
  public static boolean gitGraphBranchIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphBranchIdentifier")) return false;
    if (!nextTokenIs(builder_, "<git graph branch identifier>", DOUBLE_QUOTE, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GIT_GRAPH_BRANCH_IDENTIFIER, "<git graph branch identifier>");
    result_ = identifier(builder_, level_ + 1);
    if (!result_) result_ = gitGraphBranchIdentifier_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // DOUBLE_QUOTE quotedBranchIdentifier DOUBLE_QUOTE
  private static boolean gitGraphBranchIdentifier_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphBranchIdentifier_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && quotedBranchIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // GIT_GRAPH [DIR] COLON | GIT_GRAPH
  public static boolean gitGraphHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphHeader")) return false;
    if (!nextTokenIs(builder_, GIT_GRAPH)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = gitGraphHeader_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, GIT_GRAPH);
    exit_section_(builder_, marker_, GIT_GRAPH_HEADER, result_);
    return result_;
  }

  // GIT_GRAPH [DIR] COLON
  private static boolean gitGraphHeader_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphHeader_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GIT_GRAPH);
    result_ = result_ && gitGraphHeader_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [DIR]
  private static boolean gitGraphHeader_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphHeader_0_1")) return false;
    consumeToken(builder_, DIR);
    return true;
  }

  /* ********************************************************** */
  // gitGraphStatement [IGNORED] [EOL] | [IGNORED] EOL
  static boolean gitGraphLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = gitGraphLine_0(builder_, level_ + 1);
    if (!result_) result_ = gitGraphLine_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // gitGraphStatement [IGNORED] [EOL]
  private static boolean gitGraphLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = gitGraphStatement(builder_, level_ + 1);
    result_ = result_ && gitGraphLine_0_1(builder_, level_ + 1);
    result_ = result_ && gitGraphLine_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IGNORED]
  private static boolean gitGraphLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLine_0_1")) return false;
    consumeToken(builder_, IGNORED);
    return true;
  }

  // [EOL]
  private static boolean gitGraphLine_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLine_0_2")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  // [IGNORED] EOL
  private static boolean gitGraphLine_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLine_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = gitGraphLine_1_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IGNORED]
  private static boolean gitGraphLine_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLine_1_0")) return false;
    consumeToken(builder_, IGNORED);
    return true;
  }

  /* ********************************************************** */
  // gitGraphLine [gitGraphLines]
  static boolean gitGraphLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = gitGraphLine(builder_, level_ + 1);
    result_ = result_ && gitGraphLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [gitGraphLines]
  private static boolean gitGraphLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphLines_1")) return false;
    gitGraphLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // commitStatement
  //   | mergeStatement
  //   | cherryPickStatement
  //   | branchStatement
  //   | checkoutStatement
  //   | accStatement
  static boolean gitGraphStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gitGraphStatement")) return false;
    boolean result_;
    result_ = commitStatement(builder_, level_ + 1);
    if (!result_) result_ = mergeStatement(builder_, level_ + 1);
    if (!result_) result_ = cherryPickStatement(builder_, level_ + 1);
    if (!result_) result_ = branchStatement(builder_, level_ + 1);
    if (!result_) result_ = checkoutStatement(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_ICON ICON_VALUE? CLOSE_ICON
  public static boolean iconStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "iconStatement")) return false;
    if (!nextTokenIs(builder_, OPEN_ICON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_ICON);
    result_ = result_ && iconStatement_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_ICON);
    exit_section_(builder_, marker_, ICON_STATEMENT, result_);
    return result_;
  }

  // ICON_VALUE?
  private static boolean iconStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "iconStatement_1")) return false;
    consumeToken(builder_, ICON_VALUE);
    return true;
  }

  /* ********************************************************** */
  // ALIAS+
  public static boolean idAlias(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "idAlias")) return false;
    if (!nextTokenIs(builder_, ALIAS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ALIAS);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ALIAS)) break;
      if (!empty_element_parsed_guard_(builder_, "idAlias", pos_)) break;
    }
    exit_section_(builder_, marker_, ID_ALIAS, result_);
    return result_;
  }

  /* ********************************************************** */
  // ID
  public static boolean identifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifier")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ID);
    exit_section_(builder_, marker_, IDENTIFIER, result_);
    return result_;
  }

  /* ********************************************************** */
  // SANKEY_TEXT+
  public static boolean identifyingComplexSankeyText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingComplexSankeyText")) return false;
    if (!nextTokenIs(builder_, SANKEY_TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SANKEY_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, SANKEY_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "identifyingComplexSankeyText", pos_)) break;
    }
    exit_section_(builder_, marker_, IDENTIFYING_COMPLEX_SANKEY_TEXT, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE identifyingQuotedSankeyFieldValue DOUBLE_QUOTE
  public static boolean identifyingQuotedSankeyField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingQuotedSankeyField")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && identifyingQuotedSankeyFieldValue(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, IDENTIFYING_QUOTED_SANKEY_FIELD, result_);
    return result_;
  }

  /* ********************************************************** */
  // ( quotedSankeyFieldInnerValue | (complexSankeyText | COMMA)+ )+
  public static boolean identifyingQuotedSankeyFieldValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingQuotedSankeyFieldValue")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IDENTIFYING_QUOTED_SANKEY_FIELD_VALUE, "<identifying quoted sankey field value>");
    result_ = identifyingQuotedSankeyFieldValue_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!identifyingQuotedSankeyFieldValue_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "identifyingQuotedSankeyFieldValue", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // quotedSankeyFieldInnerValue | (complexSankeyText | COMMA)+
  private static boolean identifyingQuotedSankeyFieldValue_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingQuotedSankeyFieldValue_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quotedSankeyFieldInnerValue(builder_, level_ + 1);
    if (!result_) result_ = identifyingQuotedSankeyFieldValue_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (complexSankeyText | COMMA)+
  private static boolean identifyingQuotedSankeyFieldValue_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingQuotedSankeyFieldValue_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = identifyingQuotedSankeyFieldValue_0_1_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!identifyingQuotedSankeyFieldValue_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "identifyingQuotedSankeyFieldValue_0_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // complexSankeyText | COMMA
  private static boolean identifyingQuotedSankeyFieldValue_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingQuotedSankeyFieldValue_0_1_0")) return false;
    boolean result_;
    result_ = complexSankeyText(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    return result_;
  }

  /* ********************************************************** */
  // identifyingQuotedSankeyField | identifyingComplexSankeyText
  static boolean identifyingSankeyField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifyingSankeyField")) return false;
    if (!nextTokenIs(builder_, "", DOUBLE_QUOTE, SANKEY_TEXT)) return false;
    boolean result_;
    result_ = identifyingQuotedSankeyField(builder_, level_ + 1);
    if (!result_) result_ = identifyingComplexSankeyText(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // innerStateLines
  static boolean innerStateBody(PsiBuilder builder_, int level_) {
    return innerStateLines(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // stateDiagramStatement | dividerStatement
  static boolean innerStateDiagramStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "innerStateDiagramStatement")) return false;
    boolean result_;
    result_ = stateDiagramStatement(builder_, level_ + 1);
    if (!result_) result_ = dividerStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // innerStateDiagramStatement [EOL] | EOL
  static boolean innerStateLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "innerStateLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = innerStateLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // innerStateDiagramStatement [EOL]
  private static boolean innerStateLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "innerStateLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = innerStateDiagramStatement(builder_, level_ + 1);
    result_ = result_ && innerStateLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean innerStateLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "innerStateLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // innerStateLine [innerStateLines]
  static boolean innerStateLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "innerStateLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = innerStateLine(builder_, level_ + 1);
    result_ = result_ && innerStateLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [innerStateLines]
  private static boolean innerStateLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "innerStateLines_1")) return false;
    innerStateLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // journeyLines
  public static boolean journeyBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, JOURNEY_BODY, "<journey body>");
    result_ = journeyLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // complexTaskName COLON (IGNORED | sectionTaskData [COLON journeyNamedData? (COLON sectionTaskData?)*])
  public static boolean journeyDataStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement")) return false;
    if (!nextTokenIs(builder_, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexTaskName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && journeyDataStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, JOURNEY_DATA_STATEMENT, result_);
    return result_;
  }

  // IGNORED | sectionTaskData [COLON journeyNamedData? (COLON sectionTaskData?)*]
  private static boolean journeyDataStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IGNORED);
    if (!result_) result_ = journeyDataStatement_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sectionTaskData [COLON journeyNamedData? (COLON sectionTaskData?)*]
  private static boolean journeyDataStatement_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sectionTaskData(builder_, level_ + 1);
    result_ = result_ && journeyDataStatement_2_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COLON journeyNamedData? (COLON sectionTaskData?)*]
  private static boolean journeyDataStatement_2_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1_1")) return false;
    journeyDataStatement_2_1_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON journeyNamedData? (COLON sectionTaskData?)*
  private static boolean journeyDataStatement_2_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && journeyDataStatement_2_1_1_0_1(builder_, level_ + 1);
    result_ = result_ && journeyDataStatement_2_1_1_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // journeyNamedData?
  private static boolean journeyDataStatement_2_1_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1_1_0_1")) return false;
    journeyNamedData(builder_, level_ + 1);
    return true;
  }

  // (COLON sectionTaskData?)*
  private static boolean journeyDataStatement_2_1_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1_1_0_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!journeyDataStatement_2_1_1_0_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "journeyDataStatement_2_1_1_0_2", pos_)) break;
    }
    return true;
  }

  // COLON sectionTaskData?
  private static boolean journeyDataStatement_2_1_1_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1_1_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && journeyDataStatement_2_1_1_0_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sectionTaskData?
  private static boolean journeyDataStatement_2_1_1_0_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyDataStatement_2_1_1_0_2_0_1")) return false;
    sectionTaskData(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // JOURNEY
  public static boolean journeyHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyHeader")) return false;
    if (!nextTokenIs(builder_, JOURNEY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, JOURNEY);
    exit_section_(builder_, marker_, JOURNEY_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // journeyStatement [EOL] | EOL
  static boolean journeyLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeyLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // journeyStatement [EOL]
  private static boolean journeyLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeyStatement(builder_, level_ + 1);
    result_ = result_ && journeyLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean journeyLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // (journeyLine | IGNORED) [journeyLines]
  static boolean journeyLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeyLines_0(builder_, level_ + 1);
    result_ = result_ && journeyLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // journeyLine | IGNORED
  private static boolean journeyLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyLines_0")) return false;
    boolean result_;
    result_ = journeyLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [journeyLines]
  private static boolean journeyLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyLines_1")) return false;
    journeyLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // recNamedData
  public static boolean journeyNamedData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyNamedData")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, JOURNEY_NAMED_DATA, "<journey named data>");
    result_ = recNamedData(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // journeySectionLines
  public static boolean journeySectionBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionBlock")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, JOURNEY_SECTION_BLOCK, "<journey section block>");
    result_ = journeySectionLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SECTION complexSectionTitle
  public static boolean journeySectionHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionHeader")) return false;
    if (!nextTokenIs(builder_, SECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SECTION);
    result_ = result_ && complexSectionTitle(builder_, level_ + 1);
    exit_section_(builder_, marker_, JOURNEY_SECTION_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // journeyDataStatement
  static boolean journeySectionInnerStatement(PsiBuilder builder_, int level_) {
    return journeyDataStatement(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // journeySectionInnerStatement [EOL] | EOL
  static boolean journeySectionLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionLine")) return false;
    if (!nextTokenIs(builder_, "", EOL, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeySectionLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // journeySectionInnerStatement [EOL]
  private static boolean journeySectionLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeySectionInnerStatement(builder_, level_ + 1);
    result_ = result_ && journeySectionLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean journeySectionLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // (journeySectionLine | IGNORED) [journeySectionLines]
  static boolean journeySectionLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeySectionLines_0(builder_, level_ + 1);
    result_ = result_ && journeySectionLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // journeySectionLine | IGNORED
  private static boolean journeySectionLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionLines_0")) return false;
    boolean result_;
    result_ = journeySectionLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [journeySectionLines]
  private static boolean journeySectionLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionLines_1")) return false;
    journeySectionLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // journeySectionHeader EOL* [journeySectionBlock]
  public static boolean journeySectionStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionStatement")) return false;
    if (!nextTokenIs(builder_, SECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeySectionHeader(builder_, level_ + 1);
    result_ = result_ && journeySectionStatement_1(builder_, level_ + 1);
    result_ = result_ && journeySectionStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, JOURNEY_SECTION_STATEMENT, result_);
    return result_;
  }

  // EOL*
  private static boolean journeySectionStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionStatement_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "journeySectionStatement_1", pos_)) break;
    }
    return true;
  }

  // [journeySectionBlock]
  private static boolean journeySectionStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeySectionStatement_2")) return false;
    journeySectionBlock(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // journeyDataStatement
  //   | journeySectionStatement
  //   | titleStatement
  //   | directive
  //   | accStatement
  static boolean journeyStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "journeyStatement")) return false;
    boolean result_;
    result_ = journeyDataStatement(builder_, level_ + 1);
    if (!result_) result_ = journeySectionStatement(builder_, level_ + 1);
    if (!result_) result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // classDiagramIdentifier [generic]
  public static boolean leftId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leftId")) return false;
    if (!nextTokenIs(builder_, "<left id>", BACK_QUOTE, CLASS_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LEFT_ID, "<left id>");
    result_ = classDiagramIdentifier(builder_, level_ + 1);
    result_ = result_ && leftId_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [generic]
  private static boolean leftId_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leftId_1")) return false;
    generic(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LINE_KEYWORD [xyChartText] plotData
  public static boolean lineStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lineStatement")) return false;
    if (!nextTokenIs(builder_, LINE_KEYWORD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINE_KEYWORD);
    result_ = result_ && lineStatement_1(builder_, level_ + 1);
    result_ = result_ && plotData(builder_, level_ + 1);
    exit_section_(builder_, marker_, LINE_STATEMENT, result_);
    return result_;
  }

  // [xyChartText]
  private static boolean lineStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lineStatement_1")) return false;
    xyChartText(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LINE | DOTTED_LINE
  public static boolean lineType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lineType")) return false;
    if (!nextTokenIs(builder_, "<line type>", DOTTED_LINE, LINE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LINE_TYPE, "<line type>");
    result_ = consumeToken(builder_, LINE);
    if (!result_) result_ = consumeToken(builder_, DOTTED_LINE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // maybeEmptyString COLON maybeEmptyString [COMMA linkContent]
  static boolean linkContent(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkContent")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = maybeEmptyString(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && maybeEmptyString(builder_, level_ + 1);
    result_ = result_ && linkContent_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA linkContent]
  private static boolean linkContent_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkContent_3")) return false;
    linkContent_3_0(builder_, level_ + 1);
    return true;
  }

  // COMMA linkContent
  private static boolean linkContent_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkContent_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && linkContent(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LINK complexIdentifier COLON complexMessage
  public static boolean linkStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkStatement")) return false;
    if (!nextTokenIs(builder_, LINK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINK);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && complexMessage(builder_, level_ + 1);
    exit_section_(builder_, marker_, LINK_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // LINK_STYLE linkStyleTarget styleOptions
  public static boolean linkStyleStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkStyleStatement")) return false;
    if (!nextTokenIs(builder_, LINK_STYLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINK_STYLE);
    result_ = result_ && linkStyleTarget(builder_, level_ + 1);
    result_ = result_ && styleOptions(builder_, level_ + 1);
    exit_section_(builder_, marker_, LINK_STYLE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // STYLE_TARGET (COMMA STYLE_TARGET)* | DEFAULT
  static boolean linkStyleTarget(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkStyleTarget")) return false;
    if (!nextTokenIs(builder_, "", DEFAULT, STYLE_TARGET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = linkStyleTarget_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, DEFAULT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STYLE_TARGET (COMMA STYLE_TARGET)*
  private static boolean linkStyleTarget_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkStyleTarget_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STYLE_TARGET);
    result_ = result_ && linkStyleTarget_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA STYLE_TARGET)*
  private static boolean linkStyleTarget_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkStyleTarget_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!linkStyleTarget_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "linkStyleTarget_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA STYLE_TARGET
  private static boolean linkStyleTarget_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linkStyleTarget_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, STYLE_TARGET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LINKS complexIdentifier COLON linksValues
  public static boolean linksStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linksStatement")) return false;
    if (!nextTokenIs(builder_, LINKS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINKS);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && linksValues(builder_, level_ + 1);
    exit_section_(builder_, marker_, LINKS_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY linkContent CLOSE_CURLY
  public static boolean linksValues(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "linksValues")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && linkContent(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, LINKS_VALUES, result_);
    return result_;
  }

  /* ********************************************************** */
  // LOOP [complexControlId]
  public static boolean loopHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "loopHeader")) return false;
    if (!nextTokenIs(builder_, LOOP)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LOOP);
    result_ = result_ && loopHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, LOOP_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean loopHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "loopHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // loopHeader EOL+ [sequenceBody] END
  public static boolean loopStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "loopStatement")) return false;
    if (!nextTokenIs(builder_, LOOP)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = loopHeader(builder_, level_ + 1);
    result_ = result_ && loopStatement_1(builder_, level_ + 1);
    result_ = result_ && loopStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, LOOP_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean loopStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "loopStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "loopStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean loopStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "loopStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // MD_STRING_VALUE
  public static boolean markdownValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "markdownValue")) return false;
    if (!nextTokenIs(builder_, MD_STRING_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MD_STRING_VALUE);
    exit_section_(builder_, marker_, MARKDOWN_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE BACK_QUOTE [markdownValue] BACK_QUOTE DOUBLE_QUOTE
  static boolean maybeEmptyMdText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyMdText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOUBLE_QUOTE, BACK_QUOTE);
    result_ = result_ && maybeEmptyMdText_2(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, BACK_QUOTE, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [markdownValue]
  private static boolean maybeEmptyMdText_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyMdText_2")) return false;
    markdownValue(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE LINK_TEXT* DOUBLE_QUOTE
  static boolean maybeEmptyQuotedLinkText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyQuotedLinkText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && maybeEmptyQuotedLinkText_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LINK_TEXT*
  private static boolean maybeEmptyQuotedLinkText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyQuotedLinkText_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LINK_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "maybeEmptyQuotedLinkText_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE ALIAS* DOUBLE_QUOTE
  static boolean maybeEmptyQuotedNodeText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyQuotedNodeText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && maybeEmptyQuotedNodeText_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ALIAS*
  private static boolean maybeEmptyQuotedNodeText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyQuotedNodeText_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ALIAS)) break;
      if (!empty_element_parsed_guard_(builder_, "maybeEmptyQuotedNodeText_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE DOUBLE_QUOTE | string
  static boolean maybeEmptyString(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "maybeEmptyString")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parseTokens(builder_, 0, DOUBLE_QUOTE, DOUBLE_QUOTE);
    if (!result_) result_ = string(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE BACK_QUOTE markdownValue BACK_QUOTE DOUBLE_QUOTE
  static boolean mdText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mdText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOUBLE_QUOTE, BACK_QUOTE);
    result_ = result_ && markdownValue(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, BACK_QUOTE, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ATTRIBUTE_WORD
  //   | genericTypeId
  //   | OPEN_SQUARE | CLOSE_SQUARE
  //   | OPEN_CURLY | CLOSE_CURLY
  //   | OPEN_ROUND | CLOSE_ROUND
  //   | OPEN_ANGLE | CLOSE_ANGLE
  //   | PLUS | MINUS | POUND | TILDA | STAR | DOLLAR | COMMA | DOT
  static boolean memberAttrWord(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberAttrWord")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ATTRIBUTE_WORD);
    if (!result_) result_ = genericTypeId(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, OPEN_SQUARE);
    if (!result_) result_ = consumeToken(builder_, CLOSE_SQUARE);
    if (!result_) result_ = consumeToken(builder_, OPEN_CURLY);
    if (!result_) result_ = consumeToken(builder_, CLOSE_CURLY);
    if (!result_) result_ = consumeToken(builder_, OPEN_ROUND);
    if (!result_) result_ = consumeToken(builder_, CLOSE_ROUND);
    if (!result_) result_ = consumeToken(builder_, OPEN_ANGLE);
    if (!result_) result_ = consumeToken(builder_, CLOSE_ANGLE);
    if (!result_) result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, POUND);
    if (!result_) result_ = consumeToken(builder_, TILDA);
    if (!result_) result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOLLAR);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, DOT);
    return result_;
  }

  /* ********************************************************** */
  // memberAttrWord+
  public static boolean memberAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberAttribute")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MEMBER_ATTRIBUTE, "<member attribute>");
    result_ = memberAttrWord(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!memberAttrWord(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "memberAttribute", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // classMemberStatement [EOL] | EOL
  static boolean memberLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = memberLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // classMemberStatement [EOL]
  private static boolean memberLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classMemberStatement(builder_, level_ + 1);
    result_ = result_ && memberLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean memberLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // memberLine [memberLines]
  static boolean memberLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = memberLine(builder_, level_ + 1);
    result_ = result_ && memberLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [memberLines]
  private static boolean memberLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberLines_1")) return false;
    memberLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // classDiagramIdentifier [generic] COLON memberAttribute
  public static boolean memberStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberStatement")) return false;
    if (!nextTokenIs(builder_, "<member statement>", BACK_QUOTE, CLASS_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MEMBER_STATEMENT, "<member statement>");
    result_ = classDiagramIdentifier(builder_, level_ + 1);
    result_ = result_ && memberStatement_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && memberAttribute(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [generic]
  private static boolean memberStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "memberStatement_1")) return false;
    generic(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // MERGE gitGraphBranchIdentifier [commitAttributes]
  public static boolean mergeStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mergeStatement")) return false;
    if (!nextTokenIs(builder_, MERGE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MERGE);
    result_ = result_ && gitGraphBranchIdentifier(builder_, level_ + 1);
    result_ = result_ && mergeStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, MERGE_STATEMENT, result_);
    return result_;
  }

  // [commitAttributes]
  private static boolean mergeStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mergeStatement_2")) return false;
    commitAttributes(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // mindmapLines
  public static boolean mindmapBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MINDMAP_BODY, "<mindmap body>");
    result_ = mindmapLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // STYLE_SEPARATOR CLASS+
  public static boolean mindmapClassStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapClassStatement")) return false;
    if (!nextTokenIs(builder_, STYLE_SEPARATOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STYLE_SEPARATOR);
    result_ = result_ && mindmapClassStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, MINDMAP_CLASS_STATEMENT, result_);
    return result_;
  }

  // CLASS+
  private static boolean mindmapClassStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapClassStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, CLASS)) break;
      if (!empty_element_parsed_guard_(builder_, "mindmapClassStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // MINDMAP
  public static boolean mindmapHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapHeader")) return false;
    if (!nextTokenIs(builder_, MINDMAP)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MINDMAP);
    exit_section_(builder_, marker_, MINDMAP_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // mindmapStatement [EOL] | EOL
  static boolean mindmapLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mindmapLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // mindmapStatement [EOL]
  private static boolean mindmapLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mindmapStatement(builder_, level_ + 1);
    result_ = result_ && mindmapLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean mindmapLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // mindmapLine [mindmapLines]
  static boolean mindmapLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mindmapLine(builder_, level_ + 1);
    result_ = result_ && mindmapLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [mindmapLines]
  private static boolean mindmapLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapLines_1")) return false;
    mindmapLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NODE_DESCR_START nodeDescription NODE_DESCR_END
  public static boolean mindmapNodeDescr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapNodeDescr")) return false;
    if (!nextTokenIs(builder_, NODE_DESCR_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NODE_DESCR_START);
    result_ = result_ && nodeDescription(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, NODE_DESCR_END);
    exit_section_(builder_, marker_, MINDMAP_NODE_DESCR, result_);
    return result_;
  }

  /* ********************************************************** */
  // (ID | COLON)+
  public static boolean mindmapNodeId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapNodeId")) return false;
    if (!nextTokenIs(builder_, "<mindmap node id>", COLON, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MINDMAP_NODE_ID, "<mindmap node id>");
    result_ = mindmapNodeId_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!mindmapNodeId_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "mindmapNodeId", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ID | COLON
  private static boolean mindmapNodeId_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapNodeId_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ID);
    if (!result_) result_ = consumeToken(builder_, COLON);
    return result_;
  }

  /* ********************************************************** */
  // mindmapNodeId [mindmapNodeDescr]
  public static boolean mindmapNodeStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapNodeStatement")) return false;
    if (!nextTokenIs(builder_, "<mindmap node statement>", COLON, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MINDMAP_NODE_STATEMENT, "<mindmap node statement>");
    result_ = mindmapNodeId(builder_, level_ + 1);
    result_ = result_ && mindmapNodeStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [mindmapNodeDescr]
  private static boolean mindmapNodeStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapNodeStatement_1")) return false;
    mindmapNodeDescr(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // mindmapNodeStatement
  //   | iconStatement
  //   | mindmapClassStatement
  //   | directive
  static boolean mindmapStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mindmapStatement")) return false;
    boolean result_;
    result_ = mindmapNodeStatement(builder_, level_ + 1);
    if (!result_) result_ = iconStatement(builder_, level_ + 1);
    if (!result_) result_ = mindmapClassStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL* namespaceStatements? CLOSE_CURLY
  public static boolean namespaceBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && namespaceBlock_1(builder_, level_ + 1);
    result_ = result_ && namespaceBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, NAMESPACE_BLOCK, result_);
    return result_;
  }

  // EOL*
  private static boolean namespaceBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceBlock_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "namespaceBlock_1", pos_)) break;
    }
    return true;
  }

  // namespaceStatements?
  private static boolean namespaceBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceBlock_2")) return false;
    namespaceStatements(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NAMESPACE namespaceIdentifier
  public static boolean namespaceHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceHeader")) return false;
    if (!nextTokenIs(builder_, NAMESPACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NAMESPACE);
    result_ = result_ && namespaceIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, NAMESPACE_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // CLASS_ID+
  public static boolean namespaceIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceIdentifier")) return false;
    if (!nextTokenIs(builder_, CLASS_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS_ID);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, CLASS_ID)) break;
      if (!empty_element_parsed_guard_(builder_, "namespaceIdentifier", pos_)) break;
    }
    exit_section_(builder_, marker_, NAMESPACE_IDENTIFIER, result_);
    return result_;
  }

  /* ********************************************************** */
  // classStatement [EOL] | EOL
  static boolean namespaceLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceLine")) return false;
    if (!nextTokenIs(builder_, "", CLASS, EOL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = namespaceLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // classStatement [EOL]
  private static boolean namespaceLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classStatement(builder_, level_ + 1);
    result_ = result_ && namespaceLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean namespaceLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // namespaceLine [namespaceLines]
  static boolean namespaceLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceLines")) return false;
    if (!nextTokenIs(builder_, "", CLASS, EOL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = namespaceLine(builder_, level_ + 1);
    result_ = result_ && namespaceLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [namespaceLines]
  private static boolean namespaceLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceLines_1")) return false;
    namespaceLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // namespaceHeader [namespaceBlock]
  public static boolean namespaceStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceStatement")) return false;
    if (!nextTokenIs(builder_, NAMESPACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = namespaceHeader(builder_, level_ + 1);
    result_ = result_ && namespaceStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, NAMESPACE_STATEMENT, result_);
    return result_;
  }

  // [namespaceBlock]
  private static boolean namespaceStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namespaceStatement_1")) return false;
    namespaceBlock(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // namespaceLines
  static boolean namespaceStatements(PsiBuilder builder_, int level_) {
    return namespaceLines(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // ZEN_UML
  //   | sankeyHeader EOL* [sankeyBody]
  static boolean newDiagram(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newDiagram")) return false;
    if (!nextTokenIs(builder_, "", SANKEY, ZEN_UML)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ZEN_UML);
    if (!result_) result_ = newDiagram_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sankeyHeader EOL* [sankeyBody]
  private static boolean newDiagram_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newDiagram_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sankeyHeader(builder_, level_ + 1);
    result_ = result_ && newDiagram_1_1(builder_, level_ + 1);
    result_ = result_ && newDiagram_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean newDiagram_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newDiagram_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "newDiagram_1_1", pos_)) break;
    }
    return true;
  }

  // [sankeyBody]
  private static boolean newDiagram_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newDiagram_1_2")) return false;
    sankeyBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // EOL newStart | newDiagram
  static boolean newStart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newStart")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = newStart_0(builder_, level_ + 1);
    if (!result_) result_ = newDiagram(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL newStart
  private static boolean newStart_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newStart_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    result_ = result_ && newStart(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // NODE_DESCR+ | string | mdText
  public static boolean nodeDescription(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeDescription")) return false;
    if (!nextTokenIs(builder_, "<node description>", DOUBLE_QUOTE, NODE_DESCR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NODE_DESCRIPTION, "<node description>");
    result_ = nodeDescription_0(builder_, level_ + 1);
    if (!result_) result_ = string(builder_, level_ + 1);
    if (!result_) result_ = mdText(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // NODE_DESCR+
  private static boolean nodeDescription_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeDescription_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NODE_DESCR);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NODE_DESCR)) break;
      if (!empty_element_parsed_guard_(builder_, "nodeDescription_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (styledVertex | vertex)  [AMPERSAND nodeStatement]
  public static boolean nodeStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeStatement")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, NODE_STATEMENT, null);
    result_ = nodeStatement_0(builder_, level_ + 1);
    result_ = result_ && nodeStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // styledVertex | vertex
  private static boolean nodeStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeStatement_0")) return false;
    boolean result_;
    result_ = styledVertex(builder_, level_ + 1);
    if (!result_) result_ = vertex(builder_, level_ + 1);
    return result_;
  }

  // [AMPERSAND nodeStatement]
  private static boolean nodeStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeStatement_1")) return false;
    nodeStatement_1_0(builder_, level_ + 1);
    return true;
  }

  // AMPERSAND nodeStatement
  private static boolean nodeStatement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeStatement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && nodeStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (maybeEmptyQuotedNodeText | maybeEmptyMdText) ALIAS+
  //   | (quotedNodeText | mdText) ALIAS*
  //   | ALIAS+
  public static boolean nodeText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText")) return false;
    if (!nextTokenIs(builder_, "<node text>", ALIAS, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NODE_TEXT, "<node text>");
    result_ = nodeText_0(builder_, level_ + 1);
    if (!result_) result_ = nodeText_1(builder_, level_ + 1);
    if (!result_) result_ = nodeText_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (maybeEmptyQuotedNodeText | maybeEmptyMdText) ALIAS+
  private static boolean nodeText_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nodeText_0_0(builder_, level_ + 1);
    result_ = result_ && nodeText_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // maybeEmptyQuotedNodeText | maybeEmptyMdText
  private static boolean nodeText_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_0_0")) return false;
    boolean result_;
    result_ = maybeEmptyQuotedNodeText(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyMdText(builder_, level_ + 1);
    return result_;
  }

  // ALIAS+
  private static boolean nodeText_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ALIAS);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ALIAS)) break;
      if (!empty_element_parsed_guard_(builder_, "nodeText_0_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (quotedNodeText | mdText) ALIAS*
  private static boolean nodeText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nodeText_1_0(builder_, level_ + 1);
    result_ = result_ && nodeText_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // quotedNodeText | mdText
  private static boolean nodeText_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_1_0")) return false;
    boolean result_;
    result_ = quotedNodeText(builder_, level_ + 1);
    if (!result_) result_ = mdText(builder_, level_ + 1);
    return result_;
  }

  // ALIAS*
  private static boolean nodeText_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ALIAS)) break;
      if (!empty_element_parsed_guard_(builder_, "nodeText_1_1", pos_)) break;
    }
    return true;
  }

  // ALIAS+
  private static boolean nodeText_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodeText_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ALIAS);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ALIAS)) break;
      if (!empty_element_parsed_guard_(builder_, "nodeText_2", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // complexNoteContent? END
  //   | COLON [simpleNoteContent]
  static boolean noteContent(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteContent")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noteContent_0(builder_, level_ + 1);
    if (!result_) result_ = noteContent_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // complexNoteContent? END
  private static boolean noteContent_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteContent_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noteContent_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // complexNoteContent?
  private static boolean noteContent_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteContent_0_0")) return false;
    complexNoteContent(builder_, level_ + 1);
    return true;
  }

  // COLON [simpleNoteContent]
  private static boolean noteContent_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteContent_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && noteContent_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [simpleNoteContent]
  private static boolean noteContent_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteContent_1_1")) return false;
    simpleNoteContent(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NOTE (RIGHT_OF | LEFT_OF) stateId
  public static boolean noteHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteHeader")) return false;
    if (!nextTokenIs(builder_, NOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NOTE);
    result_ = result_ && noteHeader_1(builder_, level_ + 1);
    result_ = result_ && stateId(builder_, level_ + 1);
    exit_section_(builder_, marker_, NOTE_HEADER, result_);
    return result_;
  }

  // RIGHT_OF | LEFT_OF
  private static boolean noteHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteHeader_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RIGHT_OF);
    if (!result_) result_ = consumeToken(builder_, LEFT_OF);
    return result_;
  }

  /* ********************************************************** */
  // simpleNoteContent [EOL] | EOL
  static boolean noteLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteLine")) return false;
    if (!nextTokenIs(builder_, "", EOL, NOTE_CONTENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noteLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // simpleNoteContent [EOL]
  private static boolean noteLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = simpleNoteContent(builder_, level_ + 1);
    result_ = result_ && noteLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean noteLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // noteLine [noteLines]
  static boolean noteLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteLines")) return false;
    if (!nextTokenIs(builder_, "", EOL, NOTE_CONTENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noteLine(builder_, level_ + 1);
    result_ = result_ && noteLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [noteLines]
  private static boolean noteLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteLines_1")) return false;
    noteLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NOTE (RIGHT_OF | LEFT_OF) complexIdentifier COLON complexMessage
  //   | NOTE OVER complexIdentifier [COMMA complexIdentifier] COLON complexMessage
  public static boolean noteStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteStatement")) return false;
    if (!nextTokenIs(builder_, NOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noteStatement_0(builder_, level_ + 1);
    if (!result_) result_ = noteStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, NOTE_STATEMENT, result_);
    return result_;
  }

  // NOTE (RIGHT_OF | LEFT_OF) complexIdentifier COLON complexMessage
  private static boolean noteStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteStatement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NOTE);
    result_ = result_ && noteStatement_0_1(builder_, level_ + 1);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && complexMessage(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // RIGHT_OF | LEFT_OF
  private static boolean noteStatement_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteStatement_0_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RIGHT_OF);
    if (!result_) result_ = consumeToken(builder_, LEFT_OF);
    return result_;
  }

  // NOTE OVER complexIdentifier [COMMA complexIdentifier] COLON complexMessage
  private static boolean noteStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, NOTE, OVER);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    result_ = result_ && noteStatement_1_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && complexMessage(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA complexIdentifier]
  private static boolean noteStatement_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteStatement_1_3")) return false;
    noteStatement_1_3_0(builder_, level_ + 1);
    return true;
  }

  // COMMA complexIdentifier
  private static boolean noteStatement_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "noteStatement_1_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // pieHeader EOL* [pieBody]
  //   | journeyHeader EOL* [journeyBody]
  //   | flowchartHeader EOL* [flowchartBody]
  //   | sequenceHeader EOL* [sequenceBody]
  //   | classDiagramHeader EOL* classBody
  //   | stateHeader EOL* [stateBody]
  //   | erHeader EOL* [erBody]
  //   | ganttHeader EOL* [ganttBody]
  //   | requirementDiagramHeader EOL* [requirementDiagramBody]
  //   | gitGraphHeader EOL* [gitGraphBody]
  //   | c4Header EOL* c4Body
  //   | mindmapHeader EOL* mindmapBody
  //   | timelineHeader EOL* [timelineBody]
  //   | quadrantHeader EOL* [quadrantBody]
  //   | xyChartHeader EOL* [xyChartBody]
  //   | blockDiagramHeader EOL* [blockDiagramBody]
  static boolean oldDiagram(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = oldDiagram_0(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_1(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_2(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_3(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_4(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_5(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_6(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_7(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_8(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_9(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_10(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_11(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_12(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_13(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_14(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram_15(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // pieHeader EOL* [pieBody]
  private static boolean oldDiagram_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = pieHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_0_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_0_1", pos_)) break;
    }
    return true;
  }

  // [pieBody]
  private static boolean oldDiagram_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_0_2")) return false;
    pieBody(builder_, level_ + 1);
    return true;
  }

  // journeyHeader EOL* [journeyBody]
  private static boolean oldDiagram_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = journeyHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_1_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_1_1", pos_)) break;
    }
    return true;
  }

  // [journeyBody]
  private static boolean oldDiagram_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_1_2")) return false;
    journeyBody(builder_, level_ + 1);
    return true;
  }

  // flowchartHeader EOL* [flowchartBody]
  private static boolean oldDiagram_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_2_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_2_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_2_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_2_1", pos_)) break;
    }
    return true;
  }

  // [flowchartBody]
  private static boolean oldDiagram_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_2_2")) return false;
    flowchartBody(builder_, level_ + 1);
    return true;
  }

  // sequenceHeader EOL* [sequenceBody]
  private static boolean oldDiagram_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sequenceHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_3_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_3_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_3_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_3_1", pos_)) break;
    }
    return true;
  }

  // [sequenceBody]
  private static boolean oldDiagram_3_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_3_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  // classDiagramHeader EOL* classBody
  private static boolean oldDiagram_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classDiagramHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_4_1(builder_, level_ + 1);
    result_ = result_ && classBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_4_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_4_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_4_1", pos_)) break;
    }
    return true;
  }

  // stateHeader EOL* [stateBody]
  private static boolean oldDiagram_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_5_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_5_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_5_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_5_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_5_1", pos_)) break;
    }
    return true;
  }

  // [stateBody]
  private static boolean oldDiagram_5_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_5_2")) return false;
    stateBody(builder_, level_ + 1);
    return true;
  }

  // erHeader EOL* [erBody]
  private static boolean oldDiagram_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = erHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_6_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_6_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_6_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_6_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_6_1", pos_)) break;
    }
    return true;
  }

  // [erBody]
  private static boolean oldDiagram_6_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_6_2")) return false;
    erBody(builder_, level_ + 1);
    return true;
  }

  // ganttHeader EOL* [ganttBody]
  private static boolean oldDiagram_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ganttHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_7_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_7_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_7_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_7_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_7_1", pos_)) break;
    }
    return true;
  }

  // [ganttBody]
  private static boolean oldDiagram_7_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_7_2")) return false;
    ganttBody(builder_, level_ + 1);
    return true;
  }

  // requirementDiagramHeader EOL* [requirementDiagramBody]
  private static boolean oldDiagram_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementDiagramHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_8_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_8_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_8_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_8_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_8_1", pos_)) break;
    }
    return true;
  }

  // [requirementDiagramBody]
  private static boolean oldDiagram_8_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_8_2")) return false;
    requirementDiagramBody(builder_, level_ + 1);
    return true;
  }

  // gitGraphHeader EOL* [gitGraphBody]
  private static boolean oldDiagram_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_9")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = gitGraphHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_9_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_9_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_9_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_9_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_9_1", pos_)) break;
    }
    return true;
  }

  // [gitGraphBody]
  private static boolean oldDiagram_9_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_9_2")) return false;
    gitGraphBody(builder_, level_ + 1);
    return true;
  }

  // c4Header EOL* c4Body
  private static boolean oldDiagram_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_10")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = c4Header(builder_, level_ + 1);
    result_ = result_ && oldDiagram_10_1(builder_, level_ + 1);
    result_ = result_ && c4Body(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_10_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_10_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_10_1", pos_)) break;
    }
    return true;
  }

  // mindmapHeader EOL* mindmapBody
  private static boolean oldDiagram_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_11")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mindmapHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_11_1(builder_, level_ + 1);
    result_ = result_ && mindmapBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_11_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_11_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_11_1", pos_)) break;
    }
    return true;
  }

  // timelineHeader EOL* [timelineBody]
  private static boolean oldDiagram_12(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_12")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_12_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_12_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_12_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_12_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_12_1", pos_)) break;
    }
    return true;
  }

  // [timelineBody]
  private static boolean oldDiagram_12_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_12_2")) return false;
    timelineBody(builder_, level_ + 1);
    return true;
  }

  // quadrantHeader EOL* [quadrantBody]
  private static boolean oldDiagram_13(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_13")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_13_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_13_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_13_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_13_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_13_1", pos_)) break;
    }
    return true;
  }

  // [quadrantBody]
  private static boolean oldDiagram_13_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_13_2")) return false;
    quadrantBody(builder_, level_ + 1);
    return true;
  }

  // xyChartHeader EOL* [xyChartBody]
  private static boolean oldDiagram_14(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_14")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xyChartHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_14_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_14_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_14_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_14_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_14_1", pos_)) break;
    }
    return true;
  }

  // [xyChartBody]
  private static boolean oldDiagram_14_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_14_2")) return false;
    xyChartBody(builder_, level_ + 1);
    return true;
  }

  // blockDiagramHeader EOL* [blockDiagramBody]
  private static boolean oldDiagram_15(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_15")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockDiagramHeader(builder_, level_ + 1);
    result_ = result_ && oldDiagram_15_1(builder_, level_ + 1);
    result_ = result_ && oldDiagram_15_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean oldDiagram_15_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_15_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "oldDiagram_15_1", pos_)) break;
    }
    return true;
  }

  // [blockDiagramBody]
  private static boolean oldDiagram_15_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldDiagram_15_2")) return false;
    blockDiagramBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // EOL oldStart | directive oldStart | oldDiagram
  static boolean oldStart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldStart")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = oldStart_0(builder_, level_ + 1);
    if (!result_) result_ = oldStart_1(builder_, level_ + 1);
    if (!result_) result_ = oldDiagram(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL oldStart
  private static boolean oldStart_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldStart_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    result_ = result_ && oldStart(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // directive oldStart
  private static boolean oldStart_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oldStart_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = directive(builder_, level_ + 1);
    result_ = result_ && oldStart(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPT [complexControlId]
  public static boolean optHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optHeader")) return false;
    if (!nextTokenIs(builder_, OPT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPT);
    result_ = result_ && optHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, OPT_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean optHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // optHeader EOL+ [sequenceBody] END
  public static boolean optStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optStatement")) return false;
    if (!nextTokenIs(builder_, OPT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = optHeader(builder_, level_ + 1);
    result_ = result_ && optStatement_1(builder_, level_ + 1);
    result_ = result_ && optStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, OPT_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean optStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "optStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean optStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // OPTION [complexControlId]
  public static boolean optionHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionHeader")) return false;
    if (!nextTokenIs(builder_, OPTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPTION);
    result_ = result_ && optionHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, OPTION_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean optionHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // optionHeader EOL+ [sequenceBody]
  static boolean optionSection(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSection")) return false;
    if (!nextTokenIs(builder_, OPTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = optionHeader(builder_, level_ + 1);
    result_ = result_ && optionSection_1(builder_, level_ + 1);
    result_ = result_ && optionSection_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL+
  private static boolean optionSection_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSection_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "optionSection_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean optionSection_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSection_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // optionSection [EOL* optionSections]
  static boolean optionSections(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSections")) return false;
    if (!nextTokenIs(builder_, OPTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = optionSection(builder_, level_ + 1);
    result_ = result_ && optionSections_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL* optionSections]
  private static boolean optionSections_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSections_1")) return false;
    optionSections_1_0(builder_, level_ + 1);
    return true;
  }

  // EOL* optionSections
  private static boolean optionSections_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSections_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = optionSections_1_0_0(builder_, level_ + 1);
    result_ = result_ && optionSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean optionSections_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionSections_1_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "optionSections_1_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // PAR [complexControlId]
  public static boolean parHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parHeader")) return false;
    if (!nextTokenIs(builder_, PAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PAR);
    result_ = result_ && parHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, PAR_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean parHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // PAR_OVER [complexControlId]
  public static boolean parOverHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverHeader")) return false;
    if (!nextTokenIs(builder_, PAR_OVER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PAR_OVER);
    result_ = result_ && parOverHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, PAR_OVER_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean parOverHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // parOverHeader EOL+ [sequenceBody] [EOL* andSections] END
  public static boolean parOverStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverStatement")) return false;
    if (!nextTokenIs(builder_, PAR_OVER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parOverHeader(builder_, level_ + 1);
    result_ = result_ && parOverStatement_1(builder_, level_ + 1);
    result_ = result_ && parOverStatement_2(builder_, level_ + 1);
    result_ = result_ && parOverStatement_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, PAR_OVER_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean parOverStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "parOverStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean parOverStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  // [EOL* andSections]
  private static boolean parOverStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverStatement_3")) return false;
    parOverStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // EOL* andSections
  private static boolean parOverStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parOverStatement_3_0_0(builder_, level_ + 1);
    result_ = result_ && andSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean parOverStatement_3_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parOverStatement_3_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "parOverStatement_3_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // parHeader EOL+ [sequenceBody] [EOL* andSections] END
  public static boolean parStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parStatement")) return false;
    if (!nextTokenIs(builder_, PAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parHeader(builder_, level_ + 1);
    result_ = result_ && parStatement_1(builder_, level_ + 1);
    result_ = result_ && parStatement_2(builder_, level_ + 1);
    result_ = result_ && parStatement_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, PAR_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean parStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "parStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean parStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  // [EOL* andSections]
  private static boolean parStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parStatement_3")) return false;
    parStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // EOL* andSections
  private static boolean parStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parStatement_3_0_0(builder_, level_ + 1);
    result_ = result_ && andSections(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean parStatement_3_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parStatement_3_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "parStatement_3_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // PARENT COLON DOUBLE_QUOTE commitIdValue DOUBLE_QUOTE
  public static boolean parentCommitIdAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parentCommitIdAttribute")) return false;
    if (!nextTokenIs(builder_, PARENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, PARENT, COLON, DOUBLE_QUOTE);
    result_ = result_ && commitIdValue(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, PARENT_COMMIT_ID_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // pieLines
  public static boolean pieBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PIE_BODY, "<pie body>");
    result_ = pieLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // string COLON VALUE
  public static boolean pieDataStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieDataStatement")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = string(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, COLON, VALUE);
    exit_section_(builder_, marker_, PIE_DATA_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // PIE [showDataRec]
  public static boolean pieHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieHeader")) return false;
    if (!nextTokenIs(builder_, PIE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIE);
    result_ = result_ && pieHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, PIE_HEADER, result_);
    return result_;
  }

  // [showDataRec]
  private static boolean pieHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieHeader_1")) return false;
    showDataRec(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // pieStatement [EOL] | EOL
  static boolean pieLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = pieLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // pieStatement [EOL]
  private static boolean pieLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = pieStatement(builder_, level_ + 1);
    result_ = result_ && pieLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean pieLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // pieLine [pieLines]
  static boolean pieLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = pieLine(builder_, level_ + 1);
    result_ = result_ && pieLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [pieLines]
  private static boolean pieLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieLines_1")) return false;
    pieLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // pieDataStatement
  //   | titleStatement
  //   | directive
  //   | accStatement
  static boolean pieStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pieStatement")) return false;
    boolean result_;
    result_ = pieDataStatement(builder_, level_ + 1);
    if (!result_) result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_SQUARE (NUM (COMMA NUM)*) CLOSE_SQUARE
  public static boolean plotData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "plotData")) return false;
    if (!nextTokenIs(builder_, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_SQUARE);
    result_ = result_ && plotData_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, PLOT_DATA, result_);
    return result_;
  }

  // NUM (COMMA NUM)*
  private static boolean plotData_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "plotData_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NUM);
    result_ = result_ && plotData_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA NUM)*
  private static boolean plotData_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "plotData_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!plotData_1_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "plotData_1_1", pos_)) break;
    }
    return true;
  }

  // COMMA NUM
  private static boolean plotData_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "plotData_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, NUM);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_SQUARE NUM COMMA NUM CLOSE_SQUARE
  public static boolean point(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "point")) return false;
    if (!nextTokenIs(builder_, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, OPEN_SQUARE, NUM, COMMA, NUM, CLOSE_SQUARE);
    exit_section_(builder_, marker_, POINT, result_);
    return result_;
  }

  /* ********************************************************** */
  // quadrantComplexText COLON point
  public static boolean pointStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pointStatement")) return false;
    if (!nextTokenIs(builder_, "<point statement>", DOUBLE_QUOTE, QUADRANT_TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, POINT_STATEMENT, "<point statement>");
    result_ = quadrantComplexText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && point(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // quadrantLines
  public static boolean quadrantBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, QUADRANT_BODY, "<quadrant body>");
    result_ = quadrantLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (maybeEmptyString | maybeEmptyMdText) QUADRANT_TEXT+
  //   | (string | mdText) QUADRANT_TEXT*
  //   | QUADRANT_TEXT+
  public static boolean quadrantComplexText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText")) return false;
    if (!nextTokenIs(builder_, "<quadrant complex text>", DOUBLE_QUOTE, QUADRANT_TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, QUADRANT_COMPLEX_TEXT, "<quadrant complex text>");
    result_ = quadrantComplexText_0(builder_, level_ + 1);
    if (!result_) result_ = quadrantComplexText_1(builder_, level_ + 1);
    if (!result_) result_ = quadrantComplexText_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (maybeEmptyString | maybeEmptyMdText) QUADRANT_TEXT+
  private static boolean quadrantComplexText_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantComplexText_0_0(builder_, level_ + 1);
    result_ = result_ && quadrantComplexText_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // maybeEmptyString | maybeEmptyMdText
  private static boolean quadrantComplexText_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_0_0")) return false;
    boolean result_;
    result_ = maybeEmptyString(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyMdText(builder_, level_ + 1);
    return result_;
  }

  // QUADRANT_TEXT+
  private static boolean quadrantComplexText_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUADRANT_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, QUADRANT_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "quadrantComplexText_0_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (string | mdText) QUADRANT_TEXT*
  private static boolean quadrantComplexText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantComplexText_1_0(builder_, level_ + 1);
    result_ = result_ && quadrantComplexText_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // string | mdText
  private static boolean quadrantComplexText_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_1_0")) return false;
    boolean result_;
    result_ = string(builder_, level_ + 1);
    if (!result_) result_ = mdText(builder_, level_ + 1);
    return result_;
  }

  // QUADRANT_TEXT*
  private static boolean quadrantComplexText_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, QUADRANT_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "quadrantComplexText_1_1", pos_)) break;
    }
    return true;
  }

  // QUADRANT_TEXT+
  private static boolean quadrantComplexText_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantComplexText_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUADRANT_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, QUADRANT_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "quadrantComplexText_2", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // QUADRANT quadrantComplexText
  public static boolean quadrantDetailsStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantDetailsStatement")) return false;
    if (!nextTokenIs(builder_, QUADRANT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUADRANT);
    result_ = result_ && quadrantComplexText(builder_, level_ + 1);
    exit_section_(builder_, marker_, QUADRANT_DETAILS_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // QUADRANT_CHART
  public static boolean quadrantHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantHeader")) return false;
    if (!nextTokenIs(builder_, QUADRANT_CHART)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUADRANT_CHART);
    exit_section_(builder_, marker_, QUADRANT_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // quadrantStatement [separator] | separator
  static boolean quadrantLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // quadrantStatement [separator]
  private static boolean quadrantLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantStatement(builder_, level_ + 1);
    result_ = result_ && quadrantLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean quadrantLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // quadrantLine [quadrantLines]
  static boolean quadrantLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quadrantLine(builder_, level_ + 1);
    result_ = result_ && quadrantLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [quadrantLines]
  private static boolean quadrantLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantLines_1")) return false;
    quadrantLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // titleStatement
  //   | directive
  //   | accStatement
  //   | pointStatement
  //   | axisDetailsStatement
  //   | quadrantDetailsStatement
  static boolean quadrantStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quadrantStatement")) return false;
    boolean result_;
    result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    if (!result_) result_ = pointStatement(builder_, level_ + 1);
    if (!result_) result_ = axisDetailsStatement(builder_, level_ + 1);
    if (!result_) result_ = quadrantDetailsStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // STRING_VALUE
  public static boolean quotedBranchIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedBranchIdentifier")) return false;
    if (!nextTokenIs(builder_, STRING_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING_VALUE);
    exit_section_(builder_, marker_, QUOTED_BRANCH_IDENTIFIER, result_);
    return result_;
  }

  /* ********************************************************** */
  // STRING_VALUE
  public static boolean quotedClassIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedClassIdentifier")) return false;
    if (!nextTokenIs(builder_, STRING_VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING_VALUE);
    exit_section_(builder_, marker_, QUOTED_CLASS_IDENTIFIER, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE LINK_TEXT+ DOUBLE_QUOTE
  static boolean quotedLinkText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedLinkText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && quotedLinkText_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LINK_TEXT+
  private static boolean quotedLinkText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedLinkText_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LINK_TEXT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LINK_TEXT)) break;
      if (!empty_element_parsed_guard_(builder_, "quotedLinkText_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE ALIAS+ DOUBLE_QUOTE
  static boolean quotedNodeText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedNodeText")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && quotedNodeText_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ALIAS+
  private static boolean quotedNodeText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedNodeText_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ALIAS);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, ALIAS)) break;
      if (!empty_element_parsed_guard_(builder_, "quotedNodeText_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE quotedSankeyFieldValue DOUBLE_QUOTE
  public static boolean quotedSankeyField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedSankeyField")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_QUOTE);
    result_ = result_ && quotedSankeyFieldValue(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, QUOTED_SANKEY_FIELD, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE DOUBLE_QUOTE quotedSankeyFieldValue DOUBLE_QUOTE DOUBLE_QUOTE
  public static boolean quotedSankeyFieldInnerValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedSankeyFieldInnerValue")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOUBLE_QUOTE, DOUBLE_QUOTE);
    result_ = result_ && quotedSankeyFieldValue(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, DOUBLE_QUOTE, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, QUOTED_SANKEY_FIELD_INNER_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // ( quotedSankeyFieldInnerValue | (complexSankeyText | COMMA)+ )+
  public static boolean quotedSankeyFieldValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedSankeyFieldValue")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, QUOTED_SANKEY_FIELD_VALUE, "<quoted sankey field value>");
    result_ = quotedSankeyFieldValue_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!quotedSankeyFieldValue_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "quotedSankeyFieldValue", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // quotedSankeyFieldInnerValue | (complexSankeyText | COMMA)+
  private static boolean quotedSankeyFieldValue_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedSankeyFieldValue_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quotedSankeyFieldInnerValue(builder_, level_ + 1);
    if (!result_) result_ = quotedSankeyFieldValue_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (complexSankeyText | COMMA)+
  private static boolean quotedSankeyFieldValue_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedSankeyFieldValue_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = quotedSankeyFieldValue_0_1_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!quotedSankeyFieldValue_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "quotedSankeyFieldValue_0_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // complexSankeyText | COMMA
  private static boolean quotedSankeyFieldValue_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "quotedSankeyFieldValue_0_1_0")) return false;
    boolean result_;
    result_ = complexSankeyText(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    return result_;
  }

  /* ********************************************************** */
  // complexNamedData [COMMA recNamedData]
  static boolean recNamedData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recNamedData")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexNamedData(builder_, level_ + 1);
    result_ = result_ && recNamedData_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA recNamedData]
  private static boolean recNamedData_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recNamedData_1")) return false;
    recNamedData_1_0(builder_, level_ + 1);
    return true;
  }

  // COMMA recNamedData
  private static boolean recNamedData_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recNamedData_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && recNamedData(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // complexTaskData [COMMA recTaskData]
  static boolean recTaskData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recTaskData")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexTaskData(builder_, level_ + 1);
    result_ = result_ && recTaskData_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA recTaskData]
  private static boolean recTaskData_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recTaskData_1")) return false;
    recTaskData_1_0(builder_, level_ + 1);
    return true;
  }

  // COMMA recTaskData
  private static boolean recTaskData_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recTaskData_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && recTaskData(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // RECT [complexControlId]
  public static boolean rectHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rectHeader")) return false;
    if (!nextTokenIs(builder_, RECT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, RECT);
    result_ = result_ && rectHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, RECT_HEADER, result_);
    return result_;
  }

  // [complexControlId]
  private static boolean rectHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rectHeader_1")) return false;
    complexControlId(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // rectHeader EOL+ [sequenceBody] END
  public static boolean rectStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rectStatement")) return false;
    if (!nextTokenIs(builder_, RECT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = rectHeader(builder_, level_ + 1);
    result_ = result_ && rectStatement_1(builder_, level_ + 1);
    result_ = result_ && rectStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, RECT_STATEMENT, result_);
    return result_;
  }

  // EOL+
  private static boolean rectStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rectStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "rectStatement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sequenceBody]
  private static boolean rectStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rectStatement_2")) return false;
    sequenceBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // [cardinality] [relationTypeLeft] lineType [relationTypeRight] [cardinality]
  public static boolean relation(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relation")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATION, "<relation>");
    result_ = relation_0(builder_, level_ + 1);
    result_ = result_ && relation_1(builder_, level_ + 1);
    result_ = result_ && lineType(builder_, level_ + 1);
    result_ = result_ && relation_3(builder_, level_ + 1);
    result_ = result_ && relation_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [cardinality]
  private static boolean relation_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relation_0")) return false;
    cardinality(builder_, level_ + 1);
    return true;
  }

  // [relationTypeLeft]
  private static boolean relation_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relation_1")) return false;
    relationTypeLeft(builder_, level_ + 1);
    return true;
  }

  // [relationTypeRight]
  private static boolean relation_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relation_3")) return false;
    relationTypeRight(builder_, level_ + 1);
    return true;
  }

  // [cardinality]
  private static boolean relation_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relation_4")) return false;
    cardinality(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // leftId relation rightId [COLON complexLabel]
  public static boolean relationStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationStatement")) return false;
    if (!nextTokenIs(builder_, "<relation statement>", BACK_QUOTE, CLASS_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATION_STATEMENT, "<relation statement>");
    result_ = leftId(builder_, level_ + 1);
    result_ = result_ && relation(builder_, level_ + 1);
    result_ = result_ && rightId(builder_, level_ + 1);
    result_ = result_ && relationStatement_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [COLON complexLabel]
  private static boolean relationStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationStatement_3")) return false;
    relationStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON complexLabel
  private static boolean relationStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && complexLabel(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // AGGREGATION
  //   | EXTENSION_START
  //   | COMPOSITION
  //   | DEPENDENCY_START
  //   | LOLLIPOP
  public static boolean relationTypeLeft(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationTypeLeft")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATION_TYPE_LEFT, "<relation type left>");
    result_ = consumeToken(builder_, AGGREGATION);
    if (!result_) result_ = consumeToken(builder_, EXTENSION_START);
    if (!result_) result_ = consumeToken(builder_, COMPOSITION);
    if (!result_) result_ = consumeToken(builder_, DEPENDENCY_START);
    if (!result_) result_ = consumeToken(builder_, LOLLIPOP);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // AGGREGATION
  //   | EXTENSION_END
  //   | COMPOSITION
  //   | DEPENDENCY_END
  //   | LOLLIPOP
  public static boolean relationTypeRight(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationTypeRight")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATION_TYPE_RIGHT, "<relation type right>");
    result_ = consumeToken(builder_, AGGREGATION);
    if (!result_) result_ = consumeToken(builder_, EXTENSION_END);
    if (!result_) result_ = consumeToken(builder_, COMPOSITION);
    if (!result_) result_ = consumeToken(builder_, DEPENDENCY_END);
    if (!result_) result_ = consumeToken(builder_, LOLLIPOP);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // erCardinality (IDENTIFYING | NON_IDENTIFYING) erCardinality
  public static boolean relationship(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationship")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATIONSHIP, "<relationship>");
    result_ = erCardinality(builder_, level_ + 1);
    result_ = result_ && relationship_1(builder_, level_ + 1);
    result_ = result_ && erCardinality(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // IDENTIFYING | NON_IDENTIFYING
  private static boolean relationship_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationship_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFYING);
    if (!result_) result_ = consumeToken(builder_, NON_IDENTIFYING);
    return result_;
  }

  /* ********************************************************** */
  // identifier ARROW_LEFT reqRelationship REQ_LINE identifier
  //   | identifier REQ_LINE reqRelationship ARROW_RIGHT identifier
  public static boolean relationshipDef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationshipDef")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = relationshipDef_0(builder_, level_ + 1);
    if (!result_) result_ = relationshipDef_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, RELATIONSHIP_DEF, result_);
    return result_;
  }

  // identifier ARROW_LEFT reqRelationship REQ_LINE identifier
  private static boolean relationshipDef_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationshipDef_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = identifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW_LEFT);
    result_ = result_ && reqRelationship(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, REQ_LINE);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // identifier REQ_LINE reqRelationship ARROW_RIGHT identifier
  private static boolean relationshipDef_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationshipDef_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = identifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, REQ_LINE);
    result_ = result_ && reqRelationship(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW_RIGHT);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONTAINS | COPIES | DERIVES | SATISFIES | VERIFIES | REFINES | TRACES
  public static boolean reqRelationship(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "reqRelationship")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REQ_RELATIONSHIP, "<req relationship>");
    result_ = consumeToken(builder_, CONTAINS);
    if (!result_) result_ = consumeToken(builder_, COPIES);
    if (!result_) result_ = consumeToken(builder_, DERIVES);
    if (!result_) result_ = consumeToken(builder_, SATISFIES);
    if (!result_) result_ = consumeToken(builder_, VERIFIES);
    if (!result_) result_ = consumeToken(builder_, REFINES);
    if (!result_) result_ = consumeToken(builder_, TRACES);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL+ requirementBlockLines? CLOSE_CURLY
  public static boolean requirementBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && requirementBlock_1(builder_, level_ + 1);
    result_ = result_ && requirementBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, REQUIREMENT_BLOCK, result_);
    return result_;
  }

  // EOL+
  private static boolean requirementBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlock_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "requirementBlock_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // requirementBlockLines?
  private static boolean requirementBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlock_2")) return false;
    requirementBlockLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // requirementBlockStatement [EOL] | EOL
  static boolean requirementBlockLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlockLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementBlockLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // requirementBlockStatement [EOL]
  private static boolean requirementBlockLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlockLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementBlockStatement(builder_, level_ + 1);
    result_ = result_ && requirementBlockLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean requirementBlockLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlockLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // requirementBlockLine [requirementBlockLines]
  static boolean requirementBlockLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlockLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementBlockLine(builder_, level_ + 1);
    result_ = result_ && requirementBlockLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [requirementBlockLines]
  private static boolean requirementBlockLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlockLines_1")) return false;
    requirementBlockLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // requirementIdAttribute
  //   | requirementTextAttribute
  //   | requirementRiskAttribute
  //   | requirementVerifyMethodAttribute
  static boolean requirementBlockStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementBlockStatement")) return false;
    boolean result_;
    result_ = requirementIdAttribute(builder_, level_ + 1);
    if (!result_) result_ = requirementTextAttribute(builder_, level_ + 1);
    if (!result_) result_ = requirementRiskAttribute(builder_, level_ + 1);
    if (!result_) result_ = requirementVerifyMethodAttribute(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // requirementHeader requirementBlock
  public static boolean requirementDef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementDef")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REQUIREMENT_DEF, "<requirement def>");
    result_ = requirementHeader(builder_, level_ + 1);
    result_ = result_ && requirementBlock(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // requirementLines
  public static boolean requirementDiagramBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementDiagramBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REQUIREMENT_DIAGRAM_BODY, "<requirement diagram body>");
    result_ = requirementLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // REQUIREMENT_DIAGRAM
  public static boolean requirementDiagramHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementDiagramHeader")) return false;
    if (!nextTokenIs(builder_, REQUIREMENT_DIAGRAM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REQUIREMENT_DIAGRAM);
    exit_section_(builder_, marker_, REQUIREMENT_DIAGRAM_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // requirementType identifier
  public static boolean requirementHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementHeader")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REQUIREMENT_HEADER, "<requirement header>");
    result_ = requirementType(builder_, level_ + 1);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ID_KEYWORD COLON requirementValue
  public static boolean requirementIdAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementIdAttribute")) return false;
    if (!nextTokenIs(builder_, ID_KEYWORD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ID_KEYWORD, COLON);
    result_ = result_ && requirementValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, REQUIREMENT_ID_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // requirementStatement [EOL] | EOL
  static boolean requirementLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // requirementStatement [EOL]
  private static boolean requirementLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementStatement(builder_, level_ + 1);
    result_ = result_ && requirementLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean requirementLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // requirementLine [requirementLines]
  static boolean requirementLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = requirementLine(builder_, level_ + 1);
    result_ = result_ && requirementLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [requirementLines]
  private static boolean requirementLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementLines_1")) return false;
    requirementLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // RISK COLON riskLevel
  public static boolean requirementRiskAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementRiskAttribute")) return false;
    if (!nextTokenIs(builder_, RISK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, RISK, COLON);
    result_ = result_ && riskLevel(builder_, level_ + 1);
    exit_section_(builder_, marker_, REQUIREMENT_RISK_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // requirementDef
  //   | elementDef
  //   | relationshipDef
  //   | directive
  //   | accStatement
  static boolean requirementStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementStatement")) return false;
    boolean result_;
    result_ = requirementDef(builder_, level_ + 1);
    if (!result_) result_ = elementDef(builder_, level_ + 1);
    if (!result_) result_ = relationshipDef(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // TEXT COLON requirementValue
  public static boolean requirementTextAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementTextAttribute")) return false;
    if (!nextTokenIs(builder_, TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, TEXT, COLON);
    result_ = result_ && requirementValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, REQUIREMENT_TEXT_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // REQUIREMENT
  //   | FUNCTIONAL_REQUIREMENT
  //   | INTERFACE_REQUIREMENT
  //   | PERFORMANCE_REQUIREMENT
  //   | PHYSICAL_REQUIREMENT
  //   | DESIGN_CONSTRAINT
  public static boolean requirementType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REQUIREMENT_TYPE, "<requirement type>");
    result_ = consumeToken(builder_, REQUIREMENT);
    if (!result_) result_ = consumeToken(builder_, FUNCTIONAL_REQUIREMENT);
    if (!result_) result_ = consumeToken(builder_, INTERFACE_REQUIREMENT);
    if (!result_) result_ = consumeToken(builder_, PERFORMANCE_REQUIREMENT);
    if (!result_) result_ = consumeToken(builder_, PHYSICAL_REQUIREMENT);
    if (!result_) result_ = consumeToken(builder_, DESIGN_CONSTRAINT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // complexLabel | string
  static boolean requirementValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementValue")) return false;
    if (!nextTokenIs(builder_, "", DOUBLE_QUOTE, LABEL)) return false;
    boolean result_;
    result_ = complexLabel(builder_, level_ + 1);
    if (!result_) result_ = string(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // VERIFY_METHOD COLON verifyType
  public static boolean requirementVerifyMethodAttribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "requirementVerifyMethodAttribute")) return false;
    if (!nextTokenIs(builder_, VERIFY_METHOD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, VERIFY_METHOD, COLON);
    result_ = result_ && verifyType(builder_, level_ + 1);
    exit_section_(builder_, marker_, REQUIREMENT_VERIFY_METHOD_ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // classDiagramIdentifier [generic]
  public static boolean rightId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rightId")) return false;
    if (!nextTokenIs(builder_, "<right id>", BACK_QUOTE, CLASS_ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RIGHT_ID, "<right id>");
    result_ = classDiagramIdentifier(builder_, level_ + 1);
    result_ = result_ && rightId_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [generic]
  private static boolean rightId_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rightId_1")) return false;
    generic(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LOW | MEDIUM | HIGH
  public static boolean riskLevel(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "riskLevel")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RISK_LEVEL, "<risk level>");
    result_ = consumeToken(builder_, LOW);
    if (!result_) result_ = consumeToken(builder_, MEDIUM);
    if (!result_) result_ = consumeToken(builder_, HIGH);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // sankeyLines
  public static boolean sankeyBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SANKEY_BODY, "<sankey body>");
    result_ = sankeyLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // quotedSankeyField | complexSankeyText
  static boolean sankeyField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyField")) return false;
    if (!nextTokenIs(builder_, "", DOUBLE_QUOTE, SANKEY_TEXT)) return false;
    boolean result_;
    result_ = quotedSankeyField(builder_, level_ + 1);
    if (!result_) result_ = complexSankeyText(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // SANKEY
  public static boolean sankeyHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyHeader")) return false;
    if (!nextTokenIs(builder_, SANKEY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SANKEY);
    exit_section_(builder_, marker_, SANKEY_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // sankeyStatement [EOL] | EOL
  static boolean sankeyLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sankeyLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sankeyStatement [EOL]
  private static boolean sankeyLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sankeyStatement(builder_, level_ + 1);
    result_ = result_ && sankeyLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean sankeyLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // sankeyLine [sankeyLines]
  static boolean sankeyLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sankeyLine(builder_, level_ + 1);
    result_ = result_ && sankeyLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [sankeyLines]
  private static boolean sankeyLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyLines_1")) return false;
    sankeyLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // identifyingSankeyField COMMA identifyingSankeyField COMMA sankeyField
  public static boolean sankeyRecordStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sankeyRecordStatement")) return false;
    if (!nextTokenIs(builder_, "<sankey record statement>", DOUBLE_QUOTE, SANKEY_TEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SANKEY_RECORD_STATEMENT, "<sankey record statement>");
    result_ = identifyingSankeyField(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && identifyingSankeyField(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && sankeyField(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // sankeyRecordStatement
  static boolean sankeyStatement(PsiBuilder builder_, int level_) {
    return sankeyRecordStatement(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // recTaskData
  public static boolean sectionTaskData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sectionTaskData")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SECTION_TASK_DATA, "<section task data>");
    result_ = recTaskData(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // EOL | SEMICOLON
  static boolean separator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "separator")) return false;
    if (!nextTokenIs(builder_, "", EOL, SEMICOLON)) return false;
    boolean result_;
    result_ = consumeToken(builder_, EOL);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    return result_;
  }

  /* ********************************************************** */
  // sequenceLines
  public static boolean sequenceBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SEQUENCE_BODY, "<sequence body>");
    result_ = sequenceLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SEQUENCE
  public static boolean sequenceHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceHeader")) return false;
    if (!nextTokenIs(builder_, SEQUENCE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEQUENCE);
    exit_section_(builder_, marker_, SEQUENCE_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // sequenceStatement [separator] | separator
  static boolean sequenceLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sequenceLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sequenceStatement [separator]
  private static boolean sequenceLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sequenceStatement(builder_, level_ + 1);
    result_ = result_ && sequenceLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean sequenceLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (sequenceLine | IGNORED) [sequenceLines]
  static boolean sequenceLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = sequenceLines_0(builder_, level_ + 1);
    result_ = result_ && sequenceLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sequenceLine | IGNORED
  private static boolean sequenceLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceLines_0")) return false;
    boolean result_;
    result_ = sequenceLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [sequenceLines]
  private static boolean sequenceLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceLines_1")) return false;
    sequenceLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // actorStatement
  //   | signalStatement
  //   | autonumberStatement
  //   | activateStatement
  //   | deactivateStatement
  //   | noteStatement
  //   | linksStatement
  //   | linkStatement
  //   | loopStatement
  //   | rectStatement
  //   | optStatement
  //   | breakStatement
  //   | boxStatement
  //   | altStatement
  //   | parStatement
  //   | parOverStatement
  //   | criticalStatement
  //   | directive
  //   | accStatement
  //   | titleStatement
  static boolean sequenceStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sequenceStatement")) return false;
    boolean result_;
    result_ = actorStatement(builder_, level_ + 1);
    if (!result_) result_ = signalStatement(builder_, level_ + 1);
    if (!result_) result_ = autonumberStatement(builder_, level_ + 1);
    if (!result_) result_ = activateStatement(builder_, level_ + 1);
    if (!result_) result_ = deactivateStatement(builder_, level_ + 1);
    if (!result_) result_ = noteStatement(builder_, level_ + 1);
    if (!result_) result_ = linksStatement(builder_, level_ + 1);
    if (!result_) result_ = linkStatement(builder_, level_ + 1);
    if (!result_) result_ = loopStatement(builder_, level_ + 1);
    if (!result_) result_ = rectStatement(builder_, level_ + 1);
    if (!result_) result_ = optStatement(builder_, level_ + 1);
    if (!result_) result_ = breakStatement(builder_, level_ + 1);
    if (!result_) result_ = boxStatement(builder_, level_ + 1);
    if (!result_) result_ = altStatement(builder_, level_ + 1);
    if (!result_) result_ = parStatement(builder_, level_ + 1);
    if (!result_) result_ = parOverStatement(builder_, level_ + 1);
    if (!result_) result_ = criticalStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    if (!result_) result_ = titleStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // EOL showDataRec | SHOW_DATA
  static boolean showDataRec(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "showDataRec")) return false;
    if (!nextTokenIs(builder_, "", EOL, SHOW_DATA)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = showDataRec_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SHOW_DATA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL showDataRec
  private static boolean showDataRec_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "showDataRec_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EOL);
    result_ = result_ && showDataRec(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // signalType [PLUS | MINUS]
  public static boolean signal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "signal")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SIGNAL, "<signal>");
    result_ = signalType(builder_, level_ + 1);
    result_ = result_ && signal_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [PLUS | MINUS]
  private static boolean signal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "signal_1")) return false;
    signal_1_0(builder_, level_ + 1);
    return true;
  }

  // PLUS | MINUS
  private static boolean signal_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "signal_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    return result_;
  }

  /* ********************************************************** */
  // complexIdentifier signal complexIdentifier COLON complexMessage
  public static boolean signalStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "signalStatement")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexIdentifier(builder_, level_ + 1);
    result_ = result_ && signal(builder_, level_ + 1);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && complexMessage(builder_, level_ + 1);
    exit_section_(builder_, marker_, SIGNAL_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // SOLID_OPEN_ARROW
  //   | DOTTED_OPEN_ARROW
  //   | SOLID_ARROW
  //   | DOTTED_ARROW
  //   | SOLID_CROSS
  //   | DOTTED_CROSS
  //   | SOLID_POINT
  //   | DOTTED_POINT
  public static boolean signalType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "signalType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SIGNAL_TYPE, "<signal type>");
    result_ = consumeToken(builder_, SOLID_OPEN_ARROW);
    if (!result_) result_ = consumeToken(builder_, DOTTED_OPEN_ARROW);
    if (!result_) result_ = consumeToken(builder_, SOLID_ARROW);
    if (!result_) result_ = consumeToken(builder_, DOTTED_ARROW);
    if (!result_) result_ = consumeToken(builder_, SOLID_CROSS);
    if (!result_) result_ = consumeToken(builder_, DOTTED_CROSS);
    if (!result_) result_ = consumeToken(builder_, SOLID_POINT);
    if (!result_) result_ = consumeToken(builder_, DOTTED_POINT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // NOTE_CONTENT+
  public static boolean simpleNoteContent(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleNoteContent")) return false;
    if (!nextTokenIs(builder_, NOTE_CONTENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NOTE_CONTENT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NOTE_CONTENT)) break;
      if (!empty_element_parsed_guard_(builder_, "simpleNoteContent", pos_)) break;
    }
    exit_section_(builder_, marker_, SIMPLE_NOTE_CONTENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // spaceStatementInner
  public static boolean spaceStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "spaceStatement")) return false;
    if (!nextTokenIs(builder_, SPACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = spaceStatementInner(builder_, level_ + 1);
    exit_section_(builder_, marker_, SPACE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // SPACE [blockSize]
  public static boolean spaceStatementInner(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "spaceStatementInner")) return false;
    if (!nextTokenIs(builder_, SPACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SPACE);
    result_ = result_ && spaceStatementInner_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, SPACE_STATEMENT_INNER, result_);
    return result_;
  }

  // [blockSize]
  private static boolean spaceStatementInner_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "spaceStatementInner_1")) return false;
    blockSize(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // OPEN_SQUARE STAR CLOSE_SQUARE
  public static boolean specialState(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "specialState")) return false;
    if (!nextTokenIs(builder_, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, OPEN_SQUARE, STAR, CLOSE_SQUARE);
    exit_section_(builder_, marker_, SPECIAL_STATE, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_CURLY EOL* innerStateBody? CLOSE_CURLY
  public static boolean stateBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateBlock")) return false;
    if (!nextTokenIs(builder_, OPEN_CURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_CURLY);
    result_ = result_ && stateBlock_1(builder_, level_ + 1);
    result_ = result_ && stateBlock_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_CURLY);
    exit_section_(builder_, marker_, STATE_BLOCK, result_);
    return result_;
  }

  // EOL*
  private static boolean stateBlock_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateBlock_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "stateBlock_1", pos_)) break;
    }
    return true;
  }

  // innerStateBody?
  private static boolean stateBlock_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateBlock_2")) return false;
    innerStateBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // stateLines
  public static boolean stateBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATE_BODY, "<state body>");
    result_ = stateLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // CLASS_DEF (CLASS_DEF_ID | DEFAULT) styleOptions
  public static boolean stateClassDefStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateClassDefStatement")) return false;
    if (!nextTokenIs(builder_, CLASS_DEF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS_DEF);
    result_ = result_ && stateClassDefStatement_1(builder_, level_ + 1);
    result_ = result_ && styleOptions(builder_, level_ + 1);
    exit_section_(builder_, marker_, STATE_CLASS_DEF_STATEMENT, result_);
    return result_;
  }

  // CLASS_DEF_ID | DEFAULT
  private static boolean stateClassDefStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateClassDefStatement_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, CLASS_DEF_ID);
    if (!result_) result_ = consumeToken(builder_, DEFAULT);
    return result_;
  }

  /* ********************************************************** */
  // stateId COLON complexLabel
  //   | STATE description AS stateId
  //   | stateDeclarationHeader annotation
  public static boolean stateDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATE_DECLARATION, "<state declaration>");
    result_ = stateDeclaration_0(builder_, level_ + 1);
    if (!result_) result_ = stateDeclaration_1(builder_, level_ + 1);
    if (!result_) result_ = stateDeclaration_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // stateId COLON complexLabel
  private static boolean stateDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateDeclaration_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateId(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && complexLabel(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STATE description AS stateId
  private static boolean stateDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateDeclaration_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STATE);
    result_ = result_ && description(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, AS);
    result_ = result_ && stateId(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // stateDeclarationHeader annotation
  private static boolean stateDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateDeclaration_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateDeclarationHeader(builder_, level_ + 1);
    result_ = result_ && annotation(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // STATE stateId
  public static boolean stateDeclarationHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateDeclarationHeader")) return false;
    if (!nextTokenIs(builder_, STATE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STATE);
    result_ = result_ && stateId(builder_, level_ + 1);
    exit_section_(builder_, marker_, STATE_DECLARATION_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // stateDeclaration
  //   | compositeStateDeclaration
  //   | stateRelationStatement
  //   | stateId
  //   | stateNote
  //   | stateClassDefStatement
  //   | cssClassStatement
  //   | directionStatement
  //   | directive
  //   | accStatement
  static boolean stateDiagramStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateDiagramStatement")) return false;
    boolean result_;
    result_ = stateDeclaration(builder_, level_ + 1);
    if (!result_) result_ = compositeStateDeclaration(builder_, level_ + 1);
    if (!result_) result_ = stateRelationStatement(builder_, level_ + 1);
    if (!result_) result_ = stateId(builder_, level_ + 1);
    if (!result_) result_ = stateNote(builder_, level_ + 1);
    if (!result_) result_ = stateClassDefStatement(builder_, level_ + 1);
    if (!result_) result_ = cssClassStatement(builder_, level_ + 1);
    if (!result_) result_ = directionStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // STATE_DIAGRAM
  public static boolean stateHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateHeader")) return false;
    if (!nextTokenIs(builder_, STATE_DIAGRAM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STATE_DIAGRAM);
    exit_section_(builder_, marker_, STATE_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // identifier [STYLE_SEPARATOR identifier] | specialState [STYLE_SEPARATOR identifier]
  public static boolean stateId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId")) return false;
    if (!nextTokenIs(builder_, "<state id>", ID, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATE_ID, "<state id>");
    result_ = stateId_0(builder_, level_ + 1);
    if (!result_) result_ = stateId_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // identifier [STYLE_SEPARATOR identifier]
  private static boolean stateId_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = identifier(builder_, level_ + 1);
    result_ = result_ && stateId_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [STYLE_SEPARATOR identifier]
  private static boolean stateId_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId_0_1")) return false;
    stateId_0_1_0(builder_, level_ + 1);
    return true;
  }

  // STYLE_SEPARATOR identifier
  private static boolean stateId_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STYLE_SEPARATOR);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // specialState [STYLE_SEPARATOR identifier]
  private static boolean stateId_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = specialState(builder_, level_ + 1);
    result_ = result_ && stateId_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [STYLE_SEPARATOR identifier]
  private static boolean stateId_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId_1_1")) return false;
    stateId_1_1_0(builder_, level_ + 1);
    return true;
  }

  // STYLE_SEPARATOR identifier
  private static boolean stateId_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateId_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STYLE_SEPARATOR);
    result_ = result_ && identifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // stateDiagramStatement [EOL] | EOL
  static boolean stateLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // stateDiagramStatement [EOL]
  private static boolean stateLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateDiagramStatement(builder_, level_ + 1);
    result_ = result_ && stateLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean stateLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // stateLine [stateLines]
  static boolean stateLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stateLine(builder_, level_ + 1);
    result_ = result_ && stateLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [stateLines]
  private static boolean stateLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateLines_1")) return false;
    stateLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // noteHeader EOL* noteContent
  public static boolean stateNote(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateNote")) return false;
    if (!nextTokenIs(builder_, NOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noteHeader(builder_, level_ + 1);
    result_ = result_ && stateNote_1(builder_, level_ + 1);
    result_ = result_ && noteContent(builder_, level_ + 1);
    exit_section_(builder_, marker_, STATE_NOTE, result_);
    return result_;
  }

  // EOL*
  private static boolean stateNote_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateNote_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "stateNote_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // stateId ARROW stateId [COLON complexLabel]
  public static boolean stateRelationStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateRelationStatement")) return false;
    if (!nextTokenIs(builder_, "<state relation statement>", ID, OPEN_SQUARE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATE_RELATION_STATEMENT, "<state relation statement>");
    result_ = stateId(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW);
    result_ = result_ && stateId(builder_, level_ + 1);
    result_ = result_ && stateRelationStatement_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [COLON complexLabel]
  private static boolean stateRelationStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateRelationStatement_3")) return false;
    stateRelationStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON complexLabel
  private static boolean stateRelationStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stateRelationStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && complexLabel(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_QUOTE STRING_VALUE DOUBLE_QUOTE
  public static boolean string(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "string")) return false;
    if (!nextTokenIs(builder_, DOUBLE_QUOTE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOUBLE_QUOTE, STRING_VALUE, DOUBLE_QUOTE);
    exit_section_(builder_, marker_, STRING, result_);
    return result_;
  }

  /* ********************************************************** */
  // STYLE_OPT COLON STYLE_VAL (COMMA STYLE_OPT COLON STYLE_VAL)*
  public static boolean styleOptions(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "styleOptions")) return false;
    if (!nextTokenIs(builder_, STYLE_OPT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, STYLE_OPT, COLON, STYLE_VAL);
    result_ = result_ && styleOptions_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, STYLE_OPTIONS, result_);
    return result_;
  }

  // (COMMA STYLE_OPT COLON STYLE_VAL)*
  private static boolean styleOptions_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "styleOptions_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!styleOptions_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "styleOptions_3", pos_)) break;
    }
    return true;
  }

  // COMMA STYLE_OPT COLON STYLE_VAL
  private static boolean styleOptions_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "styleOptions_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, STYLE_OPT, COLON, STYLE_VAL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // STYLE styleStatementTarget styleOptions
  public static boolean styleStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "styleStatement")) return false;
    if (!nextTokenIs(builder_, STYLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STYLE);
    result_ = result_ && styleStatementTarget(builder_, level_ + 1);
    result_ = result_ && styleOptions(builder_, level_ + 1);
    exit_section_(builder_, marker_, STYLE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // STYLE_TARGET
  public static boolean styleStatementTarget(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "styleStatementTarget")) return false;
    if (!nextTokenIs(builder_, STYLE_TARGET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STYLE_TARGET);
    exit_section_(builder_, marker_, STYLE_STATEMENT_TARGET, result_);
    return result_;
  }

  /* ********************************************************** */
  // vertex STYLE_SEPARATOR STYLE_TARGET
  public static boolean styledVertex(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "styledVertex")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = vertex(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, STYLE_SEPARATOR, STYLE_TARGET);
    exit_section_(builder_, marker_, STYLED_VERTEX, result_);
    return result_;
  }

  /* ********************************************************** */
  // subgraphLines
  public static boolean subgraphBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphBlock")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SUBGRAPH_BLOCK, "<subgraph block>");
    result_ = subgraphLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SUBGRAPH subgraphName
  public static boolean subgraphHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphHeader")) return false;
    if (!nextTokenIs(builder_, SUBGRAPH)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SUBGRAPH);
    result_ = result_ && subgraphName(builder_, level_ + 1);
    exit_section_(builder_, marker_, SUBGRAPH_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // (maybeEmptyString | maybeEmptyMdText) complexIdentifier
  //   | (string | mdText) complexIdentifier?
  //   | complexIdentifier
  static boolean subgraphId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphId")) return false;
    if (!nextTokenIs(builder_, "", DOUBLE_QUOTE, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphId_0(builder_, level_ + 1);
    if (!result_) result_ = subgraphId_1(builder_, level_ + 1);
    if (!result_) result_ = complexIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (maybeEmptyString | maybeEmptyMdText) complexIdentifier
  private static boolean subgraphId_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphId_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphId_0_0(builder_, level_ + 1);
    result_ = result_ && complexIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // maybeEmptyString | maybeEmptyMdText
  private static boolean subgraphId_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphId_0_0")) return false;
    boolean result_;
    result_ = maybeEmptyString(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyMdText(builder_, level_ + 1);
    return result_;
  }

  // (string | mdText) complexIdentifier?
  private static boolean subgraphId_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphId_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphId_1_0(builder_, level_ + 1);
    result_ = result_ && subgraphId_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // string | mdText
  private static boolean subgraphId_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphId_1_0")) return false;
    boolean result_;
    result_ = string(builder_, level_ + 1);
    if (!result_) result_ = mdText(builder_, level_ + 1);
    return result_;
  }

  // complexIdentifier?
  private static boolean subgraphId_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphId_1_1")) return false;
    complexIdentifier(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // flowchartStatement
  //   | directionStatement
  static boolean subgraphInnerStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphInnerStatement")) return false;
    boolean result_;
    result_ = flowchartStatement(builder_, level_ + 1);
    if (!result_) result_ = directionStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // subgraphInnerStatement [separator] | separator
  static boolean subgraphLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // subgraphInnerStatement [separator]
  private static boolean subgraphLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphInnerStatement(builder_, level_ + 1);
    result_ = result_ && subgraphLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean subgraphLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (subgraphLine | IGNORED) [subgraphLines]
  static boolean subgraphLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphLines_0(builder_, level_ + 1);
    result_ = result_ && subgraphLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // subgraphLine | IGNORED
  private static boolean subgraphLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphLines_0")) return false;
    boolean result_;
    result_ = subgraphLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [subgraphLines]
  private static boolean subgraphLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphLines_1")) return false;
    subgraphLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // subgraphId [OPEN_SQUARE nodeText CLOSE_SQUARE]
  public static boolean subgraphName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphName")) return false;
    if (!nextTokenIs(builder_, "<subgraph name>", DOUBLE_QUOTE, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SUBGRAPH_NAME, "<subgraph name>");
    result_ = subgraphId(builder_, level_ + 1);
    result_ = result_ && subgraphName_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [OPEN_SQUARE nodeText CLOSE_SQUARE]
  private static boolean subgraphName_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphName_1")) return false;
    subgraphName_1_0(builder_, level_ + 1);
    return true;
  }

  // OPEN_SQUARE nodeText CLOSE_SQUARE
  private static boolean subgraphName_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphName_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_SQUARE);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // subgraphHeader separator EOL* subgraphBlock? END
  public static boolean subgraphStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphStatement")) return false;
    if (!nextTokenIs(builder_, SUBGRAPH)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = subgraphHeader(builder_, level_ + 1);
    result_ = result_ && separator(builder_, level_ + 1);
    result_ = result_ && subgraphStatement_2(builder_, level_ + 1);
    result_ = result_ && subgraphStatement_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, SUBGRAPH_STATEMENT, result_);
    return result_;
  }

  // EOL*
  private static boolean subgraphStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphStatement_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "subgraphStatement_2", pos_)) break;
    }
    return true;
  }

  // subgraphBlock?
  private static boolean subgraphStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "subgraphStatement_3")) return false;
    subgraphBlock(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // timelineLines
  public static boolean timelineBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TIMELINE_BODY, "<timeline body>");
    result_ = timelineLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // complexTaskName [IGNORED] (EOL* COLON sectionTaskData)*
  public static boolean timelineDataStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineDataStatement")) return false;
    if (!nextTokenIs(builder_, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexTaskName(builder_, level_ + 1);
    result_ = result_ && timelineDataStatement_1(builder_, level_ + 1);
    result_ = result_ && timelineDataStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, TIMELINE_DATA_STATEMENT, result_);
    return result_;
  }

  // [IGNORED]
  private static boolean timelineDataStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineDataStatement_1")) return false;
    consumeToken(builder_, IGNORED);
    return true;
  }

  // (EOL* COLON sectionTaskData)*
  private static boolean timelineDataStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineDataStatement_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!timelineDataStatement_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "timelineDataStatement_2", pos_)) break;
    }
    return true;
  }

  // EOL* COLON sectionTaskData
  private static boolean timelineDataStatement_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineDataStatement_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineDataStatement_2_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && sectionTaskData(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL*
  private static boolean timelineDataStatement_2_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineDataStatement_2_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "timelineDataStatement_2_0_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // TIMELINE
  public static boolean timelineHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineHeader")) return false;
    if (!nextTokenIs(builder_, TIMELINE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TIMELINE);
    exit_section_(builder_, marker_, TIMELINE_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // timelineStatement [EOL] | EOL
  static boolean timelineLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // timelineStatement [EOL]
  private static boolean timelineLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineStatement(builder_, level_ + 1);
    result_ = result_ && timelineLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean timelineLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // (timelineLine | IGNORED) [timelineLines]
  static boolean timelineLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineLines_0(builder_, level_ + 1);
    result_ = result_ && timelineLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // timelineLine | IGNORED
  private static boolean timelineLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineLines_0")) return false;
    boolean result_;
    result_ = timelineLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [timelineLines]
  private static boolean timelineLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineLines_1")) return false;
    timelineLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // timelineSectionLines
  public static boolean timelineSectionBlock(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionBlock")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TIMELINE_SECTION_BLOCK, "<timeline section block>");
    result_ = timelineSectionLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SECTION complexSectionTitle
  public static boolean timelineSectionHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionHeader")) return false;
    if (!nextTokenIs(builder_, SECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SECTION);
    result_ = result_ && complexSectionTitle(builder_, level_ + 1);
    exit_section_(builder_, marker_, TIMELINE_SECTION_HEADER, result_);
    return result_;
  }

  /* ********************************************************** */
  // timelineDataStatement
  static boolean timelineSectionInnerStatement(PsiBuilder builder_, int level_) {
    return timelineDataStatement(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // timelineSectionInnerStatement [EOL] | EOL
  static boolean timelineSectionLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionLine")) return false;
    if (!nextTokenIs(builder_, "", EOL, TASK_NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineSectionLine_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // timelineSectionInnerStatement [EOL]
  private static boolean timelineSectionLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineSectionInnerStatement(builder_, level_ + 1);
    result_ = result_ && timelineSectionLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [EOL]
  private static boolean timelineSectionLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionLine_0_1")) return false;
    consumeToken(builder_, EOL);
    return true;
  }

  /* ********************************************************** */
  // (timelineSectionLine | IGNORED) [timelineSectionLines]
  static boolean timelineSectionLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineSectionLines_0(builder_, level_ + 1);
    result_ = result_ && timelineSectionLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // timelineSectionLine | IGNORED
  private static boolean timelineSectionLines_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionLines_0")) return false;
    boolean result_;
    result_ = timelineSectionLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IGNORED);
    return result_;
  }

  // [timelineSectionLines]
  private static boolean timelineSectionLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionLines_1")) return false;
    timelineSectionLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // timelineSectionHeader EOL* [timelineSectionBlock]
  public static boolean timelineSectionStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionStatement")) return false;
    if (!nextTokenIs(builder_, SECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = timelineSectionHeader(builder_, level_ + 1);
    result_ = result_ && timelineSectionStatement_1(builder_, level_ + 1);
    result_ = result_ && timelineSectionStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, TIMELINE_SECTION_STATEMENT, result_);
    return result_;
  }

  // EOL*
  private static boolean timelineSectionStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionStatement_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, EOL)) break;
      if (!empty_element_parsed_guard_(builder_, "timelineSectionStatement_1", pos_)) break;
    }
    return true;
  }

  // [timelineSectionBlock]
  private static boolean timelineSectionStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineSectionStatement_2")) return false;
    timelineSectionBlock(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // timelineDataStatement
  //   | timelineSectionStatement
  //   | titleStatement
  //   | directive
  //   | accStatement
  static boolean timelineStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "timelineStatement")) return false;
    boolean result_;
    result_ = timelineDataStatement(builder_, level_ + 1);
    if (!result_) result_ = timelineSectionStatement(builder_, level_ + 1);
    if (!result_) result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // TITLE complexTitleValue
  public static boolean titleStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "titleStatement")) return false;
    if (!nextTokenIs(builder_, TITLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TITLE);
    result_ = result_ && complexTitleValue(builder_, level_ + 1);
    exit_section_(builder_, marker_, TITLE_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // ANALYSIS | DEMONSTRATION | INSPECTION | TEST
  public static boolean verifyType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "verifyType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERIFY_TYPE, "<verify type>");
    result_ = consumeToken(builder_, ANALYSIS);
    if (!result_) result_ = consumeToken(builder_, DEMONSTRATION);
    if (!result_) result_ = consumeToken(builder_, INSPECTION);
    if (!result_) result_ = consumeToken(builder_, TEST);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // complexIdentifier [vertexText]
  public static boolean vertex(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertex")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = complexIdentifier(builder_, level_ + 1);
    result_ = result_ && vertex_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, VERTEX, result_);
    return result_;
  }

  // [vertexText]
  private static boolean vertex_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertex_1")) return false;
    vertexText(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // nodeStatement [flowchartLinkStatement vertexStatement]
  public static boolean vertexStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexStatement")) return false;
    if (!nextTokenIs(builder_, ID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nodeStatement(builder_, level_ + 1);
    result_ = result_ && vertexStatement_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, VERTEX_STATEMENT, result_);
    return result_;
  }

  // [flowchartLinkStatement vertexStatement]
  private static boolean vertexStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexStatement_1")) return false;
    vertexStatement_1_0(builder_, level_ + 1);
    return true;
  }

  // flowchartLinkStatement vertexStatement
  private static boolean vertexStatement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexStatement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = flowchartLinkStatement(builder_, level_ + 1);
    result_ = result_ && vertexStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_SQUARE nodeText CLOSE_SQUARE
  //   | OPEN_ROUND nodeText CLOSE_ROUND
  //   | STADIUM_START nodeText STADIUM_END
  //   | SUBROUTINE_START nodeText SUBROUTINE_END
  //   | CYLINDER_START nodeText CYLINDER_END
  //   | CIRCLE_START nodeText CIRCLE_END
  //   | ASYMMETRIC_START nodeText CLOSE_SQUARE
  //   | DIAMOND_START nodeText DIAMOND_END
  //   | HEXAGON_START nodeText HEXAGON_END
  //   | TRAP_START nodeText INV_TRAP_END
  //   | INV_TRAP_START nodeText TRAP_END
  //   | TRAP_START nodeText TRAP_END
  //   | INV_TRAP_START nodeText INV_TRAP_END
  //   | DOUBLE_CIRCLE_START nodeText DOUBLE_CIRCLE_END
  public static boolean vertexText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERTEX_TEXT, "<vertex text>");
    result_ = vertexText_0(builder_, level_ + 1);
    if (!result_) result_ = vertexText_1(builder_, level_ + 1);
    if (!result_) result_ = vertexText_2(builder_, level_ + 1);
    if (!result_) result_ = vertexText_3(builder_, level_ + 1);
    if (!result_) result_ = vertexText_4(builder_, level_ + 1);
    if (!result_) result_ = vertexText_5(builder_, level_ + 1);
    if (!result_) result_ = vertexText_6(builder_, level_ + 1);
    if (!result_) result_ = vertexText_7(builder_, level_ + 1);
    if (!result_) result_ = vertexText_8(builder_, level_ + 1);
    if (!result_) result_ = vertexText_9(builder_, level_ + 1);
    if (!result_) result_ = vertexText_10(builder_, level_ + 1);
    if (!result_) result_ = vertexText_11(builder_, level_ + 1);
    if (!result_) result_ = vertexText_12(builder_, level_ + 1);
    if (!result_) result_ = vertexText_13(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // OPEN_SQUARE nodeText CLOSE_SQUARE
  private static boolean vertexText_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_SQUARE);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // OPEN_ROUND nodeText CLOSE_ROUND
  private static boolean vertexText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPEN_ROUND);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_ROUND);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STADIUM_START nodeText STADIUM_END
  private static boolean vertexText_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STADIUM_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, STADIUM_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SUBROUTINE_START nodeText SUBROUTINE_END
  private static boolean vertexText_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SUBROUTINE_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SUBROUTINE_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CYLINDER_START nodeText CYLINDER_END
  private static boolean vertexText_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CYLINDER_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CYLINDER_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CIRCLE_START nodeText CIRCLE_END
  private static boolean vertexText_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CIRCLE_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CIRCLE_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ASYMMETRIC_START nodeText CLOSE_SQUARE
  private static boolean vertexText_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASYMMETRIC_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLOSE_SQUARE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DIAMOND_START nodeText DIAMOND_END
  private static boolean vertexText_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DIAMOND_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DIAMOND_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // HEXAGON_START nodeText HEXAGON_END
  private static boolean vertexText_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, HEXAGON_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, HEXAGON_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TRAP_START nodeText INV_TRAP_END
  private static boolean vertexText_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_9")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TRAP_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, INV_TRAP_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // INV_TRAP_START nodeText TRAP_END
  private static boolean vertexText_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_10")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, INV_TRAP_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, TRAP_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TRAP_START nodeText TRAP_END
  private static boolean vertexText_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_11")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TRAP_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, TRAP_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // INV_TRAP_START nodeText INV_TRAP_END
  private static boolean vertexText_12(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_12")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, INV_TRAP_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, INV_TRAP_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_CIRCLE_START nodeText DOUBLE_CIRCLE_END
  private static boolean vertexText_13(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "vertexText_13")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_CIRCLE_START);
    result_ = result_ && nodeText(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_CIRCLE_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // xyChartText* (bandData | arrowData)
  static boolean xAxisData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xAxisData")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xAxisData_0(builder_, level_ + 1);
    result_ = result_ && xAxisData_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // xyChartText*
  private static boolean xAxisData_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xAxisData_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!xyChartText(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "xAxisData_0", pos_)) break;
    }
    return true;
  }

  // bandData | arrowData
  private static boolean xAxisData_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xAxisData_1")) return false;
    boolean result_;
    result_ = bandData(builder_, level_ + 1);
    if (!result_) result_ = arrowData(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // X_AXIS xAxisData
  public static boolean xAxisStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xAxisStatement")) return false;
    if (!nextTokenIs(builder_, X_AXIS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, X_AXIS);
    result_ = result_ && xAxisData(builder_, level_ + 1);
    exit_section_(builder_, marker_, X_AXIS_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // xyChartLines
  public static boolean xyChartBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, XY_CHART_BODY, "<xy chart body>");
    result_ = xyChartLines(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // XY_CHART [ORIENTATION_VALUE]
  public static boolean xyChartHeader(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartHeader")) return false;
    if (!nextTokenIs(builder_, XY_CHART)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, XY_CHART);
    result_ = result_ && xyChartHeader_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, XY_CHART_HEADER, result_);
    return result_;
  }

  // [ORIENTATION_VALUE]
  private static boolean xyChartHeader_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartHeader_1")) return false;
    consumeToken(builder_, ORIENTATION_VALUE);
    return true;
  }

  /* ********************************************************** */
  // xyChartStatement [separator] | separator
  static boolean xyChartLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartLine")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xyChartLine_0(builder_, level_ + 1);
    if (!result_) result_ = separator(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // xyChartStatement [separator]
  private static boolean xyChartLine_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartLine_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xyChartStatement(builder_, level_ + 1);
    result_ = result_ && xyChartLine_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [separator]
  private static boolean xyChartLine_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartLine_0_1")) return false;
    separator(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // xyChartLine [xyChartLines]
  static boolean xyChartLines(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartLines")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xyChartLine(builder_, level_ + 1);
    result_ = result_ && xyChartLines_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [xyChartLines]
  private static boolean xyChartLines_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartLines_1")) return false;
    xyChartLines(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // titleStatement
  //   | directive
  //   | accStatement
  //   | xAxisStatement
  //   | yAxisStatement
  //   | lineStatement
  //   | barStatement
  static boolean xyChartStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartStatement")) return false;
    boolean result_;
    result_ = titleStatement(builder_, level_ + 1);
    if (!result_) result_ = directive(builder_, level_ + 1);
    if (!result_) result_ = accStatement(builder_, level_ + 1);
    if (!result_) result_ = xAxisStatement(builder_, level_ + 1);
    if (!result_) result_ = yAxisStatement(builder_, level_ + 1);
    if (!result_) result_ = lineStatement(builder_, level_ + 1);
    if (!result_) result_ = barStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // XY_CHART_TEXT | maybeEmptyString | maybeEmptyMdText
  static boolean xyChartText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xyChartText")) return false;
    if (!nextTokenIs(builder_, "", DOUBLE_QUOTE, XY_CHART_TEXT)) return false;
    boolean result_;
    result_ = consumeToken(builder_, XY_CHART_TEXT);
    if (!result_) result_ = maybeEmptyString(builder_, level_ + 1);
    if (!result_) result_ = maybeEmptyMdText(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // xyChartText* arrowData
  static boolean yAxisData(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yAxisData")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = yAxisData_0(builder_, level_ + 1);
    result_ = result_ && arrowData(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // xyChartText*
  private static boolean yAxisData_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yAxisData_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!xyChartText(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "yAxisData_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // Y_AXIS yAxisData
  public static boolean yAxisStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yAxisStatement")) return false;
    if (!nextTokenIs(builder_, Y_AXIS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, Y_AXIS);
    result_ = result_ && yAxisData(builder_, level_ + 1);
    exit_section_(builder_, marker_, Y_AXIS_STATEMENT, result_);
    return result_;
  }

}
