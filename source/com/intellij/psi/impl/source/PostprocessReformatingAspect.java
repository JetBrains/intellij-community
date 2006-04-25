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
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.HashMap;
import java.util.HashSet;

public class PostprocessReformatingAspect implements PomModelAspect {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PostprocessReformatingAspect");
  private final PsiManager myPsiManager;
  private final TreeAspect myTreeAspect;
  private final Map<FileViewProvider, List<ASTNode>> myReformatElements = new HashMap<FileViewProvider, List<ASTNode>>();
  private boolean myDisabled;
  private Set<FileViewProvider> myUpdatedProviders = new HashSet<FileViewProvider>();

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
    myUpdatedProviders.add(viewProvider);
    for (final ASTNode node : changeSet.getChangedElements()) {
      final TreeChange treeChange = changeSet.getChangesByElement(node);
      for (final ASTNode affectedChild : treeChange.getAffectedChildren()) {
        final ChangeInfo childChange = treeChange.getChangeByChild(affectedChild);
        switch(childChange.getChangeType()){
          case ChangeInfo.ADD:
          case ChangeInfo.REPLACE:
            postponeFormatting(viewProvider, affectedChild);
            break;
          case ChangeInfo.CONTENTS_CHANGED:
            if(!CodeEditUtil.isNodeGenerated(affectedChild))
              ((TreeElement)affectedChild).acceptTree(new RecursiveTreeElementVisitor(){
                protected boolean visitNode(TreeElement element) {
                  if(CodeEditUtil.isNodeGenerated(element)){
                    postponeFormatting(viewProvider, element);
                    return false;
                  }
                  return true;
                }
              });
            break;
        }
      }
    }
  }

  public void setDisabled(boolean disabled) {
    myDisabled = disabled;
  }

  private void postponeFormatting(final FileViewProvider viewProvider, final ASTNode child) {
    if (!CodeEditUtil.isNodeGenerated(child) && child.getElementType() != ElementType.WHITE_SPACE) {
      final int oldIndent = CodeEditUtil.getOldIndentation(child);
      LOG.assertTrue(oldIndent >= 0, "for not generated items old indentation must be defined");
    }
    List<ASTNode> list = myReformatElements.get(viewProvider);
    if(list == null)
      myReformatElements.put(viewProvider, list = new ArrayList<ASTNode>());
    list.add(child);
  }

  public void doPostponedFormatting(){
    if(myDisabled) return;
    try{
      for (final FileViewProvider viewProvider : myUpdatedProviders) {
        doPostponedFormatting(viewProvider);
      }
    }
    finally{
      myUpdatedProviders.clear();
      myReformatElements.clear();
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
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
    final Document document = key.getDocument();
    // Sort ranges by end offsets so that we won't need any offset adjustment after reformat or reindent
    if (document == null || documentManager.isUncommited(document)) return;

    final TreeMap<RangeMarker, PostponedAction> rangesToProcess = new TreeMap<RangeMarker, PostponedAction>(new Comparator<RangeMarker>() {
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
    });

    // process all roots in viewProvider to find marked for reformat before elements and create appropriate ragge markers
    handleReformatMarkers(key, rangesToProcess);

    // then we create ranges by changed nodes. One per node. There ranges can instersect. Ranges are sorted by end offset.
    if (astNodes != null) createActionsMap(astNodes, key, rangesToProcess);

    while(!rangesToProcess.isEmpty()){
      // now we have to normalize actions so that they not intersect and ordered in most appropriate way
      // (free reformating -> reindent -> formating under reindent)
      final List<Pair<RangeMarker, ? extends PostponedAction>> normalizedActions = normalizeAndReorderPostponedActions(rangesToProcess, document);

      // only in following loop real changes in document are made
      for (final Pair<RangeMarker, ? extends PostponedAction> normalizedAction : normalizedActions) {
        normalizedAction.getSecond().processRange(normalizedAction.getFirst(), key);
      }
    }
  }

  private List<Pair<RangeMarker, ? extends PostponedAction>> normalizeAndReorderPostponedActions(final TreeMap<RangeMarker, PostponedAction> rangesToProcess, Document document) {
    final List<Pair<RangeMarker, ReformatAction>> freeFormatingActions = new ArrayList<Pair<RangeMarker, ReformatAction>>();
    final List<Pair<RangeMarker, ReindentAction>> indentActions = new ArrayList<Pair<RangeMarker, ReindentAction>>();

    RangeMarker accumulatedRange = null;
    PostponedAction accumulatedRangeAction = null;
    Iterator<Map.Entry<RangeMarker, PostponedAction>> iterator = rangesToProcess.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<RangeMarker, PostponedAction> entry = iterator.next();
      final RangeMarker textRange = entry.getKey();
      final PostponedAction action = entry.getValue();
      if (accumulatedRange == null) {
        accumulatedRange = textRange;
        accumulatedRangeAction = action;
        iterator.remove();
      }
      else if (accumulatedRange.getStartOffset() > textRange.getEndOffset() ||
               (accumulatedRange.getStartOffset() == textRange.getEndOffset() &&
                !canStickActionsTogether(accumulatedRangeAction, accumulatedRange, action, textRange))) {
        // action can be pushed
        if (accumulatedRangeAction instanceof ReindentAction)
          indentActions.add(new Pair<RangeMarker, ReindentAction>(accumulatedRange, (ReindentAction)accumulatedRangeAction));
        else
          freeFormatingActions.add(new Pair<RangeMarker, ReformatAction>(accumulatedRange, (ReformatAction)accumulatedRangeAction));

        accumulatedRange = textRange;
        accumulatedRangeAction = action;
        iterator.remove();
      }
      else if (accumulatedRangeAction instanceof ReformatAction && action instanceof ReindentAction) {
        // split accumulated reformat range into two
        if (accumulatedRange.getStartOffset() < textRange.getStartOffset()) {
          final RangeMarker endOfRange = document.createRangeMarker(accumulatedRange.getStartOffset(), textRange.getStartOffset());
          // add heading reformat part
          rangesToProcess.put(endOfRange, accumulatedRangeAction);
          // and manage heading whitespace because formatter does not edit it in previous action
          iterator = rangesToProcess.entrySet().iterator();
          //noinspection StatementWithEmptyBody
          while(iterator.next().getKey() != textRange);
        }
        final RangeMarker rangeToProcess = document.createRangeMarker(textRange.getEndOffset(), accumulatedRange.getEndOffset());
        freeFormatingActions.add(new Pair<RangeMarker, ReformatAction>(rangeToProcess, new ReformatWithHeadingWhitespaceAction()));
        accumulatedRange = textRange;
        accumulatedRangeAction = action;
        iterator.remove();
      }
      else {
        if (!(accumulatedRangeAction instanceof ReindentAction)) {
          iterator.remove();
          if(accumulatedRangeAction instanceof ReformatAction && action instanceof ReformatWithHeadingWhitespaceAction &&
             accumulatedRange.getStartOffset() == textRange.getStartOffset() ||
             accumulatedRangeAction instanceof ReformatWithHeadingWhitespaceAction && action instanceof ReformatAction &&
             accumulatedRange.getStartOffset() < textRange.getStartOffset()){
            accumulatedRangeAction = action;
          }
          accumulatedRange = document.createRangeMarker(Math.min(accumulatedRange.getStartOffset(), textRange.getStartOffset()),
                                                        Math.max(accumulatedRange.getEndOffset(), textRange.getEndOffset()));
        }
        else if(action instanceof ReindentAction) iterator.remove(); // TODO[ik]: need to be fixed to correctly process indent inside indent
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
    result.addAll(freeFormatingActions);
    result.addAll(indentActions);
    return result;
  }

  private boolean canStickActionsTogether(final PostponedAction currentAction,
                                          final RangeMarker currentRange,
                                          final PostponedAction nextAction,
                                          final RangeMarker nextRange) {
    // empty reformat markers can't sticked together with any action
    if(nextAction instanceof ReformatWithHeadingWhitespaceAction && nextRange.getStartOffset() == nextRange.getEndOffset()) return false;
    if(currentAction instanceof ReformatWithHeadingWhitespaceAction && currentRange.getStartOffset() == currentRange.getEndOffset()) return false;
    // reindent actions can't be sticked at all
    return !(currentAction instanceof ReindentAction);
  }

  private void createActionsMap(final List<ASTNode> astNodes, final FileViewProvider provider,
                                final TreeMap<RangeMarker, PostponedAction> rangesToProcess) {
    final Set<ASTNode> nodesToProcess = new HashSet<ASTNode>(astNodes);
    final Document document = provider.getDocument();
    for (final ASTNode node : astNodes) {
      nodesToProcess.remove(node);
      final FileElement fileElement = TreeUtil.getFileElement((TreeElement)node);
      if (fileElement == null || ((PsiFile)fileElement.getPsi()).getViewProvider() != provider) continue;
      final boolean isGenerated = CodeEditUtil.isNodeGenerated(node);
      ((TreeElement)node).acceptTree(new RecursiveTreeElementVisitor() {
        boolean inGeneratedContext = !isGenerated;
        protected boolean visitNode(TreeElement current) {
          if(nodesToProcess.contains(current)) return false;
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
  }

  private void handleReformatMarkers(final FileViewProvider key,
                                                                  final TreeMap<RangeMarker, PostponedAction> rangesToProcess) {
    final Document document = key.getDocument();
    for (final FileElement fileElement : ((SingleRootFileViewProvider)key).getKnownTreeRoots()) {
      fileElement.acceptTree(
        new RecursiveTreeElementVisitor(){
          protected boolean visitNode(TreeElement element) {
            if(CodeEditUtil.isMarkedToReformatBefore(element)) {
              CodeEditUtil.markToReformatBefore(element, false);
              rangesToProcess.put(document.createRangeMarker(element.getStartOffset(), element.getStartOffset()),
                             new ReformatWithHeadingWhitespaceAction());
            }
            return true;
          }
        });
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
