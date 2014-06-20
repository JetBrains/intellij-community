package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:29:05
 * To change this template use File | Settings | File Templates.
 */
class StringToConstraintsTransformer {
  @NonNls private static final String P_STR = "(\\w+)\\('(\\w+)\\)";
  private static final Pattern p = Pattern.compile(P_STR);
  @NonNls private static final String P2_STR = "(\\w+)";
  private static final Pattern p2 = Pattern.compile(P2_STR);
  @NonNls private static final String P3_STR = "(\\w+)\\(( ?(?:[\\\"\\*<>!\\.\\?\\:\\$\\\\\\(\\)\\[\\]\\w\\|\\+ =]*|(?:\\\"[^\\\"]*\\\")) ?)\\)";
  private static final Pattern p3 = Pattern.compile(P3_STR);
  @NonNls private static final String REF = "ref";
  @NonNls private static final String READ = "read";
  @NonNls private static final String WRITE = "write";
  @NonNls private static final String REGEX = "regex";
  @NonNls private static final String REGEXW = "regexw";
  @NonNls private static final String EXPRTYPE = "exprtype";
  @NonNls private static final String FORMAL = "formal";
  @NonNls private static final String SCRIPT = "script";
  @NonNls private static final String CONTAINS = "contains";
  @NonNls private static final String WITHIN = "within";

