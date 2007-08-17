package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.ASTDiffBuilder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.LeafPsiElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.jsp.el.IELElementType;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 21, 2005
 * Time: 3:30:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiBuilderImpl extends UserDataHolderBase implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl");

  private Language myLanguage;
  private int[] myLexStarts;
  private int[] myLexEnds;
  private IElementType[] myLexTypes;

  private final MyList myProduction = new MyList();

  private final Lexer myLexer;
  private final boolean myFileLevelParsing;
  private final TokenSet myWhitespaces;
  private TokenSet myComments;

  private CharTable myCharTable;
  private int myCurrentLexem;
  private Token myCurrentToken = null;
  private final CharSequence myText;
  private final char[] myTextArray;
  private boolean myDebugMode = false;
  private ASTNode myOriginalTree = null;
  private final Token myMutableToken = new Token();
  private int myLexemCount = 0;

  private final LimitedPool<StartMarker> START_MARKERS = new LimitedPool<StartMarker>(2000, new LimitedPool.ObjectFactory<StartMarker>() {
    public StartMarker create() {
      return new StartMarker();
    }

    public void cleanup(final StartMarker startMarker) {
      startMarker.clean();
    }
  });

  private final LimitedPool<DoneMarker> DONE_MARKERS = new LimitedPool<DoneMarker>(2000, new LimitedPool.ObjectFactory<DoneMarker>() {
    public DoneMarker create() {
      return new DoneMarker();
    }

    public void cleanup(final DoneMarker doneMarker) {
      doneMarker.clean();
    }
  });

  public PsiBuilderImpl(Language lang, Lexer lexer, final ASTNode chameleon, Project project, CharSequence text) {
    myText = text;
    myTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    myLanguage = lang;
    ParserDefinition parserDefinition = lang.getParserDefinition();
    assert parserDefinition != null;
    myLexer = lexer != null ? lexer : parserDefinition.createLexer(project);
    myWhitespaces = parserDefinition.getWhitespaceTokens();
    myComments = parserDefinition.getCommentTokens();
    myCharTable = SharedImplUtil.findCharTableByTree(chameleon);

    if (chameleon instanceof ChameleonElement) { // Shall always be true BTW
      myOriginalTree = chameleon.getTreeParent().getUserData(BlockSupport.TREE_TO_BE_REPARSED);
    }

    myFileLevelParsing = myCharTable == null || myOriginalTree != null;

    myLexer.start(myText, 0, text.length(), 0);

    int approxLexCount = Math.max(10, text.length() / 5);

    myLexStarts = new int[approxLexCount];
    myLexEnds = new int[approxLexCount];
    myLexTypes = new IElementType[approxLexCount];
  }

  /**
   * For tests only!
   */
  public PsiBuilderImpl(final Lexer lexer, final TokenSet whitespaces, final TokenSet comments, CharTable charTable, CharSequence text) {
    myWhitespaces = whitespaces;
    myLexer = lexer;
    myComments = comments;
    myText = text;
    myTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    myCharTable = charTable;

    myFileLevelParsing = myCharTable == null;

    myLexer.start(myText, 0, text.length(), 0);
    myLexStarts = new int[5];
    myLexEnds = new int[5];
    myLexTypes = new IElementType[5];
  }

  public void enforceCommentTokens(TokenSet tokens) {
    myComments = tokens;
  }

  @Nullable
  public LanguageDialect getLanguageDialect() {
    return myLanguage instanceof LanguageDialect ? (LanguageDialect)myLanguage:null;
  }

  private static abstract class Node implements LighterASTNode {
    public abstract int hc();
  }

  private static class StartMarker extends ProductionMarker implements Marker {
    public PsiBuilderImpl myBuilder;
    public IElementType myType;
    public DoneMarker myDoneMarker;
    public Throwable myDebugAllocationPosition;
    public ProductionMarker firstChild;
    public ProductionMarker lastChild;
    private int myHC = -1;

    public void clean() {
      super.clean();
      myBuilder = null;
      myType = null;
      myDoneMarker = null;
      myDebugAllocationPosition = null;
      firstChild = null;
      lastChild = null;
      myHC = -1;
    }

    public int hc() {
      if (myHC == -1) {
        PsiBuilderImpl builder = myBuilder;
        int hc = 0;
        final CharSequence buf = builder.myText;
        final char[] bufArray = builder.myTextArray;
        ProductionMarker child = firstChild;
        int lexIdx = myLexemIndex;

        while (child != null) {
          int lastLeaf = child.myLexemIndex;
          for (int i = builder.myLexStarts[lexIdx]; i < builder.myLexStarts[lastLeaf]; i++) {
            hc += bufArray != null ? bufArray[i] : buf.charAt(i);
          }
          lexIdx = lastLeaf;
          hc += child.hc();
          if (child instanceof StartMarker) {
            lexIdx = ((StartMarker)child).myDoneMarker.myLexemIndex;
          }
          child = child.next;
        }

        for (int i = builder.myLexStarts[lexIdx]; i < builder.myLexStarts[myDoneMarker.myLexemIndex]; i++) {
          hc += bufArray != null ? bufArray[i]:buf.charAt(i);
        }

        myHC = hc;
      }

      return myHC;
    }

    public int getStartOffset() {
      return myBuilder.myLexStarts[myLexemIndex];
    }

    public int getEndOffset() {
      return myBuilder.myLexStarts[myDoneMarker.myLexemIndex];
    }

    public void addChild(ProductionMarker node) {
      if (firstChild == null) {
        firstChild = node;
        lastChild = node;
      }
      else {
        lastChild.next = node;
        lastChild = node;
      }
    }

    public Marker precede() {
      return myBuilder.preceed(this);
    }

    public void drop() {
      myBuilder.drop(this);
    }

    public void rollbackTo() {
      myBuilder.rollbackTo(this);
    }

    public void done(IElementType type) {
      myType = type;
      myBuilder.done(this);
    }

    public void doneBefore(IElementType type, Marker before) {
      myType = type;
      myBuilder.doneBefore(this, before);
    }

    public void doneBefore(final IElementType type, final Marker before, final String errorMessage) {
      myBuilder.myProduction.add(myBuilder.myProduction.lastIndexOf(before), new ErrorItem(myBuilder, errorMessage, ((StartMarker)before).myLexemIndex));
      doneBefore(type, before);
    }

    public void error(String message) {
      myType = ElementType.ERROR_ELEMENT;
      myBuilder.error(this, message);
    }

    public IElementType getTokenType() {
      return myType;
    }
  }

  private Marker preceed(final StartMarker marker) {
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("Cannot precede dropped or rolled-back marker");
    }
    StartMarker pre = createMarker(marker.myLexemIndex);
    myProduction.add(idx, pre);
    return pre;
  }

  public class Token extends Node {
    public IElementType myTokenType;
    public int myTokenStart;
    public int myTokenEnd;
    public int myHC = -1;

    public int hc() {
      if (myHC == -1) {
        int hc = 0;
        final int start = myTokenStart;
        final int end = myTokenEnd;
        final CharSequence buf = myText;
        final char[] bufArray = myTextArray;

        for (int i = start; i < end; i++) {
          hc += bufArray != null ? bufArray[i] : buf.charAt(i);
        }

        myHC = hc;
      }

      return myHC;
    }

    public int getEndOffset() {
      return myTokenEnd;
    }

    public int getStartOffset() {
      return myTokenStart;
    }

    public CharSequence getText() {
      return myText.subSequence(myTokenStart, myTokenEnd);
    }

    public IElementType getTokenType() {
      return myTokenType;
    }
  }

  private abstract static class ProductionMarker extends Node {
    public int myLexemIndex;
    ProductionMarker next;

    public void clean() {
      myLexemIndex = 0;
      next = null;
    }
  }

  private static class DoneMarker extends ProductionMarker {
    public StartMarker myStart;

    public DoneMarker() {}

    public DoneMarker(final StartMarker marker, int currentLexem) {
      myLexemIndex = currentLexem;
      myStart = marker;
    }

    public int hc() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public IElementType getTokenType() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public int getEndOffset() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public int getStartOffset() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public void clean() {
      super.clean();
      myStart = null;
    }
  }

  private static class DoneWithErrorMarker extends DoneMarker {
    public String myMessage;

    public DoneWithErrorMarker(final StartMarker marker, int currentLexem, String message) {
      super(marker, currentLexem);
      myMessage = message;
    }

    public void clean() {
      super.clean();
      myMessage = null;
    }
  }

  private static class ErrorItem extends ProductionMarker {
    String myMessage;
    private final PsiBuilderImpl myBuilder;

    public ErrorItem(PsiBuilderImpl builder, final String message, int idx) {
      myBuilder = builder;
      myLexemIndex = idx;
      myMessage = message;
    }

    public int hc() {
      return 0;
    }

    public int getEndOffset() {
      return myBuilder.myLexStarts[myLexemIndex];
    }

    public int getStartOffset() {
      return myBuilder.myLexStarts[myLexemIndex];
    }

    public IElementType getTokenType() {
      return ElementType.ERROR_ELEMENT;
    }

    public void clean() {
      super.clean();
      myMessage = null;
    }
  }

  public CharSequence getOriginalText() {
    return myText;
  }

  public IElementType getTokenType() {
    final Token lex = getCurrentToken();
    if (lex == null) return null;

    return lex.getTokenType();
  }

  public void advanceLexer() {
    myCurrentToken = null;
    myCurrentLexem++;
  }

  public int getCurrentOffset() {
    final PsiBuilderImpl.Token token = getCurrentToken();
    if (token == null) return getOriginalText().length();
    return token.myTokenStart;
  }

  @Nullable
  public String getTokenText() {
    final PsiBuilderImpl.Token token = getCurrentToken();
    return token != null ? token.getText().toString() : null;
  }

  @Nullable
  public Token getCurrentToken() {
    if (myCurrentToken == null) {
      while (true) {
        if (getTokenOrWhitespace()) return null;

        if (whitespaceOrComment(myLexTypes[myCurrentLexem])) {
          myCurrentLexem++;
        }
        else {
          break;
        }
      }

      myCurrentToken = myMutableToken;
      myCurrentToken.myTokenType = myLexTypes[myCurrentLexem];
      myCurrentToken.myTokenStart = myLexStarts[myCurrentLexem];
      myCurrentToken.myTokenEnd = myLexEnds[myCurrentLexem];
    }

    return myCurrentToken;
  }

  private boolean getTokenOrWhitespace() {
    while (myCurrentLexem >= myLexemCount) {
      final IElementType type = myLexer.getTokenType();
      if (type == null) return true;

      if (myLexemCount + 1 >= myLexStarts.length) {
        int newSize = myLexemCount * 3 / 2;

        resizeLexems(newSize);
      }

      myLexTypes[myLexemCount] = type;
      myLexStarts[myLexemCount] = myLexer.getTokenStart();
      myLexEnds[myLexemCount] = myLexer.getTokenEnd();

      myLexer.advance();
      myLexemCount++;
    }
    return false;
  }

  private void resizeLexems(final int newSize) {
    int[] newStarts = new int[newSize];
    System.arraycopy(myLexStarts, 0, newStarts, 0, myLexemCount);
    myLexStarts = newStarts;

    int[] newEnds = new int[newSize];
    System.arraycopy(myLexEnds, 0, newEnds, 0, myLexemCount);
    myLexEnds = newEnds;

    IElementType[] newTypes = new IElementType[newSize];
    System.arraycopy(myLexTypes, 0, newTypes, 0, myLexemCount);
    myLexTypes = newTypes;
  }

  private boolean whitespaceOrComment(IElementType token) {
    return myWhitespaces.contains(token) || myComments.contains(token);
  }

  public Marker mark() {
    StartMarker marker = createMarker(myCurrentLexem);

    myProduction.add(marker);
    return marker;
  }

  private StartMarker createMarker(final int lexemIndex) {
    StartMarker marker;
    marker = START_MARKERS.alloc();
    marker.myLexemIndex = lexemIndex;
    marker.myBuilder = this;

    if (myDebugMode) {
      marker.myDebugAllocationPosition = new Throwable("Created at the following trace.");
    }
    return marker;
  }

  public boolean eof() {
    return myCurrentLexem + 1 >= myLexemCount && getCurrentToken() == null;
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void rollbackTo(Marker marker) {
    myCurrentLexem = ((StartMarker)marker).myLexemIndex;
    myCurrentToken = null;
    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("The marker must be added before rolled back to.");
    }
    myProduction.removeRange(idx, myProduction.size());
    START_MARKERS.recycle((StartMarker)marker);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void doneBefore(Marker marker, Marker before) {
// TODO: there could be not done markers after 'marker' and that's normal
    if (((StartMarker)marker).myDoneMarker != null) {
      LOG.error("Marker already done.");
    }

    int idx = myProduction.lastIndexOf(marker);
    if (idx < 0) {
      LOG.error("Marker never been added.");
    }

    int beforeIndex = myProduction.lastIndexOf(before);

    DoneMarker doneMarker = DONE_MARKERS.alloc();
    doneMarker.myLexemIndex = ((StartMarker)before).myLexemIndex;
    doneMarker.myStart = (StartMarker)marker;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(beforeIndex, doneMarker);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void drop(Marker marker) {
    final boolean removed = myProduction.remove(myProduction.lastIndexOf(marker)) == marker;
    if (!removed) {
      LOG.error("The marker must be added before it is dropped.");
    }
    START_MARKERS.recycle((StartMarker)marker);
  }

  public void error(Marker marker, String message) {
    doValidityChecks(marker);

    DoneWithErrorMarker doneMarker = new DoneWithErrorMarker((StartMarker)marker, myCurrentLexem, message);
    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  public void done(Marker marker) {
    doValidityChecks(marker);


    DoneMarker doneMarker = DONE_MARKERS.alloc();
    doneMarker.myStart = (StartMarker)marker;
    doneMarker.myLexemIndex = myCurrentLexem;

    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "SuspiciousMethodCalls"})
  private void doValidityChecks(final Marker marker) {
    if (myDebugMode) {
      final DoneMarker doneMarker = ((StartMarker)marker).myDoneMarker;
      if (doneMarker != null) {
        LOG.error("Marker already done.");
      }
      int idx = myProduction.lastIndexOf(marker);
      if (idx < 0) {
        LOG.error("Marker never been added.");
      }

      for (int i = myProduction.size() - 1; i > idx; i--) {
        Object item = myProduction.get(i);
        if (item instanceof Marker) {
          StartMarker otherMarker = (StartMarker)item;
          if (otherMarker.myDoneMarker == null) {
            final Throwable debugAllocOther = otherMarker.myDebugAllocationPosition;
            final Throwable debugAllocThis = ((StartMarker)marker).myDebugAllocationPosition;
            if (debugAllocOther != null) {
              debugAllocThis.printStackTrace(System.err);
              debugAllocOther.printStackTrace(System.err);
            }
            LOG.error("Another not done marker added after this one. Must be done before this.");
          }
        }
      }
    }
  }

  public void error(String messageText) {
    if (myProduction.get(myProduction.size() - 1) instanceof ErrorItem) return;
    myProduction.add(new ErrorItem(this, messageText, myCurrentLexem));
  }

  public ASTNode getTreeBuilt() {
    try {
      StartMarker rootMarker = prepareLightTree();

      if (myOriginalTree != null) {
        merge(myOriginalTree, rootMarker);
        throw new BlockSupport.ReparsedSuccessfullyException();
      }
      else {
        final ASTNode rootNode = createRootAST(rootMarker);

        bind((CompositeElement)rootNode, rootMarker);

        return rootNode;
      }
    }
    finally {
      for (ProductionMarker marker : myProduction) {
        if (marker instanceof StartMarker) {
          START_MARKERS.recycle((StartMarker)marker);
        }
        else if (marker instanceof DoneMarker) {
          DONE_MARKERS.recycle((DoneMarker)marker);
        }
      }
    }
  }

  public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    StartMarker rootMarker = prepareLightTree();
    return new MyTreeStructure(rootMarker);
  }

  private ASTNode createRootAST(final StartMarker rootMarker) {
    final ASTNode rootNode;
    if (myFileLevelParsing) {
      rootNode = new FileElement(rootMarker.myType);
      myCharTable = ((FileElement)rootNode).getCharTable();
    }
    else {
      rootNode = createComposite(rootMarker);
      rootNode.putUserData(CharTable.CHAR_TABLE_KEY, myCharTable);
    }
    return rootNode;
  }

  private class MyBuilder implements DiffTreeChangeBuilder<ASTNode, LighterASTNode> {
    private ASTDiffBuilder myDelegate;
    private ASTConvertor myConvertor;

    public MyBuilder(PsiFileImpl file, LighterASTNode rootNode) {
      myDelegate = new ASTDiffBuilder(file);
      myConvertor = new ASTConvertor((Node)rootNode);
    }

    public void nodeDeleted(final ASTNode oldParent, final ASTNode oldNode) {
      myDelegate.nodeDeleted(oldParent, oldNode);
    }

    public void nodeInserted(final ASTNode oldParent, final LighterASTNode newNode, final int pos) {
      myDelegate.nodeInserted(oldParent, myConvertor.convert((Node)newNode), pos);
    }

    public void nodeReplaced(final ASTNode oldChild, final LighterASTNode newChild) {
      myDelegate.nodeReplaced(oldChild, myConvertor.convert((Node)newChild));
    }

    public TreeChangeEvent getEvent() {
      return myDelegate.getEvent();
    }
  }

  private void merge(final ASTNode oldNode, final StartMarker newNode) {
    final PsiFileImpl file = (PsiFileImpl)oldNode.getPsi().getContainingFile();
    final PomModel model = file.getProject().getModel();

    try {
      model.runTransaction(new PomTransactionBase(file, model.getModelAspect(TreeAspect.class)) {
        public PomModelEvent runInner() throws IncorrectOperationException {
          final MyBuilder builder = new MyBuilder(file, newNode);
          DiffTree.diff(new ASTStructure(oldNode), new MyTreeStructure(newNode), new MyComparator(), builder);
          file.subtreeChanged();

          return new TreeAspectEvent(model, builder.getEvent());
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private StartMarker prepareLightTree() {
    final MyList fProduction = myProduction;
    StartMarker rootMarker = (StartMarker)fProduction.get(0);

    for (int i = 1; i < fProduction.size() - 1; i++) {
      ProductionMarker item = fProduction.get(i);

      if (item instanceof StartMarker) {
        IElementType nextTokenType;
        while (item.myLexemIndex < myLexemCount &&
               ( myWhitespaces.contains(nextTokenType = myLexTypes[item.myLexemIndex]) ||
                 myComments.contains(nextTokenType)
               )
              ) {
          item.myLexemIndex++;
        }
      }
      else if (item instanceof DoneMarker || item instanceof ErrorItem) {
        int prevProductionLexIndex = fProduction.get(i - 1).myLexemIndex;
        IElementType prevTokenType;

        while (item.myLexemIndex > prevProductionLexIndex && item.myLexemIndex < myLexemCount &&
               ( myWhitespaces.contains(prevTokenType = myLexTypes[item.myLexemIndex - 1]) ||
                 myComments.contains(prevTokenType)
               )
              ) {
          item.myLexemIndex--;
        }
      }
    }

    StartMarker curNode = rootMarker;

    int lastErrorIndex = -1;

    Stack<StartMarker> nodes = new Stack<StartMarker>();
    nodes.push(rootMarker);

    for (int i = 1; i < fProduction.size(); i++) {
      ProductionMarker item = fProduction.get(i);

      if (curNode == null) LOG.error("Unexpected end of the production");

      if (item instanceof StartMarker) {
        StartMarker marker = (StartMarker)item;
        curNode.addChild(marker);
        nodes.push(curNode);
        curNode = marker;
      }
      else if (item instanceof DoneMarker) {
        curNode = nodes.pop();
      }
      else if (item instanceof ErrorItem) {
        int curToken = item.myLexemIndex;
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        curNode.addChild(item);
      }
    }

    /* TODO
    final boolean allTokensInserted = curToken == myLexStarts.size();
    if (!allTokensInserted) {
      LOG.assertTrue(false, "Not all of the tokens inserted to the tree, parsed text:\n" + myText);
    }
    */

    if (myLexStarts.length <= myCurrentLexem) {
      resizeLexems(myCurrentLexem + 1);
    }

    myLexStarts[myCurrentLexem] = myLexer.getTokenStart(); // $ terminating token.;
    myLexEnds[myCurrentLexem] = 0;
    myLexTypes[myCurrentLexem] = null;

    LOG.assertTrue(curNode == rootMarker, "Unbalanced tree");
    return rootMarker;
  }

  private void bind(CompositeElement ast, StartMarker marker) {
    bind(ast, marker, marker.myLexemIndex);
  }

  private int bind(CompositeElement ast, StartMarker marker, int lexIndex) {
    ProductionMarker child = marker.firstChild;
    while (child != null) {
      if (child instanceof StartMarker) {
        final StartMarker childMarker = (StartMarker)child;

        lexIndex = insertLeafs(lexIndex, childMarker.myLexemIndex, ast);

        CompositeElement childNode = createComposite(childMarker);
        TreeUtil.addChildren(ast, childNode);
        lexIndex = bind(childNode, childMarker, lexIndex);

        lexIndex = insertLeafs(lexIndex, childMarker.myDoneMarker.myLexemIndex, ast);
      }
      else if (child instanceof ErrorItem) {
        lexIndex = insertLeafs(lexIndex, ((ErrorItem)child).myLexemIndex, ast);
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)child).myMessage);
        TreeUtil.addChildren(ast, errorElement);
      }

      child = child.next;
    }

    return insertLeafs(lexIndex, marker.myDoneMarker.myLexemIndex, ast);
  }

  private int insertLeafs(int curToken, int lastIdx, final CompositeElement curNode) {
    lastIdx = Math.min(lastIdx, myLexemCount);
    while (curToken < lastIdx) {
      final int start = myLexStarts[curToken];
      final int end = myLexEnds[curToken];
      if (start < end) { // Empty token. Most probably a parser directive like indent/dedent in phyton
        TreeUtil.addChildren(curNode, createLeaf(myLexTypes[curToken], start, end));
      }
      curToken++;
    }

    return curToken;
  }

  private static CompositeElement createComposite(final StartMarker marker) {
    final IElementType type = marker.myType;
    if (type == ElementType.ERROR_ELEMENT) {
      CompositeElement childNode = new PsiErrorElementImpl();
      if (marker.myDoneMarker instanceof DoneWithErrorMarker) {
        ((PsiErrorElementImpl)childNode).setErrorDescription(((DoneWithErrorMarker)marker.myDoneMarker).myMessage);
      }
      return childNode;
    }
    else if (type instanceof IXmlElementType || type instanceof IJspElementType && !(type instanceof IELElementType)) { // hack....
      return Factory.createCompositeElement(type);
    }
    return new CompositeElement(type);
  }

  private class MyComparator implements ShallowNodeComparator<ASTNode, LighterASTNode> {
    public ThreeState deepEqual(final ASTNode oldNode, final LighterASTNode newNode) {
      if (newNode instanceof Token) {
        if (oldNode instanceof LeafElement) {
          return ((LeafElement)oldNode).textMatches(myText, ((Token)newNode).myTokenStart, ((Token)newNode).myTokenEnd) ? ThreeState.YES : ThreeState.NO;
        }

        if (oldNode.getElementType() instanceof IChameleonElementType && newNode.getTokenType() instanceof IChameleonElementType) {
          return ((TreeElement)oldNode).textMatches(myText, ((Token)newNode).myTokenStart, ((Token)newNode).myTokenEnd) ? ThreeState.YES : ThreeState.NO;
        }
      }

      return ThreeState.UNSURE;
    }

    @Nullable
    private String getErrorMessage(LighterASTNode node) {
      if (node instanceof ErrorItem) return ((ErrorItem)node).myMessage;
      if (node instanceof StartMarker) {
        final StartMarker marker = (StartMarker)node;
        if (marker.myType == ElementType.ERROR_ELEMENT && marker.myDoneMarker instanceof DoneWithErrorMarker) {
          return ((DoneWithErrorMarker)marker.myDoneMarker).myMessage;
        }
      }

      return null;
    }

    public boolean typesEqual(final ASTNode n1, final LighterASTNode n2) {
      if (n1 instanceof PsiWhiteSpaceImpl) {
        return n2.getTokenType() == XmlTokenType.XML_REAL_WHITE_SPACE || myWhitespaces.contains(n2.getTokenType());
      }

      return n1.getElementType() == n2.getTokenType();
    }

    public boolean hashcodesEqual(final ASTNode n1, final LighterASTNode n2) {
      if (n1 instanceof LeafElement && n2 instanceof Token) {
        return ((LeafElement)n1).textMatches(myText, ((Token)n2).myTokenStart, ((Token)n2).myTokenEnd);
      }

      if (n1 instanceof PsiErrorElement && n2.getTokenType() == ElementType.ERROR_ELEMENT) {
        final PsiErrorElement e1 = ((PsiErrorElement)n1);
        if (!Comparing.equal(e1.getErrorDescription(), getErrorMessage(n2))) return false;
      }

      return ((TreeElement)n1).hc() == ((Node)n2).hc();
    }
  }

  private class MyTreeStructure implements FlyweightCapableTreeStructure<LighterASTNode> {
    private LimitedPool<Token> myPool = new LimitedPool<Token>(1000, new LimitedPool.ObjectFactory<Token>() {
      public void cleanup(final Token token) {
        token.myHC = -1;
      }

      public Token create() {
        return new Token();
      }
    });

    private final StartMarker myRoot;

    public MyTreeStructure(final StartMarker root) {
      myRoot = root;
    }

    public LighterASTNode prepareForGetChildren(final LighterASTNode o) {
      return o;
    }

    public LighterASTNode getRoot() {
      return myRoot;
    }

    public void disposeChildren(final LighterASTNode[] nodes, final int count) {
      for (int i = 0; i < count; i++) {
        LighterASTNode node = nodes[i];
        if (node instanceof Token) {
          myPool.recycle((Token)node);
        }
      }
    }

    private int count;
    public int getChildren(final LighterASTNode item, final Ref<LighterASTNode[]> into) {
      if (item instanceof Token || item instanceof ErrorItem) return 0;
      StartMarker marker = (StartMarker)item;

      count = 0;

      ProductionMarker child = marker.firstChild;
      int lexIndex = marker.myLexemIndex;
      while (child != null) {
        lexIndex = insertLeafs(lexIndex, child.myLexemIndex, into);
        ensureCapacity(into);
        into.get()[count++] = child;
        if (child instanceof StartMarker) {
          lexIndex = ((StartMarker)child).myDoneMarker.myLexemIndex;
        }
        child = child.next;
      }
      insertLeafs(lexIndex, marker.myDoneMarker.myLexemIndex, into);

      return count;
    }

    private void ensureCapacity(final Ref<LighterASTNode[]> into) {
      LighterASTNode[] old = into.get();
      if (old == null) {
        old = new LighterASTNode[10];
        into.set(old);
      }
      else if (count >= old.length) {
        LighterASTNode[] newStore = new LighterASTNode[(count * 3) / 2];
        System.arraycopy(old, 0, newStore, 0, count);
        into.set(newStore);
      }
    }


    private int insertLeafs(int curToken, int lastIdx, Ref<LighterASTNode[]> into) {
      lastIdx = Math.min(lastIdx, myLexemCount);
      while (curToken < lastIdx) {
        final int start = myLexStarts[curToken];
        final int end = myLexEnds[curToken];
        if (start < end) { // Empty token. Most probably a parser directive like indent/dedent in phyton
          Token lexem = myPool.alloc();

          lexem.myTokenType = myLexTypes[curToken];
          lexem.myTokenStart = start;
          lexem.myTokenEnd = end;
          ensureCapacity(into);
          into.get()[count++] = lexem;
        }
        curToken++;
      }

      return curToken;
    }
  }

  private class ASTConvertor implements Convertor<Node, ASTNode> {
    private Node myRoot;

    public ASTConvertor(final Node root) {
      myRoot = root;
    }

    public ASTNode convert(final Node n) {
      if (n instanceof Token) {
        return createLeaf(n.getTokenType(), ((Token)n).myTokenStart, ((Token)n).myTokenEnd);
      }
      else if (n instanceof ErrorItem) {
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)n).myMessage);
        return errorElement;
      }
      else {
        final CompositeElement composite = n == myRoot ? (CompositeElement)createRootAST((StartMarker)myRoot) : createComposite((StartMarker)n);
        bind(composite, (StartMarker)n);
        return composite;
      }
    }
  }

  public void setDebugMode(boolean dbgMode) {
    myDebugMode = dbgMode;
  }

  public Lexer getLexer() {
    return myLexer;
  }

  @NotNull
  private LeafElement createLeaf(final IElementType type, final int start, final int end) {
    if (myWhitespaces.contains(type)) {
      return new PsiWhiteSpaceImpl(myText, start, end, myCharTable);
    }
    else if (myComments.contains(type)) {
      return new PsiCommentImpl(type, myText, start, end, myCharTable);
    }
    else if (type instanceof IChameleonElementType) {
      return new ChameleonElement(type, myText, start, end, myCharTable);
    }
    else if (type instanceof IXmlElementType || type instanceof IJspElementType && !(type instanceof IELElementType)) {
      return Factory.createLeafElement(type, myText, start, end, myCharTable);
    }
    else if (type instanceof LeafPsiElementType) {
      return (LeafElement)((LeafPsiElementType)type).createLeafNode(myText, start, end, myCharTable);
    }

    return new LeafPsiElement(type, myText, start, end, myCharTable);
  }

  /**
   * just to make removeRange method available.
   */
  private static class MyList extends ArrayList<ProductionMarker> {
    private static final Field ourElementDataField;
    static {
      Field f;
      try {
        f = ArrayList.class.getDeclaredField("elementData");
        f.setAccessible(true);
      } catch(NoSuchFieldException e) {
        LOG.error(e);
        f = null;
      }
      ourElementDataField = f;
    }

    private Object[] cachedElementData;

    public void removeRange(final int fromIndex, final int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }

    MyList() {
      super(256);
    }
    
    public int lastIndexOf(final Object o) {
      for (int i = size()-1; i >= 0; i--)
        if (cachedElementData[i]==o) return i;
      return -1;
    }

    public void ensureCapacity(final int minCapacity) {
      if (cachedElementData == null || minCapacity >= cachedElementData.length) {
        super.ensureCapacity(minCapacity);
        initCachedField();
      }
    }

    private void initCachedField() {
      try {
        cachedElementData = (Object[])ourElementDataField.get(this);
      } catch(Exception e) {
        LOG.error(e);
      }
    }
  }
}
