package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * @author maxim
 * Date: 24.02.2004
 * Time: 15:34:57
 */
public final class ReplacementBuilder {
  private String replacement;
  private List<ParameterInfo> parameterizations;
  private HashMap<String,MatchResult> matchMap;
  private final Map<String, ScriptSupport> replacementVarsMap;
  private final ReplaceOptions options;
  //private Map<TextRange,ParameterInfo> scopedParameterizations;

  ReplacementBuilder(final Project project,final ReplaceOptions options) {
    replacementVarsMap = new HashMap<String, ScriptSupport>();
    this.options = options;
    String _replacement = options.getReplacement();
    FileType fileType = options.getMatchOptions().getFileType();

    final Template template = TemplateManager.getInstance(project).createTemplate("","",_replacement);

    final int segmentsCount = template.getSegmentsCount();
    replacement = template.getTemplateText();

    for(int i=0;i<segmentsCount;++i) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);

      final ParameterInfo info = new ParameterInfo();
      info.setStartIndex(offset);
      info.setName(name);
      info.setReplacementVariable(options.getVariableDefinition(name) != null);

      // find delimiter
      int pos;
      for(pos = offset-1; pos >=0 && pos < replacement.length() && Character.isWhitespace(replacement.charAt(pos));) {
        --pos;
      }

      if (pos >= 0) {
        if (replacement.charAt(pos) == ',') {
          info.setHasCommaBefore(true);
        }
        info.setBeforeDelimiterPos(pos);
      }

      for(pos = offset; pos < replacement.length() && Character.isWhitespace(replacement.charAt(pos));) {
        ++pos;
      }

      if (pos < replacement.length()) {
        final char ch = replacement.charAt(pos);

        if (ch == ';') {
          info.setStatementContext(true);
        }
        else if (ch == ',' || ch == ')') {
          info.setParameterContext(true);
          info.setHasCommaAfter(ch == ',');
        }
        info.setAfterDelimiterPos(pos);
      }

      if (parameterizations==null) {
        parameterizations = new ArrayList<ParameterInfo>();
      }

