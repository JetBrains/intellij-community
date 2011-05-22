package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Mossienko
 * Date: Mar 4, 2004
 * Time: 9:19:34 PM
 */
public class ReplacerImpl {
  private final Project project;
  private ReplacementBuilder replacementBuilder;
  private ReplaceOptions options;
  private ReplacementContext context;
  private StructuralReplaceHandler replaceHandler;

  protected ReplacerImpl(Project project, ReplaceOptions options) {
    this.project = project;
    this.options = options;
  }

  protected String testReplace(String in, String what, String by, ReplaceOptions options,boolean filePattern) {
    FileType type = options.getMatchOptions().getFileType();
    return testReplace(in, what, by, options, filePattern, false, type, null);
  }

  protected String testReplace(String in, String what, String by, ReplaceOptions options,boolean filePattern, boolean createPhysicalFile, 
                               FileType sourceFileType, Language sourceDialect) {
    this.options = options;
    this.options.getMatchOptions().setSearchPattern(what);
    this.options.setReplacement(by);
    replacementBuilder=null;
    context = null;
    replaceHandler = null;

    this.options.getMatchOptions().clearVariableConstraints();
    MatcherImplUtil.transform(this.options.getMatchOptions());

    checkSupportedReplacementPattern(project, options);

    Matcher matcher = new Matcher(project);
    try {
      PsiElement firstElement, lastElement, parent;

      if (options.getMatchOptions().getScope() == null) {
        PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          in,
          filePattern ? PatternTreeContext.File : PatternTreeContext.Block,
          sourceFileType, sourceDialect, null,
          project,
          createPhysicalFile
        );

        firstElement = elements[0];
        lastElement = elements[elements.length-1];
        parent = firstElement.getParent();

        this.options.getMatchOptions().setScope(
          new LocalSearchScope(parent)
        );
      } else {
        parent = ((LocalSearchScope)options.getMatchOptions().getScope()).getScope()[0];
        firstElement = parent.getFirstChild();
        lastElement = parent.getLastChild();
      }

      this.options.getMatchOptions().setResultIsContextMatch(true);
      CollectingMatchResultSink sink = new CollectingMatchResultSink();
      matcher.testFindMatches(new FilteringMatchResultSink(sink), this.options.getMatchOptions());

      final List<ReplacementInfo> resultPtrList = new LinkedList<ReplacementInfo>();

      for (final MatchResult result : sink.getMatches()) {
        resultPtrList.add(buildReplacement(result));
      }

      sink.getMatches().clear();

      int startOffset = firstElement.getTextRange().getStartOffset();
      int endOffset = filePattern ?0: parent.getTextLength() - (lastElement.getTextRange().getEndOffset());

      // get nodes from text may contain
      PsiElement prevSibling = firstElement.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace) {
        startOffset -= prevSibling.getTextLength() - 1;
      }

      PsiElement nextSibling = lastElement.getNextSibling();
      if (nextSibling instanceof PsiWhiteSpace) {
        endOffset -= nextSibling.getTextLength() - 1;
      }

      replaceAll(resultPtrList);

      String result = parent.getText();
      result = result.substring(startOffset);
      result = result.substring(0,result.length() - endOffset);

