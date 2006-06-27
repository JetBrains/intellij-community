package com.intellij.psi.formatter.xml;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspDeclaration;
import com.intellij.psi.impl.source.jsp.jspJava.JspScriptlet;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractXmlBlock extends AbstractBlock {
  protected final XmlFormattingPolicy myXmlFormattingPolicy;
  public static final @NonNls String JSPX_DECLARATION_TAG_NAME = "jsp:declaration";
  public static final @NonNls String JSPX_SCRIPTLET_TAG_NAME = "jsp:scriptlet";
  private static final @NonNls String JSP_TAG_PREFIX = "jsp:";

  public AbstractXmlBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final XmlFormattingPolicy policy) {
    super(node, wrap, alignment);
    myXmlFormattingPolicy = policy;
    if (node == null) {
      LOG.assertTrue(false);
    }
    if (node.getTreeParent() == null) {
      myXmlFormattingPolicy.setRootBlock(node, this);
    }
  }


  protected WrapType getWrapType(final int type) {
    if (type == CodeStyleSettings.DO_NOT_WRAP) return WrapType.NONE;
    if (type == CodeStyleSettings.WRAP_ALWAYS) return WrapType.ALWAYS;
    if (type == CodeStyleSettings.WRAP_AS_NEEDED) return WrapType.NORMAL;
    return WrapType.CHOP_DOWN_IF_LONG;
  }

  protected Alignment chooseAlignment(final ASTNode child, final Alignment attrAlignment, final Alignment textAlignment) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE && myXmlFormattingPolicy.getShouldAlignAttributes()) return attrAlignment;
    if (elementType == ElementType.XML_TEXT && myXmlFormattingPolicy.getShouldAlignText()) return textAlignment;
    return null;
  }

  private Wrap getTagEndWrapping(final XmlTag parent) {
    return Wrap.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagEnd(parent), true);
  }

  protected Wrap chooseWrap(final ASTNode child, final Wrap tagBeginWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return textWrap;
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE) return attrWrap;
    if (elementType == ElementType.XML_START_TAG_START) return tagBeginWrap;
    if (elementType == ElementType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if (parent instanceof XmlTag) {
        final XmlTag tag = (XmlTag)parent;
        if (canWrapTagEnd(tag)) {
          return getTagEndWrapping(tag);
        }
      }
      return null;
    }
    if (elementType == ElementType.XML_TEXT || elementType == ElementType.XML_DATA_CHARACTERS) return textWrap;
    return null;
  }

  private boolean canWrapTagEnd(final XmlTag tag) {
    final String name = tag.getName();
    return tag.getSubTags().length > 0 || (name.toLowerCase().startsWith(JSP_TAG_PREFIX));
  }

  protected XmlTag getTag() {
    return getTag(myNode);
  }

  protected XmlTag getTag(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof XmlTag) {
      return (XmlTag)element;
    }
    else {
      return null;
    }
  }

  protected Wrap createTagBeginWrapping(final XmlTag tag) {
    return Wrap.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagBegin(tag), true);
  }

  protected
  @Nullable
  ASTNode processChild(List<Block> result,
                       final ASTNode child,
                       final Wrap wrap,
                       final Alignment alignment,
                       final Indent indent) {
    final Language myLanguage = myNode.getPsi().getLanguage();
    final Language childLanguage = child.getPsi().getLanguage();
    if (useMyFormatter(myLanguage, childLanguage)) {

      if (canBeAnotherTreeTagStart(child)) {
        XmlTag tag = JspTextBlock.findXmlTagAt(child, child.getStartOffset());
        if (tag != null
            && containsTag(tag)
            && doesNotIntersectSubTagsWith(tag)) {
          ASTNode currentChild = createAnotherTreeTagBlock(result, child, tag, indent, wrap, alignment);

          if (currentChild == null) {
            return null;
          }

          while (currentChild != null && currentChild.getTreeParent() != myNode && currentChild.getTreeParent() != child.getTreeParent()) {
            currentChild = processAllChildrenFrom(result, currentChild, wrap, alignment, indent);
            if (currentChild != null && (currentChild.getTreeParent() == myNode || currentChild.getTreeParent() == child.getTreeParent())) {
              return currentChild;
            }
            if (currentChild != null) {
              currentChild = currentChild.getTreeParent();

            }
          }

          return currentChild;
        }
      }

      processSimpleChild(child, indent, result, wrap, alignment);
      return child;

    }
    else {
      return createAnotherLanguageBlockWrapper(childLanguage, child, result, indent);
    }
  }

  private boolean doesNotIntersectSubTagsWith(final PsiElement tag) {
    final TextRange tagRange = tag.getTextRange();
    final XmlTag[] subTags = getSubTags();
    for (XmlTag subTag : subTags) {
      final TextRange subTagRange = subTag.getTextRange();
      if (subTagRange.getEndOffset() < tagRange.getStartOffset()) continue;
      if (subTagRange.getStartOffset() > tagRange.getEndOffset()) return true;

      if (tagRange.getStartOffset() > subTagRange.getStartOffset() && tagRange.getEndOffset() < subTagRange.getEndOffset()) return false;
      if (tagRange.getEndOffset() > subTagRange.getStartOffset() && tagRange.getEndOffset() < subTagRange.getEndOffset()) return false;

    }
    return true;
  }

  private XmlTag[] getSubTags() {

    if (myNode instanceof XmlTag) {
      return ((XmlTag)myNode.getPsi()).getSubTags();
    }
    else if (myNode.getPsi() instanceof XmlElement) {
      return collectSubTags((XmlElement)myNode.getPsi());
    }
    else {
      return new XmlTag[0];
    }

  }

  private XmlTag[] collectSubTags(final XmlElement node) {
    final List<XmlTag> result = new ArrayList<XmlTag>();
    node.processElements(new PsiElementProcessor() {
      public boolean execute(final PsiElement element) {
        if (element instanceof XmlTag) {
          result.add((XmlTag)element);
        }
        return true;
      }
    }, node);
    return result.toArray(new XmlTag[result.size()]);
  }

  private boolean containsTag(final PsiElement tag) {
    final ASTNode closingTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(myNode);
    final ASTNode startTagStart = XmlChildRole.START_TAG_END_FINDER.findChild(myNode);

    if (closingTagStart == null && startTagStart == null) {
      return tag.getTextRange().getEndOffset() <= myNode.getTextRange().getEndOffset();
    }
    else if (closingTagStart == null) {
      return false;
    }
    else {
      return tag.getTextRange().getEndOffset() <= closingTagStart.getTextRange().getEndOffset();
    }
  }

  private ASTNode processAllChildrenFrom(final List<Block> result,
                                         final @NotNull ASTNode child,
                                         final Wrap wrap,
                                         final Alignment alignment,
                                         final Indent indent) {
    ASTNode resultNode = child;
    ASTNode currentChild = child.getTreeNext();
    while (currentChild != null && currentChild.getElementType() != ElementType.XML_END_TAG_START) {
      if (!FormatterUtil.containsWhiteSpacesOnly(currentChild)) {
        currentChild = processChild(result, currentChild, wrap, alignment, indent);
        resultNode = currentChild;
      }
      if (currentChild != null) {
        currentChild = currentChild.getTreeNext();
      }
    }
    return resultNode;
  }

  private void processSimpleChild(final ASTNode child,
                                  final Indent indent,
                                  final List<Block> result,
                                  final Wrap wrap,
                                  final Alignment alignment) {
    if (myXmlFormattingPolicy.processJsp() &&
        (child.getElementType() == ElementType.JSP_XML_TEXT
         || child.getPsi() instanceof OuterLanguageElement)) {
      final Pair<PsiElement, Language> root = JspTextBlock.findPsiRootAt(child, myXmlFormattingPolicy.processJavaTree());
      if (root != null) {
        createJspTextNode(result, child, indent);
        return;
      }
    }

    if (isXmlTag(child)) {
      result.add(new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy, indent != null ? indent : Indent.getNoneIndent()));
    }
    else {
      result.add(new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, indent, null));
    }
  }

  private ASTNode createAnotherLanguageBlockWrapper(final Language childLanguage,
                                                    final ASTNode child,
                                                    final List<Block> result,
                                                    final Indent indent) {
    final FormattingModelBuilder builder = childLanguage.getFormattingModelBuilder();
    LOG.assertTrue(builder != null);
    final FormattingModel childModel = builder.createModel(child.getPsi(), getSettings());
    result.add(new AnotherLanguageBlockWrapper(child,
                                               myXmlFormattingPolicy,
                                               childModel.getRootBlock(),
                                               indent));
    return child;
  }

  private ASTNode createAnotherTreeTagBlock(final List<Block> result,
                                            final ASTNode child,
                                            PsiElement tag,
                                            final Indent indent,
                                            final Wrap wrap, final Alignment alignment) {
    Indent childIndent = indent;

    if (myNode.getElementType() == ElementType.HTML_DOCUMENT
        && tag.getParent() instanceof XmlTag
        && myXmlFormattingPolicy.indentChildrenOf((XmlTag)tag.getParent())) {
      childIndent = Indent.getNormalIndent();
    }
    result.add(createAnotherTreeTagBlock(tag, childIndent));
    ASTNode currentChild = findChildAfter(child, tag.getTextRange().getEndOffset());

    while (currentChild != null && currentChild.getTextRange().getEndOffset() > tag.getTextRange().getEndOffset()) {
      PsiElement psiElement = JspTextBlock.findXmlTagAt(currentChild, tag.getTextRange().getEndOffset());
      if (psiElement != null) {
        if (psiElement instanceof XmlTag &&
            psiElement.getTextRange().getStartOffset() >= currentChild.getTextRange().getStartOffset() &&
            containsTag(psiElement) && doesNotIntersectSubTagsWith(psiElement)) {
          result.add(createAnotherTreeTagBlock(psiElement, childIndent));
          currentChild = findChildAfter(currentChild, psiElement.getTextRange().getEndOffset());
          tag = psiElement;
        }
        else {
          result
            .add(new XmlBlock(currentChild, wrap, alignment, myXmlFormattingPolicy, indent, new TextRange(tag.getTextRange().getEndOffset(),
                                                                                                          currentChild
                                                                                                            .getTextRange().getEndOffset())));
          return currentChild;
        }
      }
      else {
        result
          .add(new XmlBlock(currentChild, wrap, alignment, myXmlFormattingPolicy, indent, new TextRange(tag.getTextRange().getEndOffset(),
                                                                                                        currentChild
                                                                                                          .getTextRange().getEndOffset())));
        return currentChild;
      }
    }

    return currentChild;
  }

  private Block createAnotherTreeTagBlock(final PsiElement tag, final Indent childIndent) {
    if (isXmlTag(tag)) {
      return new XmlTagBlock(tag.getNode(), null, null, createPolicyFor(), childIndent);
    }
    else {
      return new XmlBlock(tag.getNode(), null, null, createPolicyFor(), childIndent, tag.getTextRange());
    }

  }

  private XmlFormattingPolicy createPolicyFor() {
    return myXmlFormattingPolicy;
  }

  private CodeStyleSettings getSettings() {
    return myXmlFormattingPolicy.getSettings();
  }

