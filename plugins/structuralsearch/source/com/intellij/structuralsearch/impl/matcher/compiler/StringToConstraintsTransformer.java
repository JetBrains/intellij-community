package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.openapi.util.text.StringUtil;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:29:05
 * To change this template use File | Settings | File Templates.
 */
class StringToConstraintsTransformer {
  private static StringBuffer buf = new StringBuffer();
  private static Pattern p = Pattern.compile("(\\w+)\\('(\\w+)\\)");
  private static Pattern p2 = Pattern.compile("(\\w+)");
  private static Pattern p3 = Pattern.compile("(\\w+)\\(( ?[\\*<>\\.\\?\\:\\\\\\(\\)\\[\\]\\w\\|\\+]+ ?)\\)");

  static void transformOldPattern(MatchOptions options) {
      final String pattern = options.getSearchPattern();

      buf.setLength(0);

      StringBuffer miscBuffer = null;
      int anonymousTypedVarsCount = 0;

      for(int index=0;index < pattern.length();++index) {
        char ch = pattern.charAt(index);

        if (ch=='\'') {
          // doubling '
          if (index + 1 < pattern.length() &&
              pattern.charAt(index + 1)=='\''
             ) {
            // ignore next '
            index++;
          } else if (index + 2 < pattern.length() &&
                pattern.charAt(index + 2)=='\''
             ) {
            // eat simple character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else if (index + 3 < pattern.length() &&
                pattern.charAt(index + 1)=='\\' &&
                pattern.charAt(index + 3)=='\''
          ) {
            // eat simple escape character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else if (index + 7 < pattern.length() &&
                pattern.charAt(index + 1)=='\\' &&
                pattern.charAt(index + 2)=='u' &&
                pattern.charAt(index + 7)=='\'') {
            // eat simple escape character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else {
            // typed variable

            buf.append("$");
            if (miscBuffer==null) miscBuffer = new StringBuffer();
            else miscBuffer.setLength(0);

            // eat the name of typed var
            for(++index;index<pattern.length() && Character.isJavaIdentifierPart(pattern.charAt(index));++index) {
              ch = pattern.charAt(index);
              miscBuffer.append(ch);
              buf.append(ch);
            }

            boolean anonymous = false;

            if (miscBuffer.charAt(0)=='_')  {
              anonymous = true;

              if(miscBuffer.length() == 1) {
                // anonymous var, make it unique for the case of constraints
                anonymousTypedVarsCount++;
                miscBuffer.append(anonymousTypedVarsCount);
                buf.append(anonymousTypedVarsCount);
              } else {
                buf.deleteCharAt(buf.length()-miscBuffer.length());
                miscBuffer.deleteCharAt(0);
              }
            }


            buf.append("$");
            String typedVar = miscBuffer.toString();
            int minOccurs = 1;
            int maxOccurs = 1;
            boolean greedy = true;
            MatchVariableConstraint constraint = options.getVariableConstraint(typedVar);
            boolean constraintCreated = false;

            if (constraint==null) {
              constraint = new MatchVariableConstraint();
              constraint.setName( typedVar );
              options.addVariableConstraint(constraint);
              constraintCreated = true;
            }

            // Check the number of occurences for typed variable
            if (index < pattern.length()) {
              char possibleQuantifier = pattern.charAt(index);

              if (possibleQuantifier=='+') {
                maxOccurs = Integer.MAX_VALUE;
                ++index;
              } else if (possibleQuantifier=='?') {
                minOccurs = 0;
                ++index;
              } else if (possibleQuantifier=='*') {
                minOccurs = 0;
                maxOccurs = Integer.MAX_VALUE;
                ++index;
              } else if (possibleQuantifier=='{') {
                ++index;
                minOccurs = 0;
                while( (ch = pattern.charAt(index))>='0' && ch <= '9') {
                  minOccurs *= 10;
                  minOccurs += (ch-'0');
                  ++index;
                }

                if (ch==',') {
                  ++index;
                  maxOccurs = 0;

                  while( (ch = pattern.charAt(index))>='0' && ch <= '9') {
                    maxOccurs *= 10;
                    maxOccurs += (ch-'0');
                    ++index;
                  }
                } else {
                  maxOccurs = Integer.MAX_VALUE;
                }

                // ch must == }
                ++index;
              }

              if (index < pattern.length()) {
                ch = pattern.charAt(index);
                if (ch=='?') {
                  greedy = false;
                  ++index;
                }
              }
            }

            if (constraintCreated) {
              constraint.setMinCount(minOccurs);
              constraint.setMaxCount(maxOccurs);
              constraint.setGreedy(greedy);
              constraint.setPartOfSearchResults(!anonymous);
            }

            index = eatTypedVarCondition(index, pattern, miscBuffer, constraint);

            if (index == pattern.length()) break;
            // fall thri to append white space
            ch = pattern.charAt(index);
          }
        }

        buf.append(ch);
      }

      options.setSearchPattern( buf.toString() );
    }