  static void transformOldPattern(MatchOptions options) {
      final String pattern = options.getSearchPattern();

      final StringBuilder buf = new StringBuilder();

      StringBuilder miscBuffer = null;
      int anonymousTypedVarsCount = 0;

      for(int index=0;index < pattern.length();++index) {
        char ch = pattern.charAt(index);

        if (ch=='\'') {
          // doubling '
          final int length = pattern.length();
          if (index + 1 < length &&
              pattern.charAt(index + 1)=='\''
             ) {
            // ignore next '
            index++;
          } else if (index + 2 < length &&
                     pattern.charAt(index + 2)=='\''
             ) {
            // eat simple character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else if (index + 3 < length &&
                     pattern.charAt(index + 1)=='\\' &&
                     pattern.charAt(index + 3)=='\''
          ) {
            // eat simple escape character
            buf.append(ch);
            buf.append(pattern.charAt(++index));
            buf.append(pattern.charAt(++index));
            ch = pattern.charAt(++index);
          } else if (index + 7 < length &&
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
            if (miscBuffer == null) miscBuffer = new StringBuilder();
            else miscBuffer.setLength(0);

            // eat the name of typed var
            for(++index; index< length && Character.isJavaIdentifierPart(ch = pattern.charAt(index)); ++index) {
              miscBuffer.append(ch);
              buf.append(ch);
            }

            boolean anonymous = false;

            if (miscBuffer.length() == 0) throw new MalformedPatternException(SSRBundle.message("error.expected.character"));
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
              constraintCreated = true;
            }

            // Check the number of occurrences for typed variable
            if (index < length) {
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
                while (index < length && (ch = pattern.charAt(index)) >= '0' && ch <= '9') {
                  minOccurs *= 10;
                  minOccurs += (ch - '0');
                  if (minOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.overflow"));
                  ++index;
                }

                if (ch==',') {
                  ++index;
                  maxOccurs = 0;

                  while (index < length && (ch = pattern.charAt(index)) >= '0' && ch <= '9') {
                    maxOccurs *= 10;
                    maxOccurs += (ch - '0');
                    if (maxOccurs < 0) throw new MalformedPatternException(SSRBundle.message("error.overflow"));
                    ++index;
                  }
                } else {
                  maxOccurs = Integer.MAX_VALUE;
                }

                if (ch != '}') {
                  if (maxOccurs == Integer.MAX_VALUE) throw new MalformedPatternException(SSRBundle.message("error.expected.brace1"));
                  else throw new MalformedPatternException(SSRBundle.message("error.expected.brace2"));
                }
                ++index;
              }

              if (index < length) {
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

            if (index < length && pattern.charAt(index) == ':') {
              ++index;
              if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", ':'));
              ch = pattern.charAt(index);
              if (ch == ':') {
                // double colon instead of condition
                buf.append(ch);
              }
              else {
                index = eatTypedVarCondition(index, pattern, miscBuffer, constraint);
              }
            }

            if (constraintCreated) {
              if (constraint.getWithinConstraint().length() > 0) {
                constraint.setName(Configuration.CONTEXT_VAR_NAME);
              }
              options.addVariableConstraint(constraint);
            }

            if (index == length) break;
            // fall through to append white space
            ch = pattern.charAt(index);
          }
        }

        buf.append(ch);
      }

      options.setSearchPattern( buf.toString() );
    }

  private static int eatTypedVarCondition(int index,
                                          String pattern,
                                          StringBuilder miscBuffer,
                                          MatchVariableConstraint constraint) {
    final int length = pattern.length();

    char ch = pattern.charAt(index);
    if (ch == '+' || ch == '*') {
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
      if (index >= length) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", ch));
      ch = pattern.charAt(index);
    }

    if (ch == '[') {
      // eat complete condition

      miscBuffer.setLength(0);
      for(++index; index < length && ((ch = pattern.charAt(index))!=']' || pattern.charAt(index-1)=='\\'); ++index) {
        miscBuffer.append(ch);
      }
      if (ch != ']') throw new MalformedPatternException(SSRBundle.message("error.expected.condition.or.bracket"));
      ++index;
      parseCondition(constraint, miscBuffer.toString());
    } else {
      // eat reg exp constraint
      miscBuffer.setLength(0);
      index = handleRegExp(index, pattern, miscBuffer, constraint);
    }
    return index;
  }

  private static int handleRegExp(int index,
                                  String pattern,
                                  StringBuilder miscBuffer,
                                  MatchVariableConstraint constraint) {
    char c = pattern.charAt(index - 1);
    final int length = pattern.length();
    for(char ch; index < length && !Character.isWhitespace(ch = pattern.charAt(index)); ++index) {
      miscBuffer.append(ch);
    }

    if (miscBuffer.length() == 0)
      if (c == ':') throw new MalformedPatternException(SSRBundle.message("error.expected.condition", c));
      else return index;
    String regexp = miscBuffer.toString();

    if (constraint.getRegExp()!=null &&
        constraint.getRegExp().length() > 0 &&
        !constraint.getRegExp().equals(regexp)) {
      throw new MalformedPatternException(SSRBundle.message("error.two.different.type.constraints"));
    } else {
      try {
        Pattern.compile(regexp);
      } catch (PatternSyntaxException e) {
        throw new MalformedPatternException(SSRBundle.message("invalid.regular.expression"));
      }
      constraint.setRegExp(regexp);
    }

    return index;
  }

  private static void parseCondition(MatchVariableConstraint constraint, String condition) {
      if (condition.isEmpty()) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "["));
      final List<String> tokens = StringUtil.split(condition, "&&", true, false);

