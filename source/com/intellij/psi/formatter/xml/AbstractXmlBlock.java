package com.intellij.psi.formatter.xml;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspText;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;


public abstract class AbstractXmlBlock extends AbstractBlock {
  protected final XmlFormattingPolicy myXmlFormattingPolicy;
  public static final String JSPX_DECLARATION_TAG_NAME = "jsp:declaration";
  public static final String JSPX_SCRIPTLET_TAG_NAME = "jsp:scriptlet";

  public AbstractXmlBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final XmlFormattingPolicy policy) {
    super(node, wrap, alignment);
    myXmlFormattingPolicy = policy;
    if (node.getTreeParent() == null) {
      myXmlFormattingPolicy.setRootBlock(node, this);
    }
  }


  protected int getWrapType(final int type) {
    if (type == CodeStyleSettings.DO_NOT_WRAP) return Wrap.NONE;
    if (type == CodeStyleSettings.WRAP_ALWAYS) return Wrap.ALWAYS;
    if (type == CodeStyleSettings.WRAP_AS_NEEDED) return Wrap.NORMAL;
    return Wrap.CHOP_DOWN_IF_LONG;
  }

  protected Alignment chooseAlignment(final ASTNode child, final Alignment attrAlignment, final Alignment textAlignment) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE && myXmlFormattingPolicy.getShouldAlignAttributes()) return attrAlignment;
    if (elementType == ElementType.XML_TEXT && myXmlFormattingPolicy.getShouldAlignText()) return textAlignment;
    return null;
  }

  private Wrap getTagEndWrapping(final XmlTag parent) {
    return getFormatter().createWrap(myXmlFormattingPolicy.getWrappingTypeForTagEnd(parent), true);
  }

  protected Wrap chooseWrap(final ASTNode child, final Wrap tagBeginWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return textWrap;
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE) return attrWrap;
    if (elementType == ElementType.XML_START_TAG_START) return tagBeginWrap;
    if (elementType == ElementType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if ((parent instanceof XmlTag) && canWrapTagEnd((XmlTag)parent)) {
        return getTagEndWrapping((XmlTag)parent);
      } else {
        return null;
      }
    }
    if (elementType == ElementType.XML_TEXT || elementType == ElementType.XML_DATA_CHARACTERS) return textWrap;
    return null;
  }

  private boolean canWrapTagEnd(final XmlTag tag) {
    return tag.getSubTags().length > 0 || tag.getName().toLowerCase().startsWith("jsp:");
  }

  protected Formatter getFormatter() {
    return Formatter.getInstance();
  }

  protected IElementType getTagType() {
    return myXmlFormattingPolicy.getTagType();
  }

  protected XmlTag getTag() {
    return getTag(myNode);
  }

  protected XmlTag getTag(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof XmlTag) {
      return (XmlTag)element;
    } else {
      return null;
    }
  }

  protected Wrap createTagBeginWrapping(final XmlTag tag) {
    return getFormatter().createWrap(myXmlFormattingPolicy.getWrappingTypeForTagBegin(tag), true);
  }

  protected @Nullable ASTNode processChild(List<Block> result, final ASTNode child, final Wrap wrap, final Alignment alignment, final Indent indent) {
    final Language myLanguage = myNode.getPsi().getLanguage();
    final Language childLanguage = child.getPsi().getLanguage();
    if (useMyFormatter(myLanguage, childLanguage)) {
      Block jspScriptletNode = buildBlockForScriptletNode(child,indent);
      if (jspScriptletNode != null) {
        result.add(jspScriptletNode);
        return child;
      }
      if (myXmlFormattingPolicy.processJsp() && (child.getElementType() == ElementType.JSP_XML_TEXT || child.getPsi() instanceof JspText)) {
        final Pair<PsiElement,Language> root = JspTextBlock.findPsiRootAt(child);
        if (root != null) {
          return createJspTextNode(result, child, indent);
        }
      }
      if (child.getElementType() == getTagType() || child.getElementType() == ElementType.XML_TAG) {
        result.add(new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy, indent != null ? indent : getFormatter().getNoneIndent()));
        return child;
      }
      else if (child.getElementType() == ElementType.JSP_SCRIPTLET_END) {
        result.add(new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, getFormatter().getNoneIndent()));
        return child;
      }
      else {
        result.add(new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, indent));
        return child;
      }
    } else {
      final FormattingModelBuilder builder = childLanguage.getFormattingModelBuilder();
      LOG.assertTrue(builder != null);
      final FormattingModel childModel = builder.createModel(child.getPsi(),
                                                             myXmlFormattingPolicy.getSettings());
      result.add(new AnotherLanguageBlockWrapper(child, myXmlFormattingPolicy, childModel.getRootBlock()));
      return child;
    }
  }

  private Block buildBlockForScriptletNode(final ASTNode child, final Indent indent) {
    if (!(child.getPsi() instanceof JspText)) return null;
    ASTNode element = child.getPsi().getContainingFile()
      .getPsiRoots()[0].getNode().findLeafElementAt(child.getTextRange().getStartOffset());
    if (element != null && (element.getElementType() == ElementType.JSP_SCRIPTLET_START
                            || element.getElementType() == ElementType.JSP_DECLARATION_START
                            || element.getElementType() == ElementType.JSP_EXPRESSION_START)) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      while (element != null && element.getTextRange().getEndOffset() <=child.getTextRange().getEndOffset()) {
        if (!FormatterUtil.containsWhiteSpacesOnly(element)) {
          processChild(subBlocks, element, null, null, Formatter.getInstance().getNoneIndent());
        }
        int nextOffset = element.getTextRange().getEndOffset();
        element = element.getTreeNext();
        if (element == null) {
          element = child.getPsi().getContainingFile()
            .getPsiRoots()[0].getNode().findLeafElementAt(nextOffset);
        }
      }
      return new SyntheticBlock(subBlocks, this, indent, myXmlFormattingPolicy, getFormatter().createNormalIndent());
    } else {
      return null;
    }
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
          || Comparing.equal(name, JSPX_DECLARATION_TAG_NAME))){
      return false;
    }
    if (child.getText().trim().length() == 0) return false;
    return JspTextBlock.findPsiRootAt(child) != null;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public abstract boolean insertLineBreakBeforeTag();

  public abstract boolean removeLineBreakBeforeTag();

  protected SpaceProperty createDefaultSpace(boolean forceKeepLineBreaks) {
    boolean shouldKeepLineBreaks = myXmlFormattingPolicy.getShouldKeepLineBreaks() || forceKeepLineBreaks;
    return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, shouldKeepLineBreaks, myXmlFormattingPolicy.getKeepBlankLines());
  }

  public abstract boolean isTextElement();

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.xml.AbstractXmlBlock");

  public static Block creareJspRoot(final PsiElement element, final CodeStyleSettings settings) {
    final PsiElement[] psiRoots = (element.getContainingFile()).getPsiRoots();
    LOG.assertTrue(psiRoots.length == 4);
    final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(psiRoots[1]);
    return new XmlBlock(rootNode, null, null, new HtmlPolicy(settings, ElementType.HTML_TAG), null);
  }

  public static Block creareJspxRoot(final PsiElement element, final CodeStyleSettings settings) {
    final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(element);
    return new XmlBlock(rootNode, null, null, new HtmlPolicy(settings, ElementType.XML_TAG), null);
  }

  @Nullable
  protected ASTNode createJspTextNode(final List<Block> localResult, final ASTNode child, final Indent indent) {
    final Pair<PsiElement, Language> psiRoot = JspTextBlock.findPsiRootAt(child);

    final ASTNode correspondingNode = psiRoot.getFirst().getNode().findLeafElementAt(child.getStartOffset());
    ASTNode topParentWithTheSameOffset = findParentWithTheSameOffset(correspondingNode);

    final TextRange elementRange = topParentWithTheSameOffset.getTextRange();
    if (canInsertWholeBlock(psiRoot, elementRange) && child.getTextRange().getEndOffset() < elementRange.getEndOffset()) {
      localResult.add(new JspTextBlock(topParentWithTheSameOffset,
                                       myXmlFormattingPolicy,
                                       psiRoot, indent));
      return findChildAfter(child, elementRange.getEndOffset());
    }

    localResult.add(new JspTextBlock(child,
                                     myXmlFormattingPolicy,
                                     psiRoot,
                                     indent));
    return child;
  }

  private ASTNode findChildAfter(@NotNull final ASTNode child, final int endOffset) {
    ASTNode result = child;
    while (result != null && result.getStartOffset() < endOffset) {
      result = result.getTreeNext();
    }
    if (result != null) {
      return result.getTreePrev();
    }
    final ASTNode parent = child.getTreeParent();
    if (parent != myNode) {
      return findChildAfter(parent, endOffset);
    } else {
      return null;
    }
  }

  private ASTNode findParentWithTheSameOffset(final ASTNode correspondingNode) {
    int offset =correspondingNode.getTextRange().getStartOffset();
    ASTNode result = correspondingNode;
    while (result.getTreeParent() != null && result.getTreeParent().getTextRange().getStartOffset() == offset) {
      result = result.getTreeParent();
    }
    return result;
  }

  private boolean canInsertWholeBlock(final Pair<PsiElement, Language> psiRoot, final TextRange elementRange) {
    if (psiRoot.getFirst().getLanguage() != StdLanguages.JSP) return false;
    if (elementRange.equals(psiRoot.getFirst().getTextRange())) return false;
    return  elementRange.getEndOffset() <= myNode.getTextRange().getEndOffset();
  }
}