private boolean canBeAnotherTreeTagStart(final ASTNode child) {
    return myXmlFormattingPolicy.processJsp()
           && PsiUtil.getJspFile(myNode.getPsi()) != null
           && (isXmlTag(myNode) || myNode.getElementType() == ElementType.HTML_DOCUMENT || myNode.getPsi() instanceof PsiFile) &&
           (child.getElementType() == ElementType.XML_DATA_CHARACTERS || child.getElementType() == ElementType.JSP_XML_TEXT ||
            child.getPsi() instanceof OuterLanguageElement);

  }
  protected boolean isXmlTag(final ASTNode child) {
    return isXmlTag(child.getPsi());
  }

  protected boolean isXmlTag(final PsiElement psi) {
    return psi instanceof XmlTag && !(psi instanceof JspScriptlet) && !(psi instanceof JspDeclaration);
  }

  private boolean useMyFormatter(final Language myLanguage, final Language childLanguage) {
    return myLanguage == childLanguage
           || childLanguage == StdLanguages.JAVA
           || childLanguage == StdLanguages.HTML
           || childLanguage == StdLanguages.XML
           || childLanguage == StdLanguages.JSP
           || childLanguage == StdLanguages.JSPX
           || childLanguage.getFormattingModelBuilder() == null;
  }

  protected boolean isJspxJavaContainingNode(final ASTNode child) {
    if (child.getElementType() != ElementType.XML_TEXT) return false;
    final ASTNode treeParent = child.getTreeParent();
    if (treeParent == null) return false;
    if (treeParent.getElementType() != ElementType.XML_TAG) return false;
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(treeParent);
    final String name = ((XmlTag)psiElement).getName();
    if (!(Comparing.equal(name, JSPX_SCRIPTLET_TAG_NAME)
          || Comparing.equal(name, JSPX_DECLARATION_TAG_NAME))) {
      return false;
    }
    if (child.getText().trim().length() == 0) return false;
    return JspTextBlock.findPsiRootAt(child, myXmlFormattingPolicy.processJavaTree()) != null;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public abstract boolean insertLineBreakBeforeTag();

  public abstract boolean removeLineBreakBeforeTag();

  protected Spacing createDefaultSpace(boolean forceKeepLineBreaks, final boolean inText) {
    boolean shouldKeepLineBreaks = getShouldKeepLineBreaks(inText, forceKeepLineBreaks);
    return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, shouldKeepLineBreaks, myXmlFormattingPolicy.getKeepBlankLines());
  }

  private boolean getShouldKeepLineBreaks(final boolean inText, final boolean forceKeepLineBreaks) {
    if (forceKeepLineBreaks) {
      return true;
    }
    if (inText && myXmlFormattingPolicy.getShouldKeepLineBreaksInText()) {
      return true;
    }
    if (!inText && myXmlFormattingPolicy.getShouldKeepLineBreaks()) {
      return true;
    }
    return false;
  }

  public abstract boolean isTextElement();

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.xml.AbstractXmlBlock");

  public static Block createJspRoot(final PsiElement element,
                                    final CodeStyleSettings settings,
                                    final FormattingDocumentModel documentModel) {
    final PsiFile file = element.getContainingFile();
    final Language baseLanguage = (PsiUtil.getJspFile(file)).getViewProvider().getTemplateDataLanguage();
    if (baseLanguage == StdLanguages.HTML || baseLanguage == StdLanguages.XHTML) {
      final PsiElement[] psiRoots = file.getPsiRoots();
      LOG.assertTrue(psiRoots.length == 4);
      final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(psiRoots[0]);
      return new XmlBlock(rootNode, null, null, new HtmlPolicy(settings, documentModel), null, null);
    }
    else if (baseLanguage == StdLanguages.XML) {
      final PsiElement[] psiRoots = file.getPsiRoots();
      LOG.assertTrue(psiRoots.length == 4);
      final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(psiRoots[0]);
      return new XmlBlock(rootNode, null, null, new XmlPolicy(settings, documentModel), null, null);
    }
    else {
      return new ReadOnlyBlock(file.getNode());
    }
  }

  public static Block creareJspxRoot(final PsiElement element,
                                     final CodeStyleSettings settings,
                                     final FormattingDocumentModel documentModel) {
    final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(element);
    return new XmlBlock(rootNode, null, null, new HtmlPolicy(settings, documentModel), null, null);
  }

  @Nullable
  protected void createJspTextNode(final List<Block> localResult, final ASTNode child, final Indent indent) {

    localResult.add(new JspTextBlock(child,
                                     myXmlFormattingPolicy,
                                     JspTextBlock.findPsiRootAt(child, myXmlFormattingPolicy.processJavaTree()),
                                     indent
    ));
  }

  private static ASTNode findChildAfter(@NotNull final ASTNode child, final int endOffset) {
    TreeElement fileNode = TreeUtil.getFileElement((TreeElement)child);
    final LeafElement leaf = fileNode.findLeafElementAt(endOffset);
    if (leaf != null && leaf.getStartOffset() == endOffset) {
      final ASTNode prev = TreeUtil.prevLeaf(leaf);
      if (prev != null) return prev;
    }
    return leaf;
    /*
    ASTNode result = child;
    while (result != null && result.getTextRange().getEndOffset() < endOffset) {
      result = TreeUtil.nextLeaf(result);
    }
    return result;
    */
  }

}