      for (String token : tokens) {
        token = token.trim();
        if (token.isEmpty()) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "&&"));
        boolean hasNot = false;
        boolean consumed = false;

        if (StringUtil.startsWithChar(token, '!')) {
          token = token.substring(1);
          if (token.isEmpty()) throw new MalformedPatternException(SSRBundle.message("error.expected.condition", "!"));
          hasNot = true;
        }

        Matcher m = p.matcher(token);

        if (m.matches()) {
          String option = m.group(1);

          if (option.equalsIgnoreCase(REF)) {
            String name = m.group(2);

            constraint.setReference(true);
            constraint.setInvertReference(hasNot);
            constraint.setNameOfReferenceVar(name);
            consumed = true;
          }
        } else {
          m = p2.matcher(token);

          if (m.matches()) {
            String option = m.group(1);

            if (option.equalsIgnoreCase(READ)) {
              constraint.setReadAccess(true);
              constraint.setInvertReadAccess(hasNot);
              consumed = true;
            } else if (option.equalsIgnoreCase(WRITE)) {
              constraint.setWriteAccess(true);
              constraint.setInvertWriteAccess(hasNot);
              consumed = true;
            }
          } else {
            m = p3.matcher(token);

            if (m.matches()) {
              String option = m.group(1);

              if (option.equalsIgnoreCase(REGEX) || option.equalsIgnoreCase(REGEXW)) {
                String typePattern = getSingleParameter(m, SSRBundle.message("reg.exp.should.be.delimited.with.spaces.error.message"));
                if (typePattern.isEmpty()) throw new MalformedPatternException(SSRBundle.message("no.reg.exp.specified.error.message"));

                if (StringUtil.startsWithChar(typePattern, '*')) {
                  typePattern = typePattern.substring(1);
                  constraint.setWithinHierarchy(true);
                }
                try {
                  Pattern.compile(typePattern);
                } catch (PatternSyntaxException e) {
                  throw new MalformedPatternException(SSRBundle.message("invalid.regular.expression"));
                }
                constraint.setRegExp( typePattern );
                constraint.setInvertRegExp( hasNot );
                consumed = true;
                if (option.equalsIgnoreCase(REGEXW)) {
                  constraint.setWholeWordsOnly(true);
                }
              } else if (option.equalsIgnoreCase(EXPRTYPE)) {
                String exprTypePattern = getSingleParameter(m, SSRBundle.message(
                  "reg.exp.in.expr.type.should.be.delimited.with.spaces.error.message"));

                if (StringUtil.startsWithChar(exprTypePattern, '*')) {
                  exprTypePattern = exprTypePattern.substring(1);
                  constraint.setExprTypeWithinHierarchy(true);
                }
                try {
                  Pattern.compile(exprTypePattern);
                } catch (PatternSyntaxException e) {
                  throw new MalformedPatternException(SSRBundle.message("invalid.regular.expression"));
                }
                constraint.setNameOfExprType( exprTypePattern );
                constraint.setInvertExprType( hasNot );
                consumed = true;
              } else if (option.equalsIgnoreCase(FORMAL)) {
                String exprTypePattern = getSingleParameter(m, SSRBundle.message(
                  "reg.exp.in.formal.arg.type.should.be.delimited.with.spaces.error.message"));

                if (StringUtil.startsWithChar(exprTypePattern, '*')) {
                  exprTypePattern = exprTypePattern.substring(1);
                  constraint.setFormalArgTypeWithinHierarchy(true);
                }
                try {
                  Pattern.compile(exprTypePattern);
                } catch (PatternSyntaxException e) {
                  throw new MalformedPatternException(SSRBundle.message("invalid.regular.expression"));
                }
                constraint.setNameOfFormalArgType( exprTypePattern );
                constraint.setInvertFormalType( hasNot );
                consumed = true;
              } else if (option.equalsIgnoreCase(SCRIPT)) {
                String script = getSingleParameter(m, SSRBundle.message("script.should.be.delimited.with.spaces.error.message"));

                constraint.setScriptCodeConstraint( script );
                consumed = true;
              } else if (option.equalsIgnoreCase(CONTAINS)) {
                if (hasNot) constraint.setInvertContainsConstraint(true);
                String script = getSingleParameter(m, SSRBundle.message("script.should.be.delimited.with.spaces.error.message"));

                constraint.setContainsConstraint(script );
                consumed = true;
              } else if (option.equalsIgnoreCase(WITHIN)) {
                if (hasNot) constraint.setInvertWithinConstraint(true);
                String script = getSingleParameter(m, SSRBundle.message("script.should.be.delimited.with.spaces.error.message"));

                constraint.setWithinConstraint(script);
                consumed = true;
              }
            }
          }
        }

        if (!consumed) {
          throw new UnsupportedPatternException(
            SSRBundle.message("option.is.not.recognized.error.message", token)
          );
        }
      }
    }

  private static String getSingleParameter(Matcher m, String errorMessage) {
    final String value = m.group(2);
    if (value.isEmpty()) return value;

    if (value.charAt(0)!=' ' || value.charAt(value.length()-1)!=' ') {
      throw new MalformedPatternException(errorMessage);
    }
    return value.trim();
  }
}
