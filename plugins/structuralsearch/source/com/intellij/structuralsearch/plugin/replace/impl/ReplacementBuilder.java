package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchResult;
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
final class ReplacementBuilder extends JavaRecursiveElementWalkingVisitor {
  private String replacement;
  private List<ParameterInfo> parameterizations;
  private HashMap<String,MatchResult> matchMap;
  private final Map<String, ScriptSupport> replacementVarsMap;
  private final ReplaceOptions options;
  //private Map<TextRange,ParameterInfo> scopedParameterizations;

  private static final class ParameterInfo {
    String name;
    int startIndex;
    boolean parameterContext;
    boolean methodParameterContext;
    boolean statementContext;
    boolean variableInitialContext;
    boolean classContext;
    int afterDelimiterPos;
    boolean hasCommaBefore;
    int beforeDelimiterPos;
    boolean hasCommaAfter;

    boolean scopeParameterization;
    boolean replacementVariable;
    
    PsiElement myElement;
  }

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
      info.startIndex = offset;
      info.name = name;
      info.replacementVariable = options.getVariableDefinition(name) != null;

      // find delimiter
      int pos;
      for(pos = offset-1; pos >=0 && pos < replacement.length() && Character.isWhitespace(replacement.charAt(pos));) {
        --pos;
      }

      if (pos >= 0) {
        if (replacement.charAt(pos) == ',') {
          info.hasCommaBefore = true;
        }
        info.beforeDelimiterPos = pos;
      }

      for(pos = offset; pos < replacement.length() && Character.isWhitespace(replacement.charAt(pos));) {
        ++pos;
      }

      if (pos < replacement.length()) {
        final char ch = replacement.charAt(pos);

        if (ch == ';') {
          info.statementContext = true;
        }
        else if (ch == ',' || ch == ')') {
          info.parameterContext = true;
          info.hasCommaAfter = ch == ',';
        }
        else if (ch == '}') {
          info.classContext = true;
        }
        info.afterDelimiterPos = pos;
      }

      if (parameterizations==null) {
        parameterizations = new ArrayList<ParameterInfo>();
      }

