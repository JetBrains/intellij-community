package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.02.2004
 * Time: 15:34:57
 * To change this template use File | Settings | File Templates.
 */
final class ReplacementBuilder extends PsiRecursiveElementVisitor {
  private String replacement;
  private List<ParameterInfo> parameterizations;
  private HashMap<String,MatchResult> map;
  private Map<TextRange,ParameterInfo> scopedParameterizations;

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
    
    PsiElement myElement;
  }

  ReplacementBuilder(final Project project,final String _replacement, final FileType fileType) {
    //this.project = project;
    final Template template = TemplateManager.getInstance(project).createTemplate("","",_replacement);

    final int segmentsCount = template.getSegmentsCount();
    replacement = template.getTemplateText();

    for(int i=0;i<segmentsCount;++i) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);

      final ParameterInfo info = new ParameterInfo();
      info.startIndex = offset;
      info.name = name;

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
        MatcherImplUtil.TreeContext.Block,
        fileType,
        project
      );
      if (elements.length > 0) {
        final PsiElement patternNode = elements[0].getParent();
        patternNode.accept(this);
      }
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException();
    }
  }

  private void fill(MatchResult r,HashMap<String,MatchResult> m) {
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

    final StringBuffer result = new StringBuffer(replacement);
    map = new HashMap<String,MatchResult>();
    fill(match,map);

    int offset = 0;

    for (final ParameterInfo info : parameterizations) {
      MatchResult r = map.get(info.name);
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
          result.delete(info.beforeDelimiterPos + offset, info.afterDelimiterPos + offset - 1);
          offset -= (info.afterDelimiterPos - info.beforeDelimiterPos - 1);
        } else if (info.statementContext) {
          offset = removeExtraSemicolon2(info, offset, result, r);
        }
        offset = insertSubstitution(result, offset, info, "");
      }

    }

    replacementInfo.variableMap = (Map<String, MatchResult>)map.clone();
    map.clear();
    return result.toString();
  }

  private int insertSubstitution(StringBuffer result, int offset, final ParameterInfo info, String image) {
    result.insert(offset+info.startIndex,image);
    offset += image.length();
    return offset;
  }

  private int handleSubstitution(final ParameterInfo info, MatchResult match, StringBuffer result, int offset) {
    if (info.name.equals(match.getName())) {
      String replacementString = match.getMatchImage();
      boolean forceAddingNewLine = false;

      if (match.getAllSons().size() > 0 && !match.isScopeMatch()) {
        // compound matches
        StringBuffer buf = new StringBuffer();
        MatchResult previous;
        MatchResult r = null;

        if (info.methodParameterContext) {
          handleMethodParameter(buf,info);
        } else {
          for (final MatchResult matchResult : match.getAllSons()) {
            previous = r;
            r = matchResult;

            if (buf.length() > 0) {
              if (info.statementContext) {
                if (!(previous.getMatchRef().getElement() instanceof PsiComment) &&
                    buf.charAt(buf.length() - 1) != '}'
                  ) {
                  buf.append(';');
                }

                final PsiElement prevSibling = r.getMatch().getPrevSibling();

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
            removeExtraSemicolon(info, r, buf);
            forceAddingNewLine = r.getMatch() instanceof PsiComment;
          }
        }

        replacementString = buf.toString();
      } else {
        StringBuffer buf = new StringBuffer();
        if (info.statementContext) {
          forceAddingNewLine = match.getMatch() instanceof PsiComment;
        }
        buf.append(replacementString);
        removeExtraSemicolon(info, match, buf);
        replacementString = buf.toString();
      }

      offset = insertSubstitution(result,offset,info,replacementString);
      offset = removeExtraSemicolon2(info, offset, result,match);
      if (forceAddingNewLine && info.statementContext) {
        result.insert(info.startIndex+offset+1,'\n');
        offset ++;
      }
    }
    return offset;
  }

  private int removeExtraSemicolon2(ParameterInfo info, int offset, StringBuffer result, MatchResult match) {
    if (info.statementContext) {
      int index = offset+info.startIndex;
      if (result.charAt(index)==';' &&
          ( match == null ||
            ( result.charAt(index-1)=='}' &&
              !(match.getMatch() instanceof PsiDeclarationStatement) // array init in dcl
            ) ||
            ( !match.isMultipleMatch() &&
              match.getMatch() instanceof PsiComment
            ) ||
            ( match.isMultipleMatch() &&
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

  private void removeExtraSemicolon(final ParameterInfo info, MatchResult r, StringBuffer buf) {
    if (info.statementContext) {
      final PsiElement element = r.getMatchRef().getElement();

      // remove extra ;
      if (buf.charAt(buf.length()-1)==';' &&
          r.getMatchImage().charAt(r.getMatchImage().length()-1)==';' &&
          ( element instanceof PsiReturnStatement ||
            element instanceof PsiDeclarationStatement ||
            element instanceof PsiAssertStatement ||
            element instanceof PsiMember ||
            false //( element instanceof PsiComment &&
            //  ((PsiComment)element).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT
            //)
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

  private void handleMethodParameter(StringBuffer buf, ParameterInfo info) {
    if(info.myElement==null) {
      // no specific handling for name of method parameter since it is handled with type
      return;
    }

    String name = ((PsiParameter)info.myElement.getParent()).getName();
    name = stripTypedVariableDecoration(name);

    for(Iterator<MatchResult> i = map.get(name).getAllSons().iterator(), j = map.get(info.name).getAllSons().iterator();
        i.hasNext() && j.hasNext();
      ) {
      if (buf.length()>0) {
        buf.append(',');
      }
      buf.append(
        j.next().getMatchImage()
      );

      buf.append(' ');

      buf.append(
        i.next().getMatchImage()
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

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitElement(expression);
  }

  public void visitVariable(PsiVariable field) {
    super.visitVariable(field);

    final PsiExpression initializer = field.getInitializer();

    if (parameterizations!=null && initializer!=null) {
      final String initText = initializer.getText();

      if(isTypedVariable(initText)) {
        final ParameterInfo initInfo = findParameterization(stripTypedVariableDecoration(initText));

        if (initInfo != null) {
          initInfo.variableInitialContext = true;
        }
      }
    }
  }

  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);

    String name = method.getName();
    if (isTypedVariable(name)) {
      name = stripTypedVariableDecoration(name);

      ParameterInfo methodInfo = findParameterization(name);
      methodInfo.scopeParameterization = true;
      if (scopedParameterizations != null) scopedParameterizations.put(method.getTextRange(), methodInfo);
    }
  }

  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    if (parameterizations!=null) {
      String name = parameter.getName();
      String type = parameter.getTypeElement().getText();

      if (isTypedVariable(name)) {
        name = stripTypedVariableDecoration(name);

        if (isTypedVariable(type)) {
          type = stripTypedVariableDecoration(type);
        }
        ParameterInfo nameInfo = findParameterization(name);
        ParameterInfo typeInfo = findParameterization(type);

        if (nameInfo!=null && typeInfo!=null) {
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

  static boolean isTypedVariable(final String name) {
    return name.charAt(0)=='$' && name.charAt(name.length()-1)=='$';
  }
}
