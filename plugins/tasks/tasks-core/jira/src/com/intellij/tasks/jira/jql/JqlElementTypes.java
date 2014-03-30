package com.intellij.tasks.jira.jql;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.tasks.jira.jql.psi.impl.*;

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
  IElementType QUERY = new JqlElementType("QUERY", JqlQueryImpl.class);
  IElementType OR_CLAUSE = new JqlElementType("OR_CLAUSE", JqlOrClauseImpl.class);
  IElementType AND_CLAUSE = new JqlElementType("AND_CLAUSE", JqlAndClauseImpl.class);
  IElementType NOT_CLAUSE = new JqlElementType("NOT_CLAUSE", JqlNotClauseImpl.class);
  // actually parenthesized clause, named so to be consistent with official grammar
  IElementType SUB_CLAUSE = new JqlElementType("SUB_CLAUSE", JqlSubClauseImpl.class);
  //IElementType TERMINAL_CLAUSE = new JqlElementType("TERMINAL_CLAUSE");
  // field (= | != | ~ | !~ | < | > | <= | >= | is [not] | [not] in) value
  IElementType SIMPLE_CLAUSE = new JqlElementType("SIMPLE_CLAUSE", JqlSimpleClauseImpl.class);
  // field was [not] [in] value {history_predicate}
  IElementType WAS_CLAUSE = new JqlElementType("WAS_CLAUSE", JqlWasClauseImpl.class);
  // field changed {history_predicate}
  IElementType CHANGED_CLAUSE = new JqlElementType("CHANGED_CLAUSE", JqlChangedClauseImpl.class);
  IElementType LIST = new JqlElementType("LIST", JqlListImpl.class);
  IElementType ORDER_BY = new JqlElementType("ORDER_BY", JqlOrderByImpl.class);
  IElementType IDENTIFIER = new JqlElementType("IDENTIFIER", JqlIdentifierImpl.class);
  IElementType LITERAL = new JqlElementType("LITERAL", JqlLiteralImpl.class);
  IElementType FUNCTION_CALL = new JqlElementType("FUNCTION_CALL", JqlFunctionCallImpl.class);
  IElementType ARGUMENT_LIST = new JqlElementType("ARGUMENT_LIST", JqlArgumentListImpl.class);
  IElementType SORT_KEY = new JqlElementType("SORT_KEY", JqlSortKeyImpl.class);
  IElementType EMPTY = new JqlElementType("EMPTY", JqlEmptyValueImpl.class);
  IElementType HISTORY_PREDICATE = new JqlElementType("HISTORY_PREDICATE", JqlHistoryPredicateImpl.class);

  TokenSet OPERAND_NODES = TokenSet.create(
    JqlTokenTypes.NUMBER_LITERAL, JqlTokenTypes.STRING_LITERAL, LIST, FUNCTION_CALL, EMPTY
  );
}