      parameterizations.add(info);
    }

    final StructuralSearchProfile profile = parameterizations != null ? StructuralSearchUtil.getProfileByFileType(fileType) : null;
    if (profile != null) {
      try {
        final PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          _replacement,
          PatternTreeContext.Block,
          fileType,
          options.getMatchOptions().getDialect(),
          options.getMatchOptions().getPatternContext(),
          project,
          false
        );
        if (elements.length > 0) {
          final PsiElement patternNode = elements[0].getParent();
          profile.provideAdditionalReplaceOptions(patternNode, options, this);
        }
      } catch (IncorrectOperationException e) {
        throw new MalformedPatternException();
      }
    }
  }

  private static void fill(MatchResult r,Map<String,MatchResult> m) {
    if (r.getName()!=null) {
      if (m.get(r.getName()) == null) {
        m.put(r.getName(), r);
      }
    }

    if (!r.isScopeMatch() || !r.isMultipleMatch()) {
      for (final MatchResult matchResult : r.getAllSons()) {
        fill(matchResult, m);
      }
    } else if (r.hasSons()) {
      final List<MatchResult> allSons = r.getAllSons();
      if (allSons.size() > 0) {
        fill(allSons.get(0),m);
      }
    }
  }

  String process(MatchResult match, ReplacementInfoImpl replacementInfo) {
    if (parameterizations==null) {
      return replacement;
    }

    final StringBuilder result = new StringBuilder(replacement);
    matchMap = new HashMap<String,MatchResult>();
    fill(match, matchMap);

    int offset = 0;

    for (final ParameterInfo info : parameterizations) {
      MatchResult r = matchMap.get(info.getName());
      if (info.isReplacementVariable()) {
        offset = insertSubstitution(result, offset, info, generateReplacement(info, match));
      }
      else if (r != null) {
        offset = handleSubstitution(info, r, result, offset);
      }
      else {
        if (info.isHasCommaBefore()) {
          result.delete(info.getBeforeDelimiterPos() + offset, info.getBeforeDelimiterPos() + 1 + offset);
          --offset;
        }
        else if (info.isHasCommaAfter()) {
          result.delete(info.getAfterDelimiterPos() + offset, info.getAfterDelimiterPos() + 1 + offset);
          --offset;
        }
        else if (info.isVariableInitialContext()) {
          //if (info.afterDelimiterPos > 0) {
            result.delete(info.getBeforeDelimiterPos() + offset, info.getAfterDelimiterPos() + offset - 1);
            offset -= (info.getAfterDelimiterPos() - info.getBeforeDelimiterPos() - 1);
          //}
        } else if (info.isStatementContext()) {
          offset = removeExtraSemicolon(info, offset, result, r);
        }
        offset = insertSubstitution(result, offset, info, "");
      }
    }

    replacementInfo.variableMap = (HashMap<String, MatchResult>)matchMap.clone();
    matchMap.clear();
    return result.toString();
  }

  private String generateReplacement(ParameterInfo info, MatchResult match) {
    ScriptSupport scriptSupport = replacementVarsMap.get(info.getName());

    if (scriptSupport == null) {
      String constraint = options.getVariableDefinition(info.getName()).getScriptCodeConstraint();
      scriptSupport = new ScriptSupport(StringUtil.stripQuotesAroundValue(constraint), info.getName());
      replacementVarsMap.put(info.getName(), scriptSupport);
    }
    return scriptSupport.evaluate((MatchResultImpl)match, null);
  }

  private static int insertSubstitution(StringBuilder result, int offset, final ParameterInfo info, String image) {
    if (image.length() > 0) result.insert(offset+ info.getStartIndex(),image);
    offset += image.length();
    return offset;
  }

  private int handleSubstitution(final ParameterInfo info, MatchResult match, StringBuilder result, int offset) {
    if (info.getName().equals(match.getName())) {
      String replacementString = match.getMatchImage();
      boolean forceAddingNewLine = false;

      if (info.isMethodParameterContext()) {
        StringBuilder buf = new StringBuilder();
        handleMethodParameter(buf, info);
        replacementString = buf.toString();
      }
      else if (match.getAllSons().size() > 0 && !match.isScopeMatch()) {
        // compound matches
        StringBuilder buf = new StringBuilder();
        MatchResult r = null;

        for (final MatchResult matchResult : match.getAllSons()) {
          MatchResult previous = r;
          r = matchResult;

          final PsiElement currentElement = r.getMatch();

          if (buf.length() > 0) {
            final PsiElement parent = currentElement.getParent();
            if (info.isStatementContext()) {
              final PsiElement previousElement = previous.getMatchRef().getElement();

              if (!(previousElement instanceof PsiComment) &&
                  ( buf.charAt(buf.length() - 1) != '}' ||
                    previousElement instanceof PsiDeclarationStatement
                  )
                ) {
                buf.append(';');
              }

              final PsiElement prevSibling = currentElement.getPrevSibling();

              if (prevSibling instanceof PsiWhiteSpace &&
                  prevSibling.getPrevSibling() == previous.getMatch()
                ) {
                // consequent statements matched so preserve whitespacing
                buf.append(prevSibling.getText());
              }
              else {
                buf.append('\n');
              }
            }
            else if (info.isParameterContext()) {
              buf.append(',');
            }
            else if (parent instanceof PsiClass) {
              final PsiElement prevSibling = PsiTreeUtil.skipSiblingsBackward(currentElement, PsiWhiteSpace.class);
              if (prevSibling instanceof PsiJavaToken && JavaTokenType.COMMA.equals(((PsiJavaToken)prevSibling).getTokenType())) {
                buf.append(',');
              }
              else {
                buf.append('\n');
              }
            }
            else if (parent instanceof PsiReferenceList) {
              buf.append(',');
            }
            else {
              buf.append(' ');
            }
          }

          buf.append(r.getMatchImage());
          removeExtraSemicolonForSingleVarInstanceInMultipleMatch(info, r, buf);
          forceAddingNewLine = currentElement instanceof PsiComment;
        }

        replacementString = buf.toString();
      } else {
        StringBuilder buf = new StringBuilder();
        if (info.isStatementContext()) {
          forceAddingNewLine = match.getMatch() instanceof PsiComment;
        }
        buf.append(replacementString);
        removeExtraSemicolonForSingleVarInstanceInMultipleMatch(info, match, buf);
        replacementString = buf.toString();
      }

      offset = insertSubstitution(result,offset,info,replacementString);
      offset = removeExtraSemicolon(info, offset, result, match);
      if (forceAddingNewLine && info.isStatementContext()) {
        result.insert(info.getStartIndex() + offset + 1, '\n');
        offset ++;
      }
    }
    return offset;
  }

  private static int removeExtraSemicolon(ParameterInfo info, int offset, StringBuilder result, MatchResult match) {
    if (info.isStatementContext()) {
      int index = offset+ info.getStartIndex();
      if (result.charAt(index)==';' &&
          ( match == null ||
            ( result.charAt(index-1)=='}' &&
              !(match.getMatch() instanceof PsiDeclarationStatement) && // array init in dcl
              !(match.getMatch() instanceof PsiNewExpression) // array initializer
            ) ||
            ( !match.isMultipleMatch() &&                                                // ; in comment
              match.getMatch() instanceof PsiComment
            ) ||
            ( match.isMultipleMatch() &&                                                 // ; in comment
              match.getAllSons().get( match.getAllSons().size() - 1 ).getMatch() instanceof PsiComment
            ) 
           )   
         ) {
        result.deleteCharAt(index);
        --offset;
      }
    }

    return offset;
  }

  private static void removeExtraSemicolonForSingleVarInstanceInMultipleMatch(final ParameterInfo info, MatchResult r, StringBuilder buf) {
    if (info.isStatementContext()) {
      final PsiElement element = r.getMatchRef().getElement();

      // remove extra ;
      if (buf.charAt(buf.length()-1)==';' &&
          r.getMatchImage().charAt(r.getMatchImage().length()-1)==';' &&
          ( element instanceof PsiReturnStatement ||
            element instanceof PsiDeclarationStatement ||
            element instanceof PsiExpressionStatement ||
            element instanceof PsiAssertStatement ||
            element instanceof PsiBreakStatement ||
            element instanceof PsiContinueStatement ||
            element instanceof PsiMember ||
            element instanceof PsiIfStatement && !(((PsiIfStatement)element).getThenBranch() instanceof PsiBlockStatement) ||
            element instanceof PsiLoopStatement && !(((PsiLoopStatement)element).getBody() instanceof PsiBlockStatement)
          )
         ) {
        // contains extra ;
        buf.deleteCharAt(buf.length()-1);
      }
    }
  }

  public ParameterInfo findParameterization(String name) {
    if (parameterizations==null) return null;

    for (final ParameterInfo info : parameterizations) {

      if (info.getName().equals(name)) {
        return info;
      }
    }

    return null;
  }

  private void handleMethodParameter(StringBuilder buf, ParameterInfo info) {
    if(info.getElement() ==null) {
      // no specific handling for name of method parameter since it is handled with type
      return;
    }

    String name = ((PsiParameter)info.getElement().getParent()).getName();
    name = StructuralSearchUtil.isTypedVariable(name) ? Replacer.stripTypedVariableDecoration(name):name;

    final MatchResult matchResult = matchMap.get(name);
    if (matchResult == null) return;

    if (matchResult.isMultipleMatch()) {
      for(Iterator<MatchResult> i = matchResult.getAllSons().iterator();
          i.hasNext();
        ) {
        if (buf.length()>0) {
          buf.append(',');
        }

        appendParameter(buf, i.next());
      }
    } else {
      appendParameter(buf, matchResult);
    }
  }

  private static void appendParameter(final StringBuilder buf, final MatchResult _matchResult) {
    for(Iterator<MatchResult> j = _matchResult.getAllSons().iterator();j.hasNext();) {
      buf.append(
        j.next().getMatchImage()
      );

      buf.append(' ');

      buf.append(
        j.next().getMatchImage()
      );
    }
  }

  public void clear() {
    replacement = null;

    if (parameterizations!=null) {
      parameterizations.clear();
      parameterizations = null;
    }
  }

  public void addParametrization(ParameterInfo e) {
    assert parameterizations != null;
    parameterizations.add(e);
  }
}
