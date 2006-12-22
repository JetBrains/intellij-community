package com.intellij.lang.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.jsp.el.IELElementType;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.DiffTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 21, 2005
 * Time: 3:30:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiBuilderImpl extends UserDataHolderBase implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl");

  private final List<Token> myLexems = new ArrayList<Token>();
  private final MyList myProduction = new MyList();

  private final Lexer myLexer;
  private final boolean myFileLevelParsing;
  private final TokenSet myWhitespaces;
  private TokenSet myComments;

  private CharTable myCharTable;
  private int myCurrentLexem;
  private Token myCurrentToken = null;
  private CharSequence myText;
  private boolean myDebugMode = false;
  private ASTNode myOriginalTree = null;

  public PsiBuilderImpl(Language lang, Lexer lexer, final ASTNode chameleon, Project project, CharSequence text) {
    myText = text;
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
  }

  /**
   * For tests only!
   */
  public PsiBuilderImpl(final Lexer lexer, final TokenSet whitespaces, final TokenSet comments, CharTable charTable, CharSequence text) {
    myWhitespaces = whitespaces;
    myLexer = lexer;
    myComments = comments;
    myText = text;
    myCharTable = charTable;

    myFileLevelParsing = myCharTable == null;

    myLexer.start(myText, 0, text.length(), 0);
  }

  public void enforeCommentTokens(TokenSet tokens) {
    myComments = tokens;
  }

  private static abstract class Node {
    Node next;

    public abstract IElementType getTokenType();
    public abstract CharSequence getText();

    public abstract int hc();
  }

  private class StartMarker extends ProductionMarker implements Marker {
    public IElementType myType;
    public DoneMarker myDoneMarker = null;
    public Throwable myDebugAllocationPosition = null;
    public Node firstChild;
    public Node lastChild;
    private int myHC = -1;

    public StartMarker(int idx) {
      super(idx);
      if (myDebugMode) {
        myDebugAllocationPosition = new Throwable("Created at the following trace.");
      }
    }

    public int hc() {
      if (myHC == -1) {
        int hc = 0;
        Node child = firstChild;
        while (child != null) {
          hc += child.hc();
          child = child.next;
        }
        myHC = hc;
      }

      return myHC;
    }

    public CharSequence getText() {
      return myLexer.getBufferSequence().subSequence(myLexems.get(myLexemIndex).myTokenStart, myLexems.get(myDoneMarker.myLexemIndex).myTokenStart);
    }

    public void addChild(Node node) {
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
      return PsiBuilderImpl.this.preceed(this);
    }

    public void drop() {
      PsiBuilderImpl.this.drop(this);
    }

    public void rollbackTo() {
      PsiBuilderImpl.this.rollbackTo(this);
    }

    public void done(IElementType type) {
      myType = type;
      PsiBuilderImpl.this.done(this);
    }

    public void doneBefore(IElementType type, Marker before) {
      myType = type;
      PsiBuilderImpl.this.doneBefore(this, before);
    }

    public void doneBefore(final IElementType type, final Marker before, final String errorMessage) {
      myProduction.add(myProduction.lastIndexOf(before), new ErrorItem(errorMessage, ((StartMarker)before).myLexemIndex));
      doneBefore(type, before);
    }

    public void error(String message) {
      myType = ElementType.ERROR_ELEMENT;
      PsiBuilderImpl.this.error(this, message);
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
    StartMarker pre = new StartMarker(marker.myLexemIndex);
    myProduction.add(idx, pre);
    return pre;
  }

  public class Token extends Node {
    private IElementType myTokenType;
    private int myTokenStart;
    private int myTokenEnd;
    private int myHC = -1;

    public Token() {
      myTokenType = myLexer.getTokenType();
      myTokenStart = myLexer.getTokenStart();
      myTokenEnd = myLexer.getTokenEnd();
    }

    public int hc() {
      if (myHC == -1) {
        int hc = 0;
        final int start = myTokenStart;
        final int end = myTokenEnd;
        final CharSequence buf = myLexer.getBufferSequence();
        for (int i = start; i < end; i++) {
          hc += buf.charAt(i);
        }

        myHC = hc;
      }

      return myHC;
    }

    public CharSequence getText() {
      return myLexer.getBufferSequence().subSequence(myTokenStart, myTokenEnd);
    }

    public IElementType getTokenType() {
      return myTokenType;
    }
  }

  private abstract static class ProductionMarker extends Node {
    public int myLexemIndex;

    public ProductionMarker(final int lexemIndex) {
      myLexemIndex = lexemIndex;
    }
  }

  private static class DoneMarker extends ProductionMarker {
    public final StartMarker myStart;

    public DoneMarker(final StartMarker marker, int currentLexem) {
      super(currentLexem);
      myStart = marker;
    }

    public int hc() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }

    public IElementType getTokenType() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }


    public CharSequence getText() {
      throw new UnsupportedOperationException("Shall not be called on this kind of markers");
    }
  }

  private static class DoneWithErrorMarker extends DoneMarker {
    public final String myMessage;

    public DoneWithErrorMarker(final StartMarker marker, int currentLexem, String message) {
      super(marker, currentLexem);
      myMessage = message;
    }
  }

  private static class ErrorItem extends ProductionMarker {
    String myMessage;

    public ErrorItem(final String message, int idx) {
      super(idx);
      myMessage = message;
    }

    public int hc() {
      return 0;
    }

    public CharSequence getText() {
      return "";
    }

    public IElementType getTokenType() {
      return ElementType.ERROR_ELEMENT;
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
      Token lastToken;
      while (true) {
        lastToken = getTokenOrWhitespace();
        if (lastToken == null) return null;
        if (whitespaceOrComment(lastToken.getTokenType())) {
          myCurrentLexem++;
        }
        else {
          break;
        }
      }
      myCurrentToken = lastToken;
    }

    return myCurrentToken;
  }

  @Nullable
  private Token getTokenOrWhitespace() {
    while (myCurrentLexem >= myLexems.size()) {
      if (myLexer.getTokenType() == null) return null;
      myLexems.add(new Token());
      myLexer.advance();
    }
    return myLexems.get(myCurrentLexem);
  }

  private boolean whitespaceOrComment(IElementType token) {
    return myWhitespaces.contains(token) || myComments.contains(token);
  }

  public Marker mark() {
    StartMarker marker = new StartMarker(myCurrentLexem);
    myProduction.add(marker);
    return marker;
  }

  public boolean eof() {
    if (myCurrentLexem + 1 < myLexems.size()) return false;
    return getCurrentToken() == null;
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

    DoneMarker doneMarker = new DoneMarker((StartMarker)marker, ((StartMarker)before).myLexemIndex);
    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(beforeIndex, doneMarker);
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void drop(Marker marker) {
    final boolean removed = myProduction.remove(myProduction.lastIndexOf(marker)) == marker;
    if (!removed) {
      LOG.error("The marker must be added before it is dropped.");
    }
  }

  public void error(Marker marker, String message) {
    doValidnessChecks(marker);

    DoneWithErrorMarker doneMarker = new DoneWithErrorMarker((StartMarker)marker, myCurrentLexem, message);
    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  public void done(Marker marker) {
    doValidnessChecks(marker);

    DoneMarker doneMarker = new DoneMarker((StartMarker)marker, myCurrentLexem);
    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "SuspiciousMethodCalls"})
  private void doValidnessChecks(final Marker marker) {
    /*
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
    */
  }

  public void error(String messageText) {
    if (myProduction.get(myProduction.size() - 1) instanceof ErrorItem) return;
    myProduction.add(new ErrorItem(messageText, myCurrentLexem));
  }

  public ASTNode getTreeBuilt() {
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

  private class MyBuilder implements DiffTreeChangeBuilder<ASTNode, Node> {
    private ASTDiffBuilder myDelegate;
    private ASTConvertor myConvertor;

    public MyBuilder(PsiFileImpl file, Node rootNode) {
      myDelegate = new ASTDiffBuilder(file);
      myConvertor = new ASTConvertor(rootNode);
    }

    public void nodeDeleted(final ASTNode oldParent, final ASTNode oldNode) {
      myDelegate.nodeDeleted(oldParent, oldNode);
    }

    public void nodeInserted(final ASTNode oldParent, final Node newNode, final int pos) {
      myDelegate.nodeInserted(oldParent, myConvertor.convert(newNode), pos);
    }

    public void nodeReplaced(final ASTNode oldChild, final Node newChild) {
      myDelegate.nodeReplaced(oldChild, myConvertor.convert(newChild));
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
          DiffTree.diff(new ASTDiffTreeStructure(oldNode), new MyTreeStructure(newNode), new MyComparator(), builder);
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
        while (item.myLexemIndex < myLexems.size() && myWhitespaces.contains(myLexems.get(item.myLexemIndex).getTokenType())) {
          item.myLexemIndex++;
        }
      }
      else if (item instanceof DoneMarker || item instanceof ErrorItem) {
        int prevProductionLexIndex = fProduction.get(i - 1).myLexemIndex;
        while (item.myLexemIndex > prevProductionLexIndex && item.myLexemIndex < myLexems.size() &&
               myWhitespaces.contains(myLexems.get(item.myLexemIndex - 1).getTokenType())) {
          item.myLexemIndex--;
        }
      }
    }

    StartMarker curNode = rootMarker;

    int curToken = 0;
    int lastErrorIndex = -1;

    Stack<StartMarker> nodes = new Stack<StartMarker>();
    nodes.push(rootMarker);

    for (int i = 1; i < fProduction.size(); i++) {
      ProductionMarker item = fProduction.get(i);

      if (curNode == null) LOG.error("Unexpected end of the production");

      int lexIndex = item.myLexemIndex;
      if (item instanceof StartMarker) {
        StartMarker marker = (StartMarker)item;
        curToken = insertLeafs(curToken, lexIndex, curNode);
        curNode.addChild(item);
        nodes.push(curNode);
        curNode = marker;
      }
      else if (item instanceof DoneMarker) {
        curToken = insertLeafs(curToken, lexIndex, curNode);
        curNode = nodes.pop();
      }
      else if (item instanceof ErrorItem) {
        curToken = insertLeafs(curToken, lexIndex, curNode);
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        curNode.addChild(item);
      }
    }

    final boolean allTokensInserted = curToken == myLexems.size();
    if (!allTokensInserted) {
      LOG.assertTrue(false, "Not all of the tokens inserted to the tree, parsed text:\n" + myText);
    }

    myLexems.add(new Token()); // $ terminating token.

    LOG.assertTrue(curNode == rootMarker, "Unbalanced tree");
    return rootMarker;
  }

  private void bind(CompositeElement ast, StartMarker marker) {
    Node child = marker.firstChild;
    while (child != null) {
      if (child instanceof StartMarker) {
        final StartMarker childMarker = (StartMarker)child;

        CompositeElement childNode = createComposite(childMarker);

        TreeUtil.addChildren(ast, childNode);
        bind(childNode, childMarker);
      }
      else if (child instanceof ErrorItem) {
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)child).myMessage);
        TreeUtil.addChildren(ast, errorElement);
      }
      else if (child instanceof Token) {
        TreeUtil.addChildren(ast, createLeaf((Token)child));
      }

      child = child.next;
    }
  }

  private CompositeElement createComposite(final StartMarker marker) {
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

  private class MyComparator implements ShallowNodeComparator<ASTNode, Node> {
    public ThreeState deepEqual(final ASTNode oldNode, final Node newNode) {
      return textMatches(oldNode, newNode);
    }

    private String getErrorMessage(Node node) {
      if (node instanceof ErrorItem) return ((ErrorItem)node).myMessage;
      if (node instanceof StartMarker) {
        final StartMarker marker = (StartMarker)node;
        if (marker.myType == ElementType.ERROR_ELEMENT && marker.myDoneMarker instanceof DoneWithErrorMarker) {
          return ((DoneWithErrorMarker)marker.myDoneMarker).myMessage;
        }
      }

      return null;
    }

    private ThreeState textMatches(final ASTNode oldNode, final Node newNode) {
      if (oldNode instanceof ChameleonElement || newNode.getTokenType() instanceof IChameleonElementType) {
        return ((TreeElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
      }

      if (oldNode.getElementType() instanceof IChameleonElementType && newNode.getTokenType() instanceof IChameleonElementType) {
        return ((TreeElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
      }

      if (oldNode instanceof LeafElement) {
        if (newNode instanceof Token) {
          return ((LeafElement)oldNode).textMatches(myLexer.getBufferSequence(), ((Token)newNode).myTokenStart, ((Token)newNode).myTokenEnd) ? ThreeState.YES : ThreeState.NO;
        }
        return ((LeafElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
      }

      if (oldNode instanceof PsiErrorElement && newNode.getTokenType() == ElementType.ERROR_ELEMENT) {
        final String m1 = ((PsiErrorElement)oldNode).getErrorDescription();
        final String m2 = getErrorMessage(newNode);
        if (!Comparing.equal(m1, m2)) return ThreeState.NO;
      }

      return ThreeState.UNSURE;
    }

    public boolean typesEqual(final ASTNode n1, final Node n2) {
      if (n1 instanceof PsiWhiteSpaceImpl) {
        return n2.getTokenType() == XmlTokenType.XML_REAL_WHITE_SPACE || myWhitespaces.contains(n2.getTokenType());
      }

      return n1.getElementType() == n2.getTokenType();
    }

    public boolean hashcodesEqual(final ASTNode n1, final Node n2) {
      if (n1 instanceof LeafElement && n2 instanceof Token) {
        return textMatches(n1, n2) == ThreeState.YES;
      }

      if (n1 instanceof PsiErrorElement && n2.getTokenType() == ElementType.ERROR_ELEMENT) {
        final PsiErrorElement e1 = ((PsiErrorElement)n1);
        if (!Comparing.equal(e1.getErrorDescription(), getErrorMessage(n2))) return false;
      }

      return ((TreeElement)n1).hc() == n2.hc();
    }
  }

  private class MyTreeStructure implements DiffTreeStructure<Node> {
    private List<Node> EMPTY = Collections.emptyList();
    private final StartMarker myRoot;

    public MyTreeStructure(final StartMarker root) {
      myRoot = root;
    }

    public Node prepareForGetChildren(final Node o) {
      return o;
    }

    public Node getRoot() {
      return myRoot;
    }

    public void getChildren(final Node item, final List<Node> into) {
      if (item instanceof Token || item instanceof ErrorItem) return;
      StartMarker marker = (StartMarker)item;

      Node child = marker.firstChild;
      while (child != null) {
        into.add(child);
        child = child.next;
      }
    }
  }

  private class ASTConvertor implements Convertor<Node, ASTNode> {
    private Node myRoot;

    public ASTConvertor(final Node root) {
      myRoot = root;
    }

    public ASTNode convert(final Node n) {
      if (n instanceof Token) {
        return createLeaf((Token)n);
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

  private int insertLeafs(int curToken, int lastIdx, final StartMarker curNode) {
    lastIdx = Math.min(lastIdx, myLexems.size());
    while (curToken < lastIdx) {
      Token lexem = myLexems.get(curToken++);
      if (lexem.myTokenStart < lexem.myTokenEnd) { // Empty token. Most probably a parser directive like indent/dedent in phyton
        curNode.addChild(lexem);
      }
    }

    return curToken;
  }

  @NotNull
  private LeafElement createLeaf(final Token lexem) {
    final IElementType type = lexem.getTokenType();
    if (myWhitespaces.contains(type)) {
      return new PsiWhiteSpaceImpl(myLexer.getBufferSequence(), lexem.myTokenStart, lexem.myTokenEnd, -1, myCharTable);
    }
    else if (myComments.contains(type)) {
      return new PsiCommentImpl(type, myLexer.getBufferSequence(), lexem.myTokenStart, lexem.myTokenEnd, -1, myCharTable);
    }
    else if (type instanceof IChameleonElementType) {
      return new ChameleonElement(type, myLexer.getBufferSequence(), lexem.myTokenStart, lexem.myTokenEnd, -1, myCharTable);
    }
    else if (type instanceof IXmlElementType || type instanceof IJspElementType && !(type instanceof IELElementType)) {
      return Factory.createLeafElement(type, myLexer.getBufferSequence(), lexem.myTokenStart, lexem.myTokenEnd, -1, myCharTable);
    }
    return new LeafPsiElement(type, myLexer.getBufferSequence(), lexem.myTokenStart, lexem.myTokenEnd, -1, myCharTable);
  }

  /**
   * just to make removeRange method available.
   */
  private static class MyList extends ArrayList<ProductionMarker> {
    public void removeRange(final int fromIndex, final int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }
  }
}
