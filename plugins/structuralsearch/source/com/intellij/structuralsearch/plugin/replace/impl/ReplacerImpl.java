package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.Template;

import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 4, 2004
 * Time: 9:19:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class ReplacerImpl {
  private Project project;
  private ReplacementBuilder replacementBuilder;
  private ReplaceOptions options;
  private ReplacementContext context;

  protected ReplacerImpl(Project _project, ReplaceOptions _options) {
    project = _project;
    options = _options;
  }

  protected String testReplace(String in, String what, String by, ReplaceOptions _options,boolean filePattern) {
    options = _options;
    options.getMatchOptions().setSearchPattern(what);
    options.setReplacement(by);
    replacementBuilder=null;
    context = null;

    options.getMatchOptions().clearVariableConstraints();
    MatcherImplUtil.transform(options.getMatchOptions());

    checkSupportedReplacementPattern(
      project,
      options.getMatchOptions().getSearchPattern(),
      by,
      options.getMatchOptions().getFileType()
    );

    Matcher matcher = new Matcher(project);
    try {
      PsiElement[] elements = MatcherImplUtil.createTreeFromText(in,filePattern, options.getMatchOptions().getFileType(), project);
      PsiElement firstElement = elements[0];
      PsiElement lastElement = elements[elements.length-1];
      PsiElement parent = firstElement.getParent();

      options.getMatchOptions().setScope(
        new LocalSearchScope(parent)
      );

      options.getMatchOptions().setResultIsContextMatch(true);
      CollectingMatchResultSink sink = new CollectingMatchResultSink();

      matcher.testFindMatches(sink,options.getMatchOptions());

      final List<ReplacementInfo> resultPtrList = new LinkedList<ReplacementInfo>();

      for(Iterator i=sink.getMatches().iterator();i.hasNext();) {
        final MatchResult result = (MatchResult)i.next();

        resultPtrList.add( buildReplacement(result) );
      }

      sink.getMatches().clear();

      int startOffset = firstElement.getTextOffset();
      int endOffset = (filePattern)?0: parent.getTextLength() - (lastElement.getTextOffset() + lastElement.getTextLength());

      // get nodes from text may contain
      PsiElement prevSibling = firstElement.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace) {
        startOffset -= (prevSibling.getTextLength() - 1);
      }

      PsiElement nextSibling = lastElement.getNextSibling();
      if (nextSibling instanceof PsiWhiteSpace) {
        endOffset -= (nextSibling.getTextLength() - 1);
      }

      replaceAll(resultPtrList);

      String result = parent.getText();
      result = result.substring(startOffset);
      result = result.substring(0,result.length() - endOffset);

      return result;
    } catch(Exception ex) {
      ex.printStackTrace( );
      return "";
    }
  }

  protected void replaceAll(final List<ReplacementInfo> resultPtrList) {
    for(Iterator<ReplacementInfo> i=resultPtrList.iterator();i.hasNext();) {
      replace(i.next());
    }
  }

  class ReplacementContext {
    private PsiCodeBlock codeBlock;

    PsiCodeBlock getCodeBlock() throws IncorrectOperationException {
      if (codeBlock == null) {
        codeBlock = createCodeBlock(project, options);
      }
      return codeBlock;
    }
  }

  protected void replace(ReplacementInfo _info) {
    final ReplacementInfoImpl info = (ReplacementInfoImpl)_info;
    final PsiElement element = (info.matchesPtrList.get(0)).getElement();
    final String replacement = info.result;

    if (element==null || !element.isWritable() || !element.isValid()) return;

    final PsiElement elementParent = element.getParent();

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                boolean listContext = false;

                try {
                  PsiElement el = findRealSubstitutionElement(element);
                  listContext = isListContext(el);

                  final PsiElement[] statements = MatcherImplUtil.createTreeFromText(
                    replacement,
                    false,
                    options.getMatchOptions().getFileType(),
                    project
                  );

                  if (context == null) context = new ReplacementContext();

                  if (listContext) {
                    if (statements.length > 1) {
                      elementParent.addRangeBefore(statements[0],statements[statements.length-1],element);
                    } else if (statements.length==1) {
                      PsiElement replacement = getMatchExpr(statements[0]);

                      handleComments(el, replacement);
                      handleModifierList(el, replacement, context);
                      replacement = handleSymbolReplacemenent(replacement, el, context);

                      if (replacement instanceof PsiTryStatement) {
                        final List<PsiCodeBlock> unmatchedCatchBlocks = el.getUserData(MatcherImplUtil.UNMATCHED_CATCH_BLOCK_CONTENT_VAR_KEY);
                        final List<PsiParameter> unmatchedCatchParams = el.getUserData(MatcherImplUtil.UNMATCHED_CATCH_PARAM_CONTENT_VAR_KEY);
                        final PsiCatchSection catches[] = ((PsiTryStatement)replacement).getCatchSections();

                        if (unmatchedCatchBlocks!=null && unmatchedCatchParams!=null) {
                          for(int i = unmatchedCatchBlocks.size()-1; i >= 0; --i) {
                            final PsiParameter parameter = unmatchedCatchParams.get(i);
                            final PsiCatchSection catchSection = PsiManager.getInstance(project).getElementFactory().createCatchSection(
                              (PsiClassType)parameter.getType(),
                              parameter.getName(),
                              null
                            );

                            catchSection.getCatchBlock().replace(
                              unmatchedCatchBlocks.get(i)
                            );
                            replacement.addAfter(
                              catchSection, catches[catches.length-1]
                            );
                          }
                        }
                      }

                      elementParent.addBefore(replacement,element);
                    }
                  } else if (statements.length > 0) {
                    int i = 0;
                    while( true ) {
                      if (!(statements[i] instanceof PsiComment ||
                            statements[i] instanceof PsiWhiteSpace
                           )
                         ) break;
                      ++i;
                    }

                    if (i != 0) {
                      elementParent.addRangeBefore(statements[0],statements[i-1],el);
                    }
                    PsiElement replacement = getMatchExpr(statements[i]);

                    if (replacement instanceof PsiStatement &&
                        !(replacement.getLastChild() instanceof PsiJavaToken)
                       ) {
                      // assert w/o ;
                      elementParent.addRangeBefore(replacement.getFirstChild(),replacement.getLastChild().getPrevSibling(),el);
                      el.delete();
                    } else {
                      PsiDocComment comment = null;

                      if (el instanceof PsiDocCommentOwner) {
                        comment = ((PsiDocCommentOwner)el).getDocComment();
                      }

                      // preserve comments
                      handleComments(el, replacement);

                      handleModifierList(el, replacement, context);

                      if (comment!=null && replacement instanceof PsiDocCommentOwner) {
                        replacement.addBefore(
                          comment,
                          replacement.getFirstChild()
                        );
                      }

                      if (replacement instanceof PsiClass) {
                        // modifier list
                        final PsiStatement[] searchStatements = context.getCodeBlock().getStatements();
                        if (searchStatements.length > 0 &&
                            searchStatements[0] instanceof PsiDeclarationStatement &&
                            ((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0] instanceof PsiClass
                           ) {
                          final PsiClass replaceClazz = (PsiClass)replacement;
                          final PsiClass queryClazz = (PsiClass)((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0];
                          final PsiClass clazz = (PsiClass)el;

                          if (replaceClazz.getExtendsList().getTextLength() == 0 &&
                              queryClazz.getExtendsList().getTextLength() == 0 &&
                              clazz.getExtendsList().getTextLength() != 0
                              ) {
                            replaceClazz.getExtendsList().addRange(
                              clazz.getExtendsList().getFirstChild(),clazz.getExtendsList().getLastChild()
                            );
                          }

                          if (replaceClazz.getImplementsList().getTextLength() == 0 &&
                              queryClazz.getImplementsList().getTextLength() == 0 &&
                              clazz.getImplementsList().getTextLength() != 0
                              ) {
                            replaceClazz.getImplementsList().addRange(
                              clazz.getImplementsList().getFirstChild(),
                              clazz.getImplementsList().getLastChild()
                            );
                          }

                          if (replaceClazz.getTypeParameterList().getTextLength() == 0 &&
                              queryClazz.getTypeParameterList().getTextLength() == 0 &&
                              clazz.getTypeParameterList().getTextLength() != 0
                              ) {
                            // skip < and >
                            replaceClazz.getTypeParameterList().replace(
                              clazz.getTypeParameterList()
                            );
                          }
                        }
                      }

                      replacement = handleSymbolReplacemenent(replacement, el, context);

                      el.replace(replacement);
                    }
                  } else {
                    el.delete();
                  }

                } catch(IncorrectOperationException ex) {
                  ex.printStackTrace();
                }

                if (listContext) {
                  for(int i = 0;i < info.matchesPtrList.size();++i) {
                    try {
                      PsiElement element = findRealSubstitutionElement(
                        (info.matchesPtrList.get(i)).getElement()
                      );
                      element.delete();
                      //PsiElement firstToDelete = element;
                      //PsiElement lastToDelete = element;
                      //final PsiElement prevSibling = element.getPrevSibling();
                      //
                      //if (prevSibling instanceof PsiWhiteSpace) {
                      //  firstToDelete = prevSibling;
                      //}
                      //
                      //final PsiElement nextSibling = element.getNextSibling();
                      //if (nextSibling instanceof PsiWhiteSpace) {
                      //  lastToDelete = nextSibling;
                      //}

                      //element.getParent().deleteChildRange(firstToDelete,lastToDelete);
                    } catch(IncorrectOperationException ex) {
                      ex.printStackTrace();
                    }
                  }
                }

                try {

                  CodeStyleManager codeStyleManager = PsiManager.getInstance(project).getCodeStyleManager();
                  final PsiFile containingFile = elementParent.getContainingFile();
                  
                  if (containingFile !=null) {
                    if (options.isToShortenFQN()) {
                      if (containingFile.getVirtualFile() != null) {
                        PsiDocumentManager.getInstance(project).commitDocument(
                          FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile())
                        );
                      }
                      
                      codeStyleManager.shortenClassReferences(
                        containingFile,
                        elementParent.getTextOffset(),
                        elementParent.getTextOffset() + elementParent.getTextLength()
                      );
                    }

                    if (options.isToReformatAccordingToStyle()) {
                      if (containingFile.getVirtualFile() != null) {
                        PsiDocumentManager.getInstance(project).commitDocument(
                          FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile())
                        );
                      }

                      codeStyleManager.reformatRange(
                        containingFile,
                        elementParent.getTextOffset(),
                        elementParent.getTextOffset() + elementParent.getTextLength(),
                        true
                      );
                    }
                  }
                } catch(IncorrectOperationException ex) {
                  ex.printStackTrace();
                }
              }
            }
          );
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        }
      },
      "ssreplace",
      "test"
    );
  }

  private static PsiElement handleSymbolReplacemenent(PsiElement replacement,
                                               final PsiElement el,
                                               ReplacementContext context) throws IncorrectOperationException {
    if (replacement instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)replacement).getQualifierExpression() == null
        ) {
      final PsiStatement[] searchStatements = context.getCodeBlock().getStatements();
      if (searchStatements.length > 0 &&
          searchStatements[0] instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)searchStatements[0]).getExpression();

        if (expression instanceof PsiReferenceExpression &&
            ((PsiReferenceExpression)expression).getQualifierExpression() == null
           ) {
          // looks like symbol replacements, namely replace AAA by BBB, so lets do the best
          if (el instanceof PsiNamedElement) {
            PsiElement oldReplacement = replacement;
            replacement = el.copy();
            ((PsiNamedElement)replacement).setName(oldReplacement.getText());
          }
        }
      }
    }
    return replacement;
  }

  private static void handleComments(final PsiElement el, final PsiElement replacement) throws IncorrectOperationException {
    if (el.getLastChild() instanceof PsiComment) {
      PsiElement firstElementAfterStatementEnd;
      firstElementAfterStatementEnd = el.getLastChild();
      for(PsiElement curElement=firstElementAfterStatementEnd.getPrevSibling();curElement!=null;curElement = curElement.getPrevSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        firstElementAfterStatementEnd = curElement;
      }
      replacement.addRangeAfter(firstElementAfterStatementEnd,el.getLastChild(),replacement.getLastChild());
    }

    final PsiElement firstChild = el.getFirstChild();
    if (firstChild instanceof PsiComment &&
        !(firstChild instanceof PsiDocComment)
        ) {
      PsiElement lastElementBeforeStatementStart = firstChild;

      for(PsiElement curElement=lastElementBeforeStatementStart.getNextSibling();curElement!=null;curElement = curElement.getNextSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        lastElementBeforeStatementStart = curElement;
      }
      replacement.addRangeBefore(el.getFirstChild(),lastElementBeforeStatementStart,replacement.getFirstChild());
    }
  }

  private static void handleModifierList(final PsiElement el,
                                  final PsiElement replacement,
                                  final ReplacementContext context) throws IncorrectOperationException {
    if (el instanceof PsiModifierListOwner && replacement instanceof PsiModifierListOwner) {
      // copy modifier list
      PsiModifierList modifierList = ((PsiModifierListOwner)el).getModifierList();

      final PsiStatement[] searchStatements = context.getCodeBlock().getStatements();
      if (searchStatements.length > 0 &&
          searchStatements[0] instanceof PsiDeclarationStatement &&
          ((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0] instanceof PsiModifierListOwner
         ) {
        PsiModifierListOwner searchVar = (PsiModifierListOwner)((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0];
        if (searchVar.getModifierList().getTextLength() == 0 &&
            ((PsiModifierListOwner)replacement).getModifierList().getTextLength() == 0
           ) {
          ((PsiModifierListOwner)replacement).getModifierList().replace(
            modifierList
          );
        }
      }
    }
  }

  private static PsiCodeBlock createCodeBlock(final Project project, final ReplaceOptions options)
    throws IncorrectOperationException {
    PsiCodeBlock search;
    search = (PsiCodeBlock)MatcherImplUtil.createTreeFromText(
      options.getMatchOptions().getSearchPattern(),
      false,
      options.getMatchOptions().getFileType(),
      project
    )[0].getParent();

    return search;
  }

  public static void checkSupportedReplacementPattern(Project project, String search,
                                                      String replacement, FileType fileType) throws UnsupportedPatternException {
    try {
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
          throw new UnsupportedPatternException(
            "Replacement variable " +
            replacementSegmentName +
            " is not defined in search segment."
          );
        }
      }

      if (fileType==StdFileTypes.JAVA) {
        PsiElement[] statements = MatcherImplUtil.createTreeFromText(search, false, fileType, project);
        boolean searchIsExpression = false;

        for (int i = 0; i < statements.length; i++) {
          PsiElement statement = statements[i];
          if (statement.getLastChild() instanceof PsiErrorElement) {
            searchIsExpression = true;
            break;
          }
        }

        PsiElement[] statements2 = MatcherImplUtil.createTreeFromText(replacement, false, fileType, project);
        boolean replaceIsExpression = false;

        for (int i = 0; i < statements2.length; i++) {
          PsiElement statement = statements2[i];
          if (statement.getLastChild() instanceof PsiErrorElement) {
            replaceIsExpression = true;
            break;
          }
        }

        if (searchIsExpression!=replaceIsExpression) {
          throw new UnsupportedPatternException(
            (searchIsExpression) ? "The search template is a well formed expression, but the replacement template is not an expression.":
            "The search template is not an expression, but the replacement template is a well formed expression."
          );
        }
      }
    } catch(IncorrectOperationException ex) {
      throw new UnsupportedPatternException("Incorrect pattern");
    }
  }

  private static PsiElement getMatchExpr(PsiElement replacement) {
    if (replacement instanceof PsiExpressionStatement &&
        !(replacement.getLastChild() instanceof PsiJavaToken)
       ) {
      // replacement is expression (and pattern should be so)
      // assert ...
      replacement = ((PsiExpressionStatement)replacement).getExpression();
    } else if (replacement instanceof PsiDeclarationStatement &&
               ((PsiDeclarationStatement)replacement).getDeclaredElements().length==1
               ) {
      return ((PsiDeclarationStatement)replacement).getDeclaredElements()[0];
    }

    return replacement;
  }

  private static boolean isListContext(PsiElement el) {
    boolean listContext = false;
    final PsiElement parent = el.getParent();

    if (parent instanceof PsiParameterList ||
        parent instanceof PsiExpressionList ||
        parent instanceof PsiCodeBlock ||
        parent instanceof XmlTag ||
        parent instanceof PsiClass
       ) {
      listContext = true;
    }

    return listContext;
  }

  private static PsiElement findRealSubstitutionElement(PsiElement el) {
    if (el instanceof PsiIdentifier) {
        // matches are tokens, identifiers, etc
      el = el.getParent();
    }

    if (el instanceof PsiReferenceExpression &&
        el.getParent() instanceof PsiMethodCallExpression
       ) {
      // method
      el = el.getParent();
    }

    if (el instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)el).getDeclaredElements()[0] instanceof PsiClass) {
      el = ((PsiDeclarationStatement)el).getDeclaredElements()[0];
    }
    return el;
  }

  protected ReplacementInfo buildReplacement(MatchResult result) {
    List<SmartPsiElementPointer> l = new LinkedList<SmartPsiElementPointer>();
    SmartPointerManager manager = SmartPointerManager.getInstance(project);

    if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
      for(Iterator i=result.getSons();i.hasNext();) {
        final MatchResult r = (MatchResult)i.next();
        if (MatchResult.LINE_MATCH.equals(r.getName())) {
          l.add( manager.createSmartPsiElementPointer(r.getMatchRef().getElement()) );
        }
      }
    } else {
      l.add( manager.createSmartPsiElementPointer(result.getMatchRef().getElement()));
    }

    ReplacementInfoImpl replacementInfo = new ReplacementInfoImpl();

    replacementInfo.matchesPtrList = l;
    if (replacementBuilder==null) {
      replacementBuilder = new ReplacementBuilder(
        project,
        options.getReplacement(),
        options.getMatchOptions().getFileType()
      );
    }
    replacementInfo.result = replacementBuilder.process(result);
    replacementInfo.matchResult = result;

    return replacementInfo;
  }
}
