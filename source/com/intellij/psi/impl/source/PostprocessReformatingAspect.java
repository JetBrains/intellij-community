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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.HashMap;
import java.util.HashSet;

public class PostprocessReformatingAspect implements PomModelAspect {
  private static final Comparator<RangeMarker> ourRangesComparator = new Comparator<RangeMarker>() {
    public int compare(final RangeMarker o1, final RangeMarker o2) {
      if (o1.equals(o2)) return 0;
      final int diff = o2.getEndOffset() - o1.getEndOffset();
      if (diff == 0){
        if(o1.getStartOffset() == o2.getStartOffset()) return 0;
        if(o1.getStartOffset() == o1.getEndOffset()) return -1; // empty ranges first
        if(o2.getStartOffset() == o2.getEndOffset()) return 1; // empty ranges first
        return o1.getStartOffset() - o2.getStartOffset();
      }
      return diff;
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PostprocessReformatingAspect");
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
        if(myDisabled) {
          if(childChange.getChangeType() == ChangeInfo.ADD &&
             affectedChild.getElementType() == ElementType.REFORMAT_MARKER) {
            final ASTNode next = TreeUtil.nextLeaf(affectedChild);
            final ASTNode prev = TreeUtil.prevLeaf(affectedChild);
            final ParserDefinition.SpaceRequirements spaceRequirements = getSpaceRequirements(prev, next);
            if(spaceRequirements == ParserDefinition.SpaceRequirements.MUST ||
               spaceRequirements == ParserDefinition.SpaceRequirements.MUST_LINE_BREAK){
              postponeFormatting(viewProvider, affectedChild);
            }
            else affectedChild.getTreeParent().removeChild(affectedChild);
          }
          continue;
        }
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

  private ParserDefinition.SpaceRequirements getSpaceRequirements(final ASTNode prev, final ASTNode next) {
    if(prev == null || next == null) return ParserDefinition.SpaceRequirements.MAY;
    final Language language = prev.getElementType().getLanguage();
    final ParserDefinition parserDefinition = language.getParserDefinition();
    final ParserDefinition.SpaceRequirements spaceRequirements = parserDefinition != null && language == next.getElementType().getLanguage() ?
                                                                 parserDefinition.spaceExistanceTypeBetweenTokens(prev, next) :
                                                                 ParserDefinition.SpaceRequirements.MAY;
    return spaceRequirements;
  }

  public void setDisabled(boolean disabled) {
    myDisabled = disabled;
  }

  private void postponeFormatting(final FileViewProvider viewProvider, final ASTNode child) {
    if(CodeEditUtil.isNodeGenerated(child)){
      // for generated elements we have to find out if there are copied elements inside and add them for reindent
      ((TreeElement)child).acceptTree(new RecursiveTreeElementVisitor(){
        protected boolean visitNode(TreeElement element) {
          final boolean generatedFlag = CodeEditUtil.isNodeGenerated(element);
          if(!generatedFlag && element.getElementType() != ElementType.WHITE_SPACE)
            postponeFormatting(viewProvider, element);
          return generatedFlag;
        }
      });
    }
    else if (child.getElementType() != ElementType.WHITE_SPACE) {
      final int oldIndent = CodeEditUtil.getOldIndentation(child);
      LOG.assertTrue(oldIndent >= 0, "for not generated items old indentation must be defined");
    }
    List<ASTNode> list = myReformatElements.get(viewProvider);
    if(list == null)
      myReformatElements.put(viewProvider, list = new ArrayList<ASTNode>());
    list.add(child);
  }

  public void doPostponedFormatting(){
    final FileViewProvider[] viewProviders = myReformatElements.keySet().toArray(new FileViewProvider[myReformatElements.size()]);
    for (int i = 0; i < viewProviders.length; i++) {
      final FileViewProvider viewProvider = viewProviders[i];
      doPostponedFormatting(viewProvider);
    }
  }

  public void doPostponedFormatting(final FileViewProvider viewProvider) {
    if(myDisabled) return;
    try{
      setDisabled(true);
      doPostponedFormattingInner(viewProvider);
    }
    finally{
      setDisabled(false);
    }
  }

  public void doPostponedFormattingInner(final FileViewProvider key) {
    final List<ASTNode> astNodes = myReformatElements.remove(key);
    if (astNodes == null) return;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
    final Document document = key.getDocument();
    // Sort ranges by end offsets so that we won't need any offset adjustment after reformat or reindent
    if (document == null || documentManager.isUncommited(document)) return;

    // we replace all reformat markers by single whitespaces which will be reformated later. We need to do this replacement because absence
    // of these whitespaces in text may cause syntax changes in text.
    // at this point we'll get only those markers which are relevant for formater disabled/enabled state (see update function)
    insertWhitespacesAtMarkersPositions(astNodes, key);

    // then we create ranges by changed nodes. One per node. There ranges can instersect. Ranges are sorted by end offset.
    final TreeMap<RangeMarker, PostponedAction> rangesToProcess = createActionsMap(astNodes, document);

    // now we have to normalize actions so that they not intersect and ordered in most appropriate way
    // (free reformating -> reindent -> formating under reindent)
    final List<Pair<RangeMarker, ? extends PostponedAction>> normalizedActions = normalizeAndReorderPostponedActions(rangesToProcess, document);

    // only in following loop real changes in document are made
    for (final Pair<RangeMarker, ? extends PostponedAction> normalizedAction : normalizedActions) {
      normalizedAction.getSecond().processRange(normalizedAction.getFirst(), key);
    }
  }

  private List<Pair<RangeMarker, ? extends PostponedAction>> normalizeAndReorderPostponedActions(final TreeMap<RangeMarker, PostponedAction> rangesToProcess, Document document) {
    final List<Pair<RangeMarker, ReformatAction>> freeFormatingActions = new ArrayList<Pair<RangeMarker, ReformatAction>>();
    final List<Pair<RangeMarker, ReindentAction>> indentActions = new ArrayList<Pair<RangeMarker, ReindentAction>>();
    final List<Pair<RangeMarker, ReformatAction>> formatingAfterIndentActions = new ArrayList<Pair<RangeMarker, ReformatAction>>();

    RangeMarker accumulatedRange = null;
    PostponedAction accumulatedRangeAction = null;
    Iterator<RangeMarker> rangesIterator = rangesToProcess.keySet().iterator();
    while (rangesIterator.hasNext()) {
      final RangeMarker textRange = rangesIterator.next();
      final PostponedAction action = rangesToProcess.get(textRange);
      if (accumulatedRange == null) {
        accumulatedRange = textRange;
        accumulatedRangeAction = action;
      }
      else if (accumulatedRange.getStartOffset() > textRange.getEndOffset() ||
               (accumulatedRange.getStartOffset() == textRange.getEndOffset() && accumulatedRangeAction instanceof ReindentAction) ||
               (accumulatedRange.getStartOffset() == accumulatedRange.getEndOffset() && accumulatedRangeAction instanceof ReformatWithHeadingWhitespaceAction)) {
        // does not instersect
        if (accumulatedRangeAction instanceof ReindentAction)
          indentActions.add(new Pair<RangeMarker, ReindentAction>(accumulatedRange, (ReindentAction)accumulatedRangeAction));
        else
          freeFormatingActions.add(new Pair<RangeMarker, ReformatAction>(accumulatedRange, (ReformatAction)accumulatedRangeAction));

        accumulatedRange = textRange;
        accumulatedRangeAction = action;
      }
      else if (accumulatedRangeAction instanceof ReformatAction && action instanceof ReindentAction) {
        // split accumulated range into two
        if (accumulatedRange.getStartOffset() < textRange.getStartOffset()) {
          final RangeMarker endOfRange = document.createRangeMarker(accumulatedRange.getStartOffset(), textRange.getStartOffset());
          // add heading reformat part
          rangesToProcess.put(endOfRange, accumulatedRangeAction);
          // and manage heading whitespace because formatter does not edit it in previous action
          rangesIterator = rangesToProcess.keySet().iterator();
          while(rangesIterator.next() != textRange);
        }
        final RangeMarker rangeToProcess = document.createRangeMarker(textRange.getEndOffset(), accumulatedRange.getEndOffset());
        freeFormatingActions.add(new Pair<RangeMarker, ReformatAction>(rangeToProcess, new ReformatWithHeadingWhitespaceAction()));
        accumulatedRange = textRange;
        accumulatedRangeAction = action;
      }
      else {
        accumulatedRange = document.createRangeMarker(Math.min(accumulatedRange.getStartOffset(), textRange.getStartOffset()),
                                                      Math.max(accumulatedRange.getEndOffset(), textRange.getEndOffset()));
        if (accumulatedRangeAction instanceof ReindentAction && action instanceof ReformatAction) {
          formatingAfterIndentActions.add(new Pair<RangeMarker, ReformatAction>(
            document.createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset()), (ReformatAction)action));
        }
        else if(accumulatedRangeAction instanceof ReformatAction && action instanceof ReformatWithHeadingWhitespaceAction ||
                accumulatedRangeAction instanceof ReformatWithHeadingWhitespaceAction && action instanceof ReformatAction){
          accumulatedRangeAction = action;
        }
      }
    }
    if (accumulatedRange != null){
      if (accumulatedRangeAction instanceof ReindentAction)
        indentActions.add(new Pair<RangeMarker, ReindentAction>(accumulatedRange, (ReindentAction)accumulatedRangeAction));
      else
        freeFormatingActions.add(new Pair<RangeMarker, ReformatAction>(accumulatedRange, (ReformatAction)accumulatedRangeAction));
    }

    final List<Pair<RangeMarker, ? extends PostponedAction>> result =
      new ArrayList<Pair<RangeMarker, ? extends PostponedAction>>(rangesToProcess.size());
    Collections.reverse(freeFormatingActions);
    Collections.reverse(indentActions);
    Collections.reverse(formatingAfterIndentActions);
    result.addAll(freeFormatingActions);
    result.addAll(indentActions);
    result.addAll(formatingAfterIndentActions);
    return result;
  }

  private TreeMap<RangeMarker, PostponedAction> createActionsMap(final List<ASTNode> astNodes, final Document document) {
    final TreeMap<RangeMarker, PostponedAction> rangesToProcess = new TreeMap<RangeMarker, PostponedAction>(ourRangesComparator);
    final Set<ASTNode> nodesToProcess = new HashSet<ASTNode>(astNodes);
    for (final ASTNode node : astNodes) {
      nodesToProcess.remove(node);
      final boolean isGenerated = CodeEditUtil.isNodeGenerated(node);
      ((TreeElement)node).acceptTree(new RecursiveTreeElementVisitor() {
        boolean inGeneratedContext = !isGenerated;
        protected boolean visitNode(TreeElement current) {
          if(nodesToProcess.contains(current)) return false;
          if(current.getElementType() == ElementType.REFORMAT_MARKER){
            rangesToProcess.put(document.createRangeMarker(current.getTextRange()), new ReformatWithHeadingWhitespaceAction());
            if(current.getFirstChildNode() != null) current.getTreeParent().replaceChild(current, current.getFirstChildNode());
            else current.getTreeParent().removeChild(current);
            return false;
          }
          final boolean currentNodeGenerated = CodeEditUtil.isNodeGenerated(current);
          if(currentNodeGenerated && !inGeneratedContext){
            rangesToProcess.put(document.createRangeMarker(current.getTextRange()), new ReformatAction());
            inGeneratedContext = true;
          }
          if(!currentNodeGenerated && inGeneratedContext){
            if(current.getElementType() == ElementType.WHITE_SPACE) return false;
            final int oldIndent = CodeEditUtil.getOldIndentation(current);
            LOG.assertTrue(oldIndent >= 0, "for not generated items old indentation must be defined");
            rangesToProcess.put(document.createRangeMarker(current.getTextRange()), new ReindentAction(oldIndent));
            inGeneratedContext = false;
          }
          return true;
        }

        public void visitComposite(CompositeElement composite) {
          boolean oldGeneratedContext = inGeneratedContext;
          super.visitComposite(composite);
          inGeneratedContext = oldGeneratedContext;
        }

        public void visitLeaf(LeafElement leaf) {
          boolean oldGeneratedContext = inGeneratedContext;
          super.visitLeaf(leaf);
          inGeneratedContext = oldGeneratedContext;
        }
      });
    }
    return rangesToProcess;
  }

  private void insertWhitespacesAtMarkersPositions(final List<ASTNode> astNodes, final FileViewProvider key) {
    for (final ASTNode node : new ArrayList<ASTNode>(astNodes)) {
      final FileElement fileElement = TreeUtil.getFileElement((TreeElement)node);
      if (fileElement != null && ((PsiFile)fileElement.getPsi()).getViewProvider() == key) {
        // insert whitespaces at marker positions
        ((TreeElement)node).acceptTree(
          new RecursiveTreeElementVisitor(){
            protected boolean visitNode(TreeElement element) {
              if(element.getElementType() == ElementType.REFORMAT_MARKER){
                final ASTNode prev = TreeUtil.prevLeaf(element); ASTNode next = TreeUtil.nextLeaf(element);
                if((prev == null || prev.getElementType() != ElementType.WHITE_SPACE) &&
                   (next == null || next.getElementType() != ElementType.WHITE_SPACE)){
                  switch(getSpaceRequirements(prev, next)){
                    case MUST_NOT:
                      element.getTreeParent().removeChild(element);
                      break;
                    case MAY:
                      return false;
                    case MUST:{
                      final LeafElement generatedWhitespace = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, new char[]{' '}, 0, 1, null, key.getManager());
                      element.getTreeParent().replaceChild(element, generatedWhitespace);
                      astNodes.add(generatedWhitespace);
                      break;
                    }
                    case MUST_LINE_BREAK:{
                      final LeafElement generatedWhitespace = Factory.createSingleLeafElement(ElementType.WHITE_SPACE, new char[]{' '}, 0, 1, null, key.getManager());
                      element.getTreeParent().replaceChild(element, generatedWhitespace);
                      astNodes.add(generatedWhitespace);
                      break;
                    }
                  }
                }
                else {
                  element.getTreeParent().removeChild(element);
                  boolean nextToWhitespace = false;
                  if (prev != null && prev.getElementType() == ElementType.WHITE_SPACE) {
                    nextToWhitespace = true;
                    astNodes.add(prev);
                    CodeEditUtil.setNodeGenerated(prev, true);
                  }
                  if (next != null && next.getElementType() == ElementType.WHITE_SPACE) {
                    if(!nextToWhitespace){
                      if(prev != null && ElementType.COMMENT_BIT_SET.contains(prev.getElementType()) && next.getText().indexOf('\n') < 0){
                        final String whitespaceText = "\n" + next.getText();
                        final LeafElement generatedWhitespace = Factory.createSingleLeafElement(
                          ElementType.WHITE_SPACE, whitespaceText.toCharArray(), 0,
                          whitespaceText.length(), null, key.getManager());
                        next.getTreeParent().replaceChild(next, generatedWhitespace);
                        next = generatedWhitespace;
                      }
                      astNodes.add(next);
                      CodeEditUtil.setNodeGenerated(next, true);
                    }
                    else next.getTreeParent().removeChild(next);
                  }
                }
                astNodes.remove(element);
                return false;
              }
              return true;
            }
          });
      }
      else astNodes.remove(node);
    }
  }