      return result;
    } catch(Exception ex) {
      throw new IncorrectOperationException("Unexpected failure:", ex);
    } finally {
      options.getMatchOptions().setScope(null);
    }
  }

  protected void replaceAll(final List<ReplacementInfo> resultPtrList) {
    PsiElement lastAffectedElement = null;
    PsiElement currentAffectedElement;

    for (ReplacementInfo info : resultPtrList) {
      PsiElement element = info.getMatch(0);
      initContextAndHandler(element);
      if (replaceHandler != null) {
        replaceHandler.prepare(info);
      }
    }

    for (final ReplacementInfo aResultPtrList : resultPtrList) {
      currentAffectedElement = doReplace(aResultPtrList);

      if (currentAffectedElement != lastAffectedElement) {
        if (lastAffectedElement != null) reformatAndShortenRefs(lastAffectedElement);
        lastAffectedElement = currentAffectedElement;
      }
    }

    reformatAndShortenRefs(lastAffectedElement);
  }

  protected void replace(ReplacementInfo info) {
    reformatAndShortenRefs(doReplace(info));
  }

  @Nullable
  private PsiElement doReplace(final ReplacementInfo info) {
    final ReplacementInfoImpl replacementInfo = (ReplacementInfoImpl)info;
    final PsiElement element = replacementInfo.matchesPtrList.get(0).getElement();

    if (element==null || !element.isWritable() || !element.isValid()) return null;

    final PsiElement elementParent = element.getParent();

    //noinspection HardCodedStringLiteral
    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                doReplace(element, replacementInfo);
              }
            }
          );
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        }
      },
      "ssreplace",
      "test"
    );

    if (!elementParent.isValid() || !elementParent.isWritable()) {
      return null;
    }

    return elementParent;
  }

  private void reformatAndShortenRefs(final PsiElement elementParent) {
    if (elementParent == null) return;
    final Runnable action = new Runnable() {
      public void run() {
        CodeStyleManager codeStyleManager = PsiManager.getInstance(project).getCodeStyleManager();
        final PsiFile containingFile = elementParent.getContainingFile();

        if (containingFile != null) {

          if (options.isToShortenFQN()) {
            if (containingFile.getVirtualFile() != null) {
              PsiDocumentManager.getInstance(project)
                .commitDocument(FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile()));
            }

            JavaCodeStyleManager.getInstance(project).shortenClassReferences(elementParent, 0, elementParent.getTextLength());
          }

          if (options.isToReformatAccordingToStyle()) {
            if (containingFile.getVirtualFile() != null) {
              PsiDocumentManager.getInstance(project)
                .commitDocument(FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile()));
            }

            final int paretOffset = elementParent.getTextRange().getStartOffset();

            codeStyleManager.reformatRange(containingFile, paretOffset, paretOffset + elementParent.getTextLength(), true);
          }
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(action);
        }
      },
      "reformat and shorten refs after ssr",
      "test"
    );
  }

  private void doReplace(final PsiElement elementToReplace,
                         final ReplacementInfoImpl info) {
    PsiManager.getInstance(project).performActionWithFormatterDisabled(new Runnable() {
        public void run() {
          initContextAndHandler(elementToReplace);

          context.replacementInfo = info;

          if (replaceHandler != null) {
            replaceHandler.replace(info);
          }
        }
      }
    );
  }

  private void initContextAndHandler(PsiElement psiContext) {
    if (context == null) {
      context = new ReplacementContext(options, project);
    }
    if (replaceHandler == null) {
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(psiContext);
      if (profile != null) {
        replaceHandler = profile.getReplaceHandler(this.context);
      }
    }
  }

  public static void handleComments(final PsiElement el, final PsiElement replacement, ReplacementContext context) throws IncorrectOperationException {
    ReplacementInfoImpl replacementInfo = context.replacementInfo;
    if (replacementInfo.elementToVariableNameMap == null) {
      replacementInfo.elementToVariableNameMap = new HashMap<PsiElement, String>(1);
      Map<String, MatchResult> variableMap = replacementInfo.variableMap;
      if (variableMap != null) {
        for(String name:variableMap.keySet()) {
          fill(name,replacementInfo.variableMap.get(name),replacementInfo.elementToVariableNameMap);
        }
      }
    }

    PsiElement lastChild = el.getLastChild();
    if (lastChild instanceof PsiComment &&
        replacementInfo.elementToVariableNameMap.get(lastChild) == null &&
        !(replacement.getLastChild() instanceof PsiComment)
      ) {
      PsiElement firstElementAfterStatementEnd = lastChild;
      for(PsiElement curElement=firstElementAfterStatementEnd.getPrevSibling();curElement!=null;curElement = curElement.getPrevSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        firstElementAfterStatementEnd = curElement;
      }
      replacement.addRangeAfter(firstElementAfterStatementEnd,lastChild,replacement.getLastChild());
    }

    final PsiElement firstChild = el.getFirstChild();
    if (firstChild instanceof PsiComment &&
        !(firstChild instanceof PsiDocComment) &&
        replacementInfo.elementToVariableNameMap.get(firstChild) == null
        ) {
      PsiElement lastElementBeforeStatementStart = firstChild;

      for(PsiElement curElement=lastElementBeforeStatementStart.getNextSibling();curElement!=null;curElement = curElement.getNextSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        lastElementBeforeStatementStart = curElement;
      }
      replacement.addRangeBefore(firstChild,lastElementBeforeStatementStart,replacement.getFirstChild());
    }
  }

  private static void fill(final String name, final MatchResult matchResult, final Map<PsiElement, String> elementToVariableNameMap) {
    boolean b = matchResult.isMultipleMatch() || matchResult.isScopeMatch();
    if(matchResult.hasSons() && b) {
      for(MatchResult r:matchResult.getAllSons()) {
        fill(name, r, elementToVariableNameMap);
      }
    } else if (!b && matchResult.getMatchRef() != null)  {
      elementToVariableNameMap.put(matchResult.getMatch(),name);
    }
  }

  public static void checkSupportedReplacementPattern(Project project, ReplaceOptions options) throws UnsupportedPatternException {
    try {
      String search = options.getMatchOptions().getSearchPattern();
      String replacement = options.getReplacement();
      FileType fileType = options.getMatchOptions().getFileType();
      Template template = TemplateManager.getInstance(project).createTemplate("","",search);
      Template template2 = TemplateManager.getInstance(project).createTemplate("","",replacement);

      int segmentCount = template2.getSegmentsCount();
      for(int i=0;i<segmentCount;++i) {
        final String replacementSegmentName = template2.getSegmentName(i);
        final int segmentCount2  = template.getSegmentsCount();
        int j;

        for(j=0;j<segmentCount2;++j) {
          final String searchSegmentName = template.getSegmentName(j);

          if (replacementSegmentName.equals(searchSegmentName)) break;

          // Reference to
          if (replacementSegmentName.startsWith(searchSegmentName) &&
              replacementSegmentName.charAt(searchSegmentName.length())=='_'
             ) {
            try {
              Integer.parseInt(replacementSegmentName.substring(searchSegmentName.length()+1));
              break;
            } catch(NumberFormatException ex) {}
          }
        }

        if (j==segmentCount2) {
          ReplacementVariableDefinition definition = options.getVariableDefinition(replacementSegmentName);

          if (definition == null || definition.getScriptCodeConstraint().length() <= 2 /*empty quotes*/) {
            throw new UnsupportedPatternException(
              SSRBundle.message("replacement.variable.is.not.defined.message", replacementSegmentName)
            );
          } else {
            String message = ScriptSupport.checkValidScript(StringUtil.stripQuotesAroundValue(definition.getScriptCodeConstraint()));
            if (message != null) {
              throw new UnsupportedPatternException(
                SSRBundle.message("replacement.variable.is.not.valid", replacementSegmentName, message)
              );
            }
          }
        }
      }

      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);

      profile.checkReplacementPattern(project, options);

    } catch(IncorrectOperationException ex) {
      throw new UnsupportedPatternException(SSRBundle.message("incorrect.pattern.message"));
    }
  }

  protected ReplacementInfo buildReplacement(MatchResult result) {
    List<SmartPsiElementPointer> l = new ArrayList<SmartPsiElementPointer>();
    SmartPointerManager manager = SmartPointerManager.getInstance(project);

    if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
      for(Iterator<MatchResult> i=result.getAllSons().iterator();i.hasNext();) {
        final MatchResult r = i.next();

        if (MatchResult.LINE_MATCH.equals(r.getName())) {
          PsiElement element = r.getMatchRef().getElement();

          if (element instanceof PsiDocComment) { // doc comment is not collapsed when created in block
            if (i.hasNext()) {
              MatchResult matchResult = i.next();

              if (MatchResult.LINE_MATCH.equals(matchResult.getName()) &&
                  matchResult.getMatch() instanceof PsiMember
                 ) {
                element = matchResult.getMatch();
              } else {
                l.add( manager.createSmartPsiElementPointer(element) );
                element = matchResult.getMatch();
              }
            }
          }
          l.add( manager.createSmartPsiElementPointer(element) );
        }
      }
    } else {
      l.add( manager.createSmartPsiElementPointer(result.getMatchRef().getElement()));
    }

    ReplacementInfoImpl replacementInfo = new ReplacementInfoImpl();

    replacementInfo.matchesPtrList = l;
    if (replacementBuilder==null) {
      replacementBuilder = new ReplacementBuilder(project,options);
    }
    replacementInfo.result = replacementBuilder.process(result,replacementInfo);
    replacementInfo.matchResult = result;

    return replacementInfo;
  }
}