      parameterizations.add(info);
    }

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
        patternNode.accept(this);
      }
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException();
    }
  }

  private void fill(MatchResult r,Map<String,MatchResult> m) {
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
      MatchResult r = matchMap.get(info.name);
      if (info.replacementVariable) {
        offset = insertSubstitution(result, offset, info, generateReplacement(info, match));
      } else
      if (r != null) {
        offset = handleSubstitution(info, r, result, offset);
      }
      else {
        if (info.hasCommaBefore) {
          result.delete(info.beforeDelimiterPos + offset, info.beforeDelimiterPos + 1 + offset);
          --offset;
        }
        else if (info.hasCommaAfter) {
          result.delete(info.afterDelimiterPos + offset, info.afterDelimiterPos + 1 + offset);
          --offset;
        }
        else if (info.variableInitialContext) {
          //if (info.afterDelimiterPos > 0) {
            result.delete(info.beforeDelimiterPos + offset, info.afterDelimiterPos + offset - 1);
            offset -= (info.afterDelimiterPos - info.beforeDelimiterPos - 1);
          //}
        } else if (info.statementContext) {
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
    ScriptSupport scriptSupport = replacementVarsMap.get(info.name);

    if (scriptSupport == null) {
      String constraint = options.getVariableDefinition(info.name).getScriptCodeConstraint();
      scriptSupport = new ScriptSupport(StringUtil.stripQuotesAroundValue(constraint));
      replacementVarsMap.put(info.name, scriptSupport);
    }
    return scriptSupport.evaluate((MatchResultImpl)match, null);
  }

  private int insertSubstitution(StringBuilder result, int offset, final ParameterInfo info, String image) {
    if (image.length() > 0) result.insert(offset+info.startIndex,image);
    offset += image.length();
    return offset;
  }

  private int handleSubstitution(final ParameterInfo info, MatchResult match, StringBuilder result, int offset) {
    if (info.name.equals(match.getName())) {
      String replacementString = match.getMatchImage();
      boolean forceAddingNewLine = false;

      if (info.methodParameterContext) {
        StringBuilder buf = new StringBuilder();
        handleMethodParameter(buf,info);
        replacementString = buf.toString();
      } else
      if (match.getAllSons().size() > 0 && !match.isScopeMatch()) {
        // compound matches
        StringBuilder buf = new StringBuilder();
        MatchResult previous;
        MatchResult r = null;

        for (final MatchResult matchResult : match.getAllSons()) {
          previous = r;
          r = matchResult;

          final PsiElement currentElement = r.getMatch();

          if (buf.length() > 0) {
            if (info.statementContext) {
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
            else if (info.parameterContext) {
              buf.append(',');
            }
            else if (info.classContext) {
              buf.append('\n');
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
        if (info.statementContext) {
          forceAddingNewLine = match.getMatch() instanceof PsiComment;
        }
        buf.append(replacementString);
        removeExtraSemicolonForSingleVarInstanceInMultipleMatch(info, match, buf);
        replacementString = buf.toString();
      }

      offset = insertSubstitution(result,offset,info,replacementString);
      offset = removeExtraSemicolon(info, offset, result,match);
      if (forceAddingNewLine && info.statementContext) {
        result.insert(info.startIndex+offset+1,'\n');
        offset ++;
      }
    }
    return offset;
  }

  private int removeExtraSemicolon(ParameterInfo info, int offset, StringBuilder result, MatchResult match) {
    if (info.statementContext) {
      int index = offset+info.startIndex;
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
    if (info.statementContext) {
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

  private ParameterInfo findParameterization(String name) {
    if (parameterizations==null) return null;

    for (final ParameterInfo info : parameterizations) {

      if (info.name.equals(name)) {
        return info;
      }
    }

    return null;
  }

  private void handleMethodParameter(StringBuilder buf, ParameterInfo info) {
    if(info.myElement==null) {
      // no specific handling for name of method parameter since it is handled with type
      return;
    }

    String name = ((PsiParameter)info.myElement.getParent()).getName();
    name = StructuralSearchUtil.isTypedVariable(name) ? stripTypedVariableDecoration(name):name;

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

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitElement(expression);
  }

  @Override public void visitVariable(PsiVariable field) {
    super.visitVariable(field);

    final PsiExpression initializer = field.getInitializer();

    if (parameterizations!=null && initializer!=null) {
      final String initText = initializer.getText();

      if(StructuralSearchUtil.isTypedVariable(initText)) {
        final ParameterInfo initInfo = findParameterization(stripTypedVariableDecoration(initText));

        if (initInfo != null) {
          initInfo.variableInitialContext = true;
        }
      }
    }
  }

  @Override public void visitMethod(PsiMethod method) {
    super.visitMethod(method);

    String name = method.getName();
    if (StructuralSearchUtil.isTypedVariable(name)) {
      name = stripTypedVariableDecoration(name);

      ParameterInfo methodInfo = findParameterization(name);
      methodInfo.scopeParameterization = true;
      //if (scopedParameterizations != null) scopedParameterizations.put(method.getTextRange(), methodInfo);
    }
  }

  @Override public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    if (parameterizations!=null) {
      String name = parameter.getName();
      String type = parameter.getTypeElement().getText();

      if (StructuralSearchUtil.isTypedVariable(name)) {
        name = stripTypedVariableDecoration(name);

        if (StructuralSearchUtil.isTypedVariable(type)) {
          type = stripTypedVariableDecoration(type);
        }
        ParameterInfo nameInfo = findParameterization(name);
        ParameterInfo typeInfo = findParameterization(type);

        if (nameInfo!=null && typeInfo!=null && !(parameter.getParent() instanceof PsiCatchSection)) {
          nameInfo.parameterContext=false;
          typeInfo.parameterContext=false;
          typeInfo.methodParameterContext=true;
          nameInfo.methodParameterContext=true;
          typeInfo.myElement = parameter.getTypeElement();
        }
      }
    }
  }

  private String stripTypedVariableDecoration(final String type) {
    return type.substring(1,type.length()-1);
  }

}
