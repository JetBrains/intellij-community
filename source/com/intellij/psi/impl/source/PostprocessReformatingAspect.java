package com.intellij.psi.impl.source;

import com.intellij.pom.PomModelAspect;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.lang.ASTNode;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class PostprocessReformatingAspect implements PomModelAspect {
  private final PsiManager myPsiManager;
  private final TreeAspect myTreeAspect;
  private final Map<FileViewProvider, List<ASTNode>> myReformatElements = new HashMap<FileViewProvider, List<ASTNode>>();
  private boolean myDisabled;

  public PostprocessReformatingAspect(PsiManager psiManager, TreeAspect treeAspect) {
    myPsiManager = psiManager;
    myTreeAspect = treeAspect;
    psiManager.getProject().getModel().registerAspect(PostprocessReformatingAspect.class, this, Collections.singleton((PomModelAspect)treeAspect));
  }

  public void update(PomModelEvent event) {
    if(myDisabled) return;
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
    if(changeSet == null) return;
    final PsiElement psiElement = changeSet.getRootElement().getPsi();
    if(psiElement == null) return;
    final FileViewProvider viewProvider = psiElement.getContainingFile().getViewProvider();
    if(!viewProvider.isEventSystemEnabled()) return;
    for (final ASTNode node : changeSet.getChangedElements()) {
      final TreeChange treeChange = changeSet.getChangesByElement(node);
      for (final ASTNode affectedChild : treeChange.getAffectedChildren()) {
        final ChangeInfo childChange = treeChange.getChangeByChild(affectedChild);
        switch(childChange.getChangeType()){
          case ChangeInfo.ADD:
            postponeFormatting(viewProvider, affectedChild);
            break;
          case ChangeInfo.REPLACE:
            postponeFormatting(viewProvider, affectedChild);
            break;
          // TODO[ik]: do we really need to process Removed elements or ChangeUtil must replace/insert surrounding
          // whitespace?
          //case ChangeInfo.REMOVED:
          //  postponeFormatting(viewProvider, affectedChild);
          //  break;
        }
      }
    }
  }

  public void setDisabled(boolean disabled) {
    myDisabled = disabled;
  }

  private void postponeFormatting(final FileViewProvider viewProvider, final ASTNode child) {
    if(CodeEditUtil.isNodeGenerated(child)){
      // for generated elements we have to find out if there are copied elements inside and add them to reindent
      ((TreeElement)child).acceptTree(new RecursiveTreeElementVisitor(){
        protected boolean visitNode(TreeElement element) {
          final boolean generatedFlag = CodeEditUtil.isNodeGenerated(element);
          if(!generatedFlag)
            postponeFormatting(viewProvider, element);
          return generatedFlag;
        }
      });
    }
    List<ASTNode> list = myReformatElements.get(viewProvider);
    if(list == null) myReformatElements.put(viewProvider, list = new ArrayList<ASTNode>());
    list.add(child);
  }

  public void doPostponedFormatting(){
    final FileViewProvider[] viewProviders = myReformatElements.keySet().toArray(new FileViewProvider[myReformatElements.size()]);
    for (int i = 0; i < viewProviders.length; i++) {
      final FileViewProvider viewProvider = viewProviders[i];
      doPostponedFormatting(viewProvider);
    }
  }

  public void doPostponedFormatting(final FileViewProvider key) {
    if(myDisabled) return;
    try{
      setDisabled(true); // for
      final List<ASTNode> astNodes = myReformatElements.remove(key);
      if(astNodes == null) return;
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myPsiManager.getProject());
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
      final Document document = key.getDocument();
      FileType fileType = key.getVirtualFile().getFileType();
      Helper helper = new Helper(fileType, myPsiManager.getProject());
      final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(styleSettings, helper);
      // Sort ranges by end offsets so that we won't need any offset adjustment after reformat or reindent
      final Comparator<TextRange> rangesComparator = new Comparator<TextRange>() {
        public int compare(final TextRange o1, final TextRange o2) {
          if(o1.equals(o2)) return 0;
          final int diff = o2.getEndOffset() - o1.getEndOffset();
          if (diff == 0) return o2.getStartOffset() - o1.getStartOffset();
          return diff;
        }
      };
      final TreeMap<TextRange, ReformatAction> rangesToProcess = new TreeMap<TextRange, ReformatAction>(rangesComparator);
      if(document == null || documentManager.isUncommited(document)) return;
      for (final ASTNode node : astNodes) {
        final FileElement element = TreeUtil.getFileElement((TreeElement)node);
        if(element != null && ((PsiFile)element.getPsi()).getViewProvider() == key){
          if(CodeEditUtil.isNodeGenerated(node))
            rangesToProcess.put(node.getTextRange(), ReformatAction.REFORMAT);
          else
            rangesToProcess.put(node.getTextRange(), ReformatAction.REINDENT);
        }
      }
      final PsiFile psiFile = key.getPsi(key.getBaseLanguage());

      TextRange accumulatedRange = null;
      ReformatAction accumulatedRangeAction = null;
      final List<RangeMarker> postIndentReformatRanges = new ArrayList<RangeMarker>();
      final ListIterator<TextRange> rangesIterator = new ArrayList<TextRange>(rangesToProcess.keySet()).listIterator();
      while(rangesIterator.hasNext()) {
        final TextRange textRange = rangesIterator.next();
        final ReformatAction action = rangesToProcess.get(textRange);
        if(accumulatedRange == null){
          accumulatedRange = textRange;
          accumulatedRangeAction = action;
        }
        else if(accumulatedRange.getStartOffset() > textRange.getEndOffset() ||
                accumulatedRange.getStartOffset() == textRange.getEndOffset() && accumulatedRangeAction == ReformatAction.REINDENT){
          // does not instersect
          processRange(codeFormatter, psiFile, accumulatedRange, accumulatedRangeAction, postIndentReformatRanges);
          accumulatedRange = textRange;
          accumulatedRangeAction = action;
        }
        else if(accumulatedRangeAction == ReformatAction.REFORMAT && action == ReformatAction.REINDENT){
          // split accumulated range into two
          if(accumulatedRange.getStartOffset() < textRange.getStartOffset()){
            final TextRange endOfRange = new TextRange(accumulatedRange.getStartOffset(), textRange.getStartOffset());
            int stepsCounter = 0;
            if(rangesIterator.hasNext()){
              TextRange current = rangesIterator.next();
              while(rangesIterator.hasNext() && current.getEndOffset() > endOfRange.getEndOffset()){
                current = rangesIterator.next();
                stepsCounter++;
              }
              rangesIterator.previous();
            }
            rangesIterator.add(endOfRange);
            while(stepsCounter-- >= 0) rangesIterator.previous();
            rangesToProcess.put(endOfRange, ReformatAction.REFORMAT);
          }
          final TextRange rangeToProcess = new TextRange(textRange.getEndOffset(), accumulatedRange.getEndOffset());
          processRange(codeFormatter, psiFile, rangeToProcess, ReformatAction.REFORMAT, postIndentReformatRanges);
          accumulatedRange = textRange;
          accumulatedRangeAction = ReformatAction.REINDENT;
        }
        else {
          accumulatedRange = new TextRange(Math.min(accumulatedRange.getStartOffset(), textRange.getStartOffset()),
                                           Math.max(accumulatedRange.getEndOffset(), textRange.getEndOffset()));
          if(accumulatedRangeAction == ReformatAction.REINDENT && action == ReformatAction.REFORMAT)
            postIndentReformatRanges.add(document.createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset()));
        }
      }
      if(accumulatedRange != null)
        processRange(codeFormatter, psiFile, accumulatedRange, accumulatedRangeAction, postIndentReformatRanges);
    }
    finally{
      setDisabled(false);
    }
  }

  private void processRange(final CodeFormatterFacade codeFormatter,
                            final PsiFile psiFile,
                            final TextRange accumulatedTextRange,
                            final ReformatAction accumulatedRangeAction,
                            final List<RangeMarker> postIndentReformatRanges) {
    switch(accumulatedRangeAction){
      case REFORMAT:
        processReformat(codeFormatter, psiFile, accumulatedTextRange);
        break;
      case REINDENT:{
        final Document document = psiFile.getViewProvider().getDocument();
        final CharSequence charsSequence = document.getCharsSequence().subSequence(accumulatedTextRange.getStartOffset(),
                                                                                   accumulatedTextRange.getEndOffset());
        final TextRange[] whitespaces = CharArrayUtil.getIndents(charsSequence, accumulatedTextRange.getStartOffset());
        if(whitespaces.length > 0 && isHeadingWhitespace(whitespaces[whitespaces.length - 1], charsSequence, accumulatedTextRange.getStartOffset())){
          final int indentAdjustment = getIndentAdjustment(psiFile, whitespaces[whitespaces.length - 1]);
          if(indentAdjustment != 0)
            adjustIndentationInRange(psiFile, document, whitespaces, indentAdjustment);
        }
        TextRange mergedRange = null;
        for (final RangeMarker postIndentReformatRange : postIndentReformatRanges) {
          if(mergedRange == null)
            mergedRange = new TextRange(postIndentReformatRange.getStartOffset(), postIndentReformatRange.getEndOffset());
          else if(mergedRange.getStartOffset() <= postIndentReformatRange.getEndOffset())
            mergedRange = new TextRange(Math.min(mergedRange.getStartOffset(), postIndentReformatRange.getStartOffset()),
                                        Math.max(mergedRange.getEndOffset(), postIndentReformatRange.getEndOffset()));
          else processReformat(codeFormatter, psiFile, mergedRange);
        }
        if(mergedRange != null) processReformat(codeFormatter, psiFile, mergedRange);
        postIndentReformatRanges.clear();
        break;
      }
    }
  }

  private boolean isHeadingWhitespace(final TextRange whitespace, final CharSequence charsSequence, final int startOffset) {
    for(int i = 0; i < whitespace.getStartOffset() - startOffset; i++)
    if(!Character.isWhitespace(charsSequence.charAt(i))) return false;
    return true;
  }

  private static void adjustIndentationInRange(final PsiFile file, final Document document, final TextRange[] indents, final int indentAdjustment) {
    final Helper formatHelper = new Helper(file.getFileType(), file.getProject());
    final CharSequence charsSequence = document.getCharsSequence();
    for (int i = 0; i < indents.length; i++) {
      final TextRange indent = indents[i];
      final String oldIndentStr = charsSequence.subSequence(indent.getStartOffset(), indent.getEndOffset()).toString();
      final int oldIndent = formatHelper.getIndent(oldIndentStr, true);
      final String newIndentStr = formatHelper.fillIndent(Math.max(oldIndent + indentAdjustment, 0));
      document.replaceString(indent.getStartOffset() + 1, indent.getEndOffset(), newIndentStr);
    }
  }

  private static int getIndentAdjustment(final PsiFile psiFile, final TextRange firstWhitespace) {
    final Helper formatHelper = new Helper(psiFile.getFileType(), psiFile.getProject());
    final Document document = psiFile.getViewProvider().getDocument();
    final String oldIndentStr = document.getCharsSequence().subSequence(firstWhitespace.getStartOffset(),
                                                                     firstWhitespace.getEndOffset()).toString();
    final String newIndentStr = CodeStyleManager.getInstance(psiFile.getProject()).getLineIndent(psiFile, firstWhitespace.getStartOffset());

    final int oldIndent = formatHelper.getIndent(oldIndentStr, true);
    final int newIndent = formatHelper.getIndent(newIndentStr, true);
    return newIndent - oldIndent;
  }

  public boolean isViewProviderLocked(final FileViewProvider fileViewProvider) {
    return myReformatElements.containsKey(fileViewProvider);
  }

  private enum ReformatAction{REFORMAT, REINDENT}

  private void processReformat(final CodeFormatterFacade codeFormatter, final PsiFile psiFile, final TextRange accumulatedTextRange) {
    final PsiDocumentManager instance = PsiDocumentManager.getInstance(psiFile.getProject());
    instance.commitDocument(instance.getDocument(psiFile));
    codeFormatter.processTextWithoutHeadWhitespace(psiFile, accumulatedTextRange.getStartOffset(), accumulatedTextRange.getEndOffset());
  }

  public boolean isDisabled() {
    return myDisabled;
  }

  final Runnable myPostprocessingApplicationAction = new Runnable() {
    public void run() {
      doPostponedFormatting();
    }
  };

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  public String getComponentName() {
    return "Postponed reformatting model";
  }

  public void initComponent() {
    ((ApplicationImpl)ApplicationManager.getApplication()).addPostWriteAction(myPostprocessingApplicationAction);
  }

  public void disposeComponent() {
    ((ApplicationImpl)ApplicationManager.getApplication()).removePostWriteAction(myPostprocessingApplicationAction);
  }
}