  private static void adjustIndentationInRange(final PsiFile file, final Document document, final TextRange[] indents, final int indentAdjustment) {
    final Helper formatHelper = new Helper(file.getFileType(), file.getProject());
    final CharSequence charsSequence = document.getCharsSequence();
    for (int i = 0; i < indents.length; i++) {
      final TextRange indent = indents[i];
      final String oldIndentStr = charsSequence.subSequence(indent.getStartOffset() + 1, indent.getEndOffset()).toString();
      final int oldIndent = formatHelper.getIndent(oldIndentStr, true);
      final String newIndentStr = formatHelper.fillIndent(Math.max(oldIndent + indentAdjustment, 0));
      document.replaceString(indent.getStartOffset() + 1, indent.getEndOffset(), newIndentStr);
    }
  }

  private static int getNewIndent(final PsiFile psiFile, final int firstWhitespace) {
    final Helper formatHelper = new Helper(psiFile.getFileType(), psiFile.getProject());
    final Document document = psiFile.getViewProvider().getDocument();
    final int startOffset = document.getLineStartOffset(document.getLineNumber(firstWhitespace));
    int endOffset = startOffset;
    final CharSequence charsSequence = document.getCharsSequence();
    while(Character.isWhitespace(charsSequence.charAt(endOffset++)));
    final String newIndentStr = charsSequence.subSequence(startOffset, endOffset - 1).toString();
    return formatHelper.getIndent(newIndentStr, true);
  }

