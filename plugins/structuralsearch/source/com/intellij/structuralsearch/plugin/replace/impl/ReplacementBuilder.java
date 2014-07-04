package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
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

  String process(MatchResult match, ReplacementInfoImpl replacementInfo, FileType type) {
    if (parameterizations==null) {
      return replacement;
    }

    final StringBuilder result = new StringBuilder(replacement);
    matchMap = new HashMap<String,MatchResult>();
    fill(match, matchMap);

    int offset = 0;

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(type);

    for (final ParameterInfo info : parameterizations) {
      MatchResult r = matchMap.get(info.getName());
      if (info.isReplacementVariable()) {
        offset = Replacer.insertSubstitution(result, offset, info, generateReplacement(info, match));
      }
      else if (r != null) {
        offset = profile != null ? profile.handleSubstitution(info, r, result, offset, matchMap) : StructuralSearchProfile.defaultHandleSubstitution(info, r, result, offset);
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
        } else if (profile != null) {
          offset = profile.processAdditionalOptions(info, offset, result, r);
        }
        offset = Replacer.insertSubstitution(result, offset, info, "");
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

  public ParameterInfo findParameterization(String name) {
    if (parameterizations==null) return null;

    for (final ParameterInfo info : parameterizations) {

      if (info.getName().equals(name)) {
        return info;
      }
    }

    return null;
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
