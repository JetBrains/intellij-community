package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspDirectiveKind;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class OuterLanguageElement extends LeafElement implements PsiElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement");
  private JspxFileViewProvider myViewProvider;
  private TextRange myRange;
  private XmlText myFollowingText;


  public OuterLanguageElement(IElementType type, JspxFileViewProvider viewProvider, TextRange range, XmlText followingText) {
    super(type);
    myViewProvider = viewProvider;
    myRange = range;
    myFollowingText = followingText;
  }

  public OuterLanguageElement(JspxFileViewProvider viewProvider, TextRange range, XmlText followingText) {
    this(JspElementType.HOLDER_TEMPLATE_DATA, viewProvider, range, followingText);
  }


  public void setRange(final TextRange range) {
    myRange = range;
    clearCaches();
    final CompositeElement element = getTreeParent();
    if(element != null) element.subtreeChanged();
  }

  public XmlText getFollowingText() {
    return myFollowingText;
  }

  private XmlTag[] myIncludes = null;

  public void clearCaches() {
    super.clearCaches();
    myIncludes = null;
  }

  public XmlTag[] getIncludeDirectivesInScope() {
    if(myIncludes != null) return myIncludes;
    final TextRange textRange = getTextRange();
    final FileViewProvider viewProvider = getContainingFile().getViewProvider();
    final JspFile jspFile = PsiUtil.getJspFile(viewProvider.getPsi(viewProvider.getBaseLanguage()));
    final XmlTag[] directiveTags = jspFile.getDirectiveTags(JspDirectiveKind.INCLUDE, false);
    final Set<XmlTag> includeDirectives = new HashSet<XmlTag>();
    for (final XmlTag directiveTag : directiveTags) {
      if (directiveTag.getNode().getStartOffset() < textRange.getStartOffset()) continue;
      if (directiveTag.getNode().getStartOffset() >= textRange.getEndOffset()) break;
      includeDirectives.add(directiveTag);
    }
    return myIncludes = includeDirectives.toArray(XmlTag.EMPTY);
  }

  public void setFollowingText(final XmlText followingText) {
    myFollowingText = followingText;
  }

  // Leaf functions start. Delegates most of functions to view provider
  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getFirstChild() {
    return null;
  }

  public PsiElement getLastChild() {
    return null;
  }

  public void acceptChildren(PsiElementVisitor visitor) {
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(this);
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(this);
  }

  public PsiFile getContainingFile() {
    if (!isValid()) throw new PsiInvalidElementAccessException(this);
    return SharedImplUtil.getContainingFile(this);
  }

  public PsiElement findElementAt(int offset) {
    return this;
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public PsiElement copy() {
    ASTNode elementCopy = copyElement();
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public boolean isValid() {
    return SharedImplUtil.isValid(this);
  }

  public boolean isWritable() {
    return SharedImplUtil.isWritable(this);
  }

  public PsiReference getReference() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    getTreeParent().removeChild(this);
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    getTreeParent().replaceChild(this, elementCopy);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    final PsiElement result = SourceTreeToPsiMap.treeElementToPsi(elementCopy);
    return result;
  }

  public String toString() {
    return "JspText";
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    return ResolveUtil.getContext(this);
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public boolean isPhysical() {
    PsiFile file = getContainingFile();
    if (file == null) return false;
    return file.isPhysical();
  }

  public GlobalSearchScope getResolveScope() {
    return ((PsiManagerImpl)getManager()).getFileManager().getResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return getManager().getSearchHelper().getUseScope(this);
  }

  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  @NotNull
  public Language getLanguage() {
    return getElementType().getLanguage();
  }

  public ASTNode getNode() {
    return this;
  }

  public PsiElement getPsi() {
    return this;
  }


  public char charAt(int position) {
    return myViewProvider.getContents().charAt(position + myRange.getStartOffset());
  }


  public int searchWord(int startOffset, StringSearcher searcher) {
    return searcher.scan(getInternedText());
  }

  public CharSequence getInternedText() {
    return myViewProvider.getContents().subSequence(myRange.getStartOffset(), myRange.getEndOffset());
  }


  public int copyTo(char[] buffer, int start) {
    final CharSequence internedText = getInternedText();
    for(int i = 0; i < internedText.length(); i++){
      buffer[start + i] = internedText.charAt(i);
    }
    return start + internedText.length();
  }

  public int textMatches(CharSequence buffer, int start) {
    final CharSequence internedText = getInternedText();
    final int length = internedText.length();
    if(buffer.length() - start < length) return -1;
    for(int i = 0; i < length; i++){
      if(internedText.charAt(i) != buffer.charAt(i + start)) return -1;
    }
    return start + length;
  }

  public void setInternedText(CharSequence id) {
    //throw new RuntimeException("Write operations are not allowed for outer language elements.");
  }

  public void setText(String text) {

  }

  public int getTextLength() {
    return myRange.getLength();
  }

  @NotNull
  public char[] textToCharArray() {
    return CharArrayUtil.fromSequence(getInternedText());
  }

  public boolean textContains(char c) {
    final CharSequence internedText = getInternedText();
    for(int i = 0; i < internedText.length(); i++){
      if(internedText.charAt(i) == c) return true;
    }
    return false;
  }

  public TextRange getTextRange() {
    return myRange;
  }
}
