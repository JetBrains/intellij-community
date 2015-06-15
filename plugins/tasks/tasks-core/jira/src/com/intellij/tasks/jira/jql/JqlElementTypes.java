package com.intellij.tasks.jira.jql;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.tasks.jira.jql.psi.impl.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 *
 * Slightly refactored JQL grammar. See original ANTLR parser grammar at:
 * http://jira.stagingonserver.com/jira-project/jira-components/jira-core/src/main/antlr3/com/atlassian/jira/jql/parser/antlr/Jql.g
 *
 * query ::= or_clause [order_by]
 * or_clause ::= and_clause {or_op and_clause}
 * and_clause ::= not_expr {and_op not_expr}
 * not_expr ::= not_op not_expr
 *            | subclause
 *            | terminal_clause
 * subclause ::= "(" or_clause ")"
 * terminal_clause ::= simple_clause
 *                   | was_clause
 *                   | changed_clause
 * simple_clause ::= field simple_op value
 * # although this is not mentioned in JQL manual, usage of both "from" and "to" predicates in "was" clause is legal
 * was_clause ::= field "was" ["not"] ["in"] operand {history_predicate}
 * changed_clause ::= field "changed" {history_predicate}
 * simple_op ::= "="
 *             | "!="
 *             | "~"
 *             | "!~"
 *             | "<"
 *             | ">"
 *             | "<="
 *             | ">="
 *             | ["not"] "in"
 *             | "is" ["not"]
 * not_op ::= "not" | "!"
 * and_op ::= "and" | "&&" | "&"
 * or_op ::= "or" | "||" | "|"
 * history_predicate ::= "from" operand
 *                     | "to" operand
 *                     | "by" operand
 *                     | "before" operand
 *                     | "after" operand
 *                     | "on" operand
 *                     | "during" operand
 * field ::= string
 *         | NUMBER
 *         | CUSTOM_FIELD
 * operand ::= empty
 *           | string
 *           | NUMBER
 *           | func
 *           | list
 * empty ::= "empty" | "null"
 * list ::= "(" operand {"," operand} ")"
 * func ::= fname "(" arg_list ")"
 * # function name can be even number (!)
 * fname ::= string | NUMBER
 * arg_list ::= argument {"," argument}
 * argument ::= string | NUMBER
 * string ::= SQUOTED_STRING
 *          | QUOTED_STRING
 *          | UNQOUTED_STRING
 * order_by ::= "order" "by" sort_key {sort_key}
 * sort_key ::= field ("asc" | "desc")
 *
 */
public interface JqlElementTypes {
  IFileElementType FILE = new IFileElementType(JqlLanguage.INSTANCE);
  IElementType QUERY = new JqlElementType("QUERY");
  IElementType OR_CLAUSE = new JqlElementType("OR_CLAUSE");
  IElementType AND_CLAUSE = new JqlElementType("AND_CLAUSE");
  IElementType NOT_CLAUSE = new JqlElementType("NOT_CLAUSE");
  // actually parenthesized clause, named so to be consistent with official grammar
  IElementType SUB_CLAUSE = new JqlElementType("SUB_CLAUSE");
  //IElementType TERMINAL_CLAUSE = new JqlElementType("TERMINAL_CLAUSE");
  // field (= | != | ~ | !~ | < | > | <= | >= | is [not] | [not] in) value
  IElementType SIMPLE_CLAUSE = new JqlElementType("SIMPLE_CLAUSE");
  // field was [not] [in] value {history_predicate}
  IElementType WAS_CLAUSE = new JqlElementType("WAS_CLAUSE");
  // field changed {history_predicate}
  IElementType CHANGED_CLAUSE = new JqlElementType("CHANGED_CLAUSE");
  IElementType LIST = new JqlElementType("LIST");
  IElementType ORDER_BY = new JqlElementType("ORDER_BY");
  IElementType IDENTIFIER = new JqlElementType("IDENTIFIER");
  IElementType LITERAL = new JqlElementType("LITERAL");
  IElementType FUNCTION_CALL = new JqlElementType("FUNCTION_CALL");
  IElementType ARGUMENT_LIST = new JqlElementType("ARGUMENT_LIST");
  IElementType SORT_KEY = new JqlElementType("SORT_KEY");
  IElementType EMPTY = new JqlElementType("EMPTY");
  IElementType HISTORY_PREDICATE = new JqlElementType("HISTORY_PREDICATE");

  TokenSet OPERAND_NODES = TokenSet.create(
    JqlTokenTypes.NUMBER_LITERAL, JqlTokenTypes.STRING_LITERAL, LIST, FUNCTION_CALL, EMPTY
  );

  class Factory {
    @NotNull
    public static PsiElement createElement(@NotNull ASTNode node) {
      final IElementType type = node.getElementType();
      if (type == QUERY) {
        return new JqlQueryImpl(node);
      }
      else if (type == OR_CLAUSE) {
        return new JqlOrClauseImpl(node);
      }
      else if (type == AND_CLAUSE) {
        return new JqlAndClauseImpl(node);
      }
      else if (type == NOT_CLAUSE) {
        return new JqlNotClauseImpl(node);
      }
      else if (type == SUB_CLAUSE) {
        return new JqlSubClauseImpl(node);
      }
      else if (type == SIMPLE_CLAUSE) {
        return new JqlSimpleClauseImpl(node);
      }
      else if (type == WAS_CLAUSE) {
        return new JqlWasClauseImpl(node);
      }
      else if (type == CHANGED_CLAUSE) {
        return new JqlChangedClauseImpl(node);
      }
      else if (type == LIST) {
        return new JqlListImpl(node);
      }
      else if (type == ORDER_BY) {
        return new JqlOrderByImpl(node);
      }
      else if (type == IDENTIFIER) {
        return new JqlIdentifierImpl(node);
      }
      else if (type == LITERAL) {
        return new JqlLiteralImpl(node);
      }
      else if (type == FUNCTION_CALL) {
        return new JqlFunctionCallImpl(node);
      }
      else if (type == ARGUMENT_LIST) {
        return new JqlArgumentListImpl(node);
      }
      else if (type == SORT_KEY) {
        return new JqlSortKeyImpl(node);
      }
      else if (type == EMPTY) {
        return new JqlEmptyValueImpl(node);
      }
      else if (type == HISTORY_PREDICATE) {
        return new JqlHistoryPredicateImpl(node);
      }
      return new ASTWrapperPsiElement(node);
    }
  }
}