  private static int eatTypedVarCondition(int index,
                                          String pattern,
                                          StringBuffer miscBuffer,
                                          MatchVariableConstraint constraint) {
    if (index<pattern.length() && pattern.charAt(index)==':') {
      ++index;

      final char ch = pattern.charAt(index);

      if (ch=='+' || ch=='*' ) {
        // this is type axis navigation relation
        switch(ch) {
          case '+':
            constraint.setStrictlyWithinHierarchy(true);
            break;
          case '*':
            constraint.setWithinHierarchy(true);
            break;
        }

        ++index;
      }

      if (pattern.charAt(index)=='[') {
        // eat complete condition

        miscBuffer.setLength(0);
        for(++index;pattern.charAt(index)!=']';++index) {
          miscBuffer.append(pattern.charAt(index));
        }
        ++index;
        parseCondition(constraint,miscBuffer.toString());
      } else {
        // eat reg exp constraint
        miscBuffer.setLength(0);
        index = handleRegExp(index, pattern, miscBuffer, constraint);
      }
    }
    return index;
  }

  private static int handleRegExp(int index,
                                  String pattern,
                                  StringBuffer miscBuffer,
                                  MatchVariableConstraint constraint) {
    for(;index<pattern.length() && !Character.isWhitespace(pattern.charAt(index));++index) {
      miscBuffer.append(pattern.charAt(index));
    }

    String regexp = miscBuffer.toString();
    if (regexp.length()==0) return index;

    if (constraint.getRegExp()!=null &&
        constraint.getRegExp().length() > 0 &&
        !constraint.getRegExp().equals(regexp)) {
      throw new MalformedPatternException("Two different type constraints");
    } else {
      constraint.setRegExp(regexp);
    }

    return index;
  }

  private static void parseCondition(MatchVariableConstraint constraint, String s) {
      StringTokenizer tokenizer = new StringTokenizer(s,"&&");

      while(tokenizer.hasMoreElements()) {
        String token = tokenizer.nextToken().trim();
        boolean hasNot = false;
        boolean consumed = false;
        String option;

        if (StringUtil.startsWithChar(token, '!')) {
          token = token.substring(1);
          hasNot = true;
        }

        Matcher m = p.matcher(token);

        if (m.matches()) {
          option = m.group(1);

          if (option.equalsIgnoreCase("ref")) {
            String name = m.group(2);

            constraint.setReference(true);
            constraint.setInvertReference(hasNot);
            constraint.setNameOfReferenceVar(name);
            consumed = true;
          }
        } else {
          m = p2.matcher(token);

          if (m.matches()) {
            option = m.group(1);

            if (option.equalsIgnoreCase("read")) {
              constraint.setReadAccess(true);
              constraint.setInvertReadAccess(hasNot);
              consumed = true;
            } else if (option.equalsIgnoreCase("write")) {
              constraint.setWriteAccess(true);
              constraint.setInvertWriteAccess(hasNot);
              consumed = true;
            }
          } else {
            m = p3.matcher(token);

            if (m.matches()) {
              option = m.group(1);

              if (option.equalsIgnoreCase("regex") ||
                  option.equalsIgnoreCase("regexw")
                  ) {
                String typePattern = getSingleParameter(m,"Reg exp should be delimited with spaces");

                if (StringUtil.startsWithChar(typePattern, '*')) {
                  typePattern = typePattern.substring(1);
                  constraint.setWithinHierarchy(true);
                }
                constraint.setRegExp( typePattern );
                constraint.setInvertRegExp( hasNot );
                consumed = true;
                if (option.equalsIgnoreCase("regexw")) {
                  constraint.setWholeWordsOnly(true);
                }
              } else if (option.equalsIgnoreCase("exprtype")) {
                String exprTypePattern = getSingleParameter(m, "Reg exp in expr type should be delimited with spaces");

                if (StringUtil.startsWithChar(exprTypePattern, '*')) {
                  exprTypePattern = exprTypePattern.substring(1);
                  constraint.setExprTypeWithinHierarchy(true);
                }
                constraint.setNameOfExprType( exprTypePattern );
                constraint.setInvertExprType( hasNot );
                consumed = true;
              } else if (option.equalsIgnoreCase("formal")) {
                String exprTypePattern = getSingleParameter(m, "Reg exp in formal arg type should be delimited with spaces");

                if (StringUtil.startsWithChar(exprTypePattern, '*')) {
                  exprTypePattern = exprTypePattern.substring(1);
                  constraint.setFormalArgTypeWithinHierarchy(true);
                }
                constraint.setNameOfFormalArgType( exprTypePattern );
                constraint.setInvertFormalType( hasNot );
                consumed = true;
              } else if (option.equalsIgnoreCase("script")) {
                String script = getSingleParameter(m, "Script should be delimited with spaces");

                constraint.setScriptCodeConstraint( script );
                consumed = true;
              }
            }
          }
        }

        if (!consumed) {
          throw new UnsupportedPatternException(
            token + " option is not recognized"
          );
        }
      }
    }

  private static String getSingleParameter(Matcher m, String errorMessage) {
    final String value = m.group(2);

    if (value.charAt(0)!=' ' || value.charAt(value.length()-1)!=' ') {
      throw new MalformedPatternException(errorMessage);
    }
    return value.trim();
  }
}