  public boolean isViewProviderLocked(final FileViewProvider fileViewProvider) {
    return myReformatElements.containsKey(fileViewProvider);
  }

  public static PostprocessReformatingAspect getInstance(Project project) {
    return project.getComponent(PostprocessReformatingAspect.class);
  }

  private interface PostponedAction {
    void processRange(RangeMarker marker, final FileViewProvider viewProvider);
  }

  private class ReformatAction implements PostponedAction {
    public void processRange(RangeMarker marker, final FileViewProvider viewProvider) {
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myPsiManager.getProject());
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
      final Document document = viewProvider.getDocument();
      final FileType fileType = viewProvider.getVirtualFile().getFileType();
      final Helper helper = new Helper(fileType, myPsiManager.getProject());
      final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(styleSettings, helper);

      documentManager.commitDocument(document);
      codeFormatter.processTextWithoutHeadWhitespace(viewProvider.getPsi(viewProvider.getBaseLanguage()),
                                                     marker.getStartOffset(), marker.getEndOffset());
    }

  }

  private class ReformatWithHeadingWhitespaceAction extends ReformatAction{
    public void processRange(RangeMarker marker, final FileViewProvider viewProvider) {
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myPsiManager.getProject());
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
      final Document document = viewProvider.getDocument();
      final FileType fileType = viewProvider.getVirtualFile().getFileType();
      final Helper helper = new Helper(fileType, myPsiManager.getProject());
      final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(styleSettings, helper);

      documentManager.commitDocument(document);
      codeFormatter.processText(viewProvider.getPsi(viewProvider.getBaseLanguage()), marker.getStartOffset(),
                                marker.getStartOffset() == marker.getEndOffset() ? marker.getEndOffset() + 1: marker.getEndOffset());
    }
  }


  private static class ReindentAction implements PostponedAction {
    private int myOldIndent;

    public ReindentAction(final int oldIndent) {
      myOldIndent = oldIndent;
    }

    public void processRange(RangeMarker marker, final FileViewProvider viewProvider) {
      final Document document = viewProvider.getDocument();
      final PsiFile psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
      final CharSequence charsSequence = document.getCharsSequence().subSequence(marker.getStartOffset(),
                                                                                 marker.getEndOffset());
      final int oldIndent = getOldIndent();
      final TextRange[] whitespaces = CharArrayUtil.getIndents(charsSequence, marker.getStartOffset());
      final int indentAdjustment = getNewIndent(psiFile, marker.getStartOffset()) - oldIndent;
      if(indentAdjustment != 0)
        adjustIndentationInRange(psiFile, document, whitespaces, indentAdjustment);
    }

    private int getOldIndent() {
      return myOldIndent;
    }

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
