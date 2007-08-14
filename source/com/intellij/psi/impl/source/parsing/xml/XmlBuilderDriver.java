/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.LighterASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

public class XmlBuilderDriver {
  private final Stack<String> myNamespacesStack = new Stack<String>();
  private final Stack<String> myPrefixesStack = new Stack<String>();
  private final CharSequence myText;
  @NonNls private static final String XMLNS = "xmlns";
  @NonNls private static final String XMLNS_COLON = "xmlns:";

  public XmlBuilderDriver(final CharSequence text) {
    myText = text;
  }

  protected CharSequence getText() {
    return myText;
  }

  public void addImplicitBinding(String prefix, String namespace) {
    myNamespacesStack.push(namespace);
    myPrefixesStack.push(prefix);
  }

  public void build(XmlBuilder builder) {
    PsiBuilderImpl b = createBuilderAndParse();

    FlyweightCapableTreeStructure<LighterASTNode> structure = b.getLightTree();

    LighterASTNode root = structure.getRoot();
    root = structure.prepareForGetChildren(root);

    final Ref<LighterASTNode[]> childrenRef = Ref.create(null);
    final int count = structure.getChildren(root, childrenRef);
    LighterASTNode[] children = childrenRef.get();

    for (int i = 0; i < count; i++) {
      LighterASTNode child = children[i];
      final IElementType tt = child.getTokenType();
      if (tt == XmlElementType.XML_TAG || tt == XmlElementType.HTML_TAG) {
        processTagNode(structure, child, builder);
      }
    }

    structure.disposeChildren(children, count);
  }

  protected PsiBuilderImpl createBuilderAndParse() {
    final ParserDefinition xmlParserDefinition = StdLanguages.XML.getParserDefinition();
    assert xmlParserDefinition != null;

    PsiBuilderImpl b = new PsiBuilderImpl(xmlParserDefinition.createLexer(null), xmlParserDefinition.getWhitespaceTokens(), TokenSet.EMPTY, null, myText);
    new XmlParsing(b).parseDocument();
    return b;
  }


  private void processTagNode(FlyweightCapableTreeStructure<LighterASTNode> structure, LighterASTNode node, XmlBuilder builder) {
    final IElementType nodeTT = node.getTokenType();
    assert nodeTT == XmlElementType.XML_TAG || nodeTT == XmlElementType.HTML_TAG;

    node = structure.prepareForGetChildren(node);

    final Ref<LighterASTNode[]> childrenRef = Ref.create(null);
    final int count = structure.getChildren(node, childrenRef);
    LighterASTNode[] children = childrenRef.get();

    int stackFrameSize = myNamespacesStack.size();
    CharSequence tagName = "";
    int headerEndOffset = node.getEndOffset();
    for (int i = 0; i < count; i++) {
      LighterASTNode child = children[i];
      final IElementType tt = child.getTokenType();
      if (tt == XmlElementType.XML_ATTRIBUTE) checkForXmlns(child, structure);
      if (tt == XmlTokenType.XML_TAG_END || tt == XmlTokenType.XML_EMPTY_ELEMENT_END) {
        headerEndOffset = child.getEndOffset();
        break;
      }
      if (tt == XmlTokenType.XML_NAME || tt == XmlTokenType.XML_TAG_NAME) {
        tagName = myText.subSequence(child.getStartOffset(), child.getEndOffset());
      }
    }

    CharSequence localName = getLocalName(tagName);
    String namespace = getNamespace(tagName);

    XmlBuilder.ProcessingOrder order = builder.startTag(localName, namespace, node.getStartOffset(), node.getEndOffset(), headerEndOffset);
    boolean processAttrs = order == XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES ||
                           order == XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES_AND_TEXTS;

    boolean processTexts = order == XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS ||
                           order == XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES_AND_TEXTS;

    for (int i = 0; i < count; i++) {
      LighterASTNode child = children[i];
      IElementType tt = child.getTokenType();
      if (tt == XmlElementType.XML_TAG || tt == XmlElementType.HTML_TAG) processTagNode(structure, child, builder);
      if (processAttrs && tt == XmlElementType.XML_ATTRIBUTE) processAttributeNode(child, structure, builder);
      if (processTexts && tt == XmlElementType.XML_TEXT) processTextNode(structure, child, builder);
      if (tt == XmlElementType.XML_ENTITY_REF) builder.entityRef(myText.subSequence(child.getStartOffset(), child.getEndOffset()), child.getStartOffset(), child.getEndOffset());
    }

    builder.endTag(localName, namespace, node.getStartOffset(), node.getEndOffset());

    int framesToDrop = myNamespacesStack.size() - stackFrameSize;
    for (int i = 0; i < framesToDrop; i++) {
      myNamespacesStack.pop();
      myPrefixesStack.pop();
    }

    structure.disposeChildren(children, count);
  }

  private void processTextNode(FlyweightCapableTreeStructure<LighterASTNode> structure, LighterASTNode node, XmlBuilder builder) {
    node = structure.prepareForGetChildren(node);

    final Ref<LighterASTNode[]> childrenRef = Ref.create(null);
    final int count = structure.getChildren(node, childrenRef);
    LighterASTNode[] children = childrenRef.get();

    for (int i = 0; i < count; i++) {
      LighterASTNode child = children[i];
      IElementType tt = child.getTokenType();
      final int start = child.getStartOffset();
      final int end = child.getEndOffset();
      final CharSequence physical = myText.subSequence(start, end);

      if (tt == XmlTokenType.XML_CDATA_START || tt == XmlTokenType.XML_CDATA_END) {
        builder.textElement("", physical, start, end);
      }
      else if (tt == XmlElementType.XML_CDATA) {
        processTextNode(structure, child, builder);
      }
      else if (tt == XmlTokenType.XML_CHAR_ENTITY_REF) {
        builder.textElement(new String(new char[] {XmlUtil.getCharFromEntityRef(physical.toString())}), physical, start, end);
      }
      else {
        builder.textElement(physical, physical, start, end);
      }
    }

    structure.disposeChildren(children, count);
  }

  private void processAttributeNode(final LighterASTNode attrNode, FlyweightCapableTreeStructure<LighterASTNode> structure, XmlBuilder builder) {
    builder.attribute(getAttributeName(attrNode, structure), getAttributeValue(attrNode, structure), attrNode.getStartOffset(), attrNode.getEndOffset());
  }

  private String getNamespace(final CharSequence tagName) {
    final String namespacePrefix;
    int pos = StringUtil.indexOf(tagName, ':');
    if (pos == -1) {
      namespacePrefix = "";
    }
    else {
      namespacePrefix = tagName.subSequence(0, pos).toString();
    }

    for (int i = myPrefixesStack.size() - 1; i >= 0; i--) {
      if (namespacePrefix.equals(myPrefixesStack.get(i))) return myNamespacesStack.get(i);
    }

    return "";
  }

  private static CharSequence getLocalName(final CharSequence tagName) {
    int pos = StringUtil.indexOf(tagName, ':');
    if (pos == -1) {
      return tagName;
    }
    return tagName.subSequence(pos + 1, tagName.length());
  }

  private void checkForXmlns(LighterASTNode attrNode, FlyweightCapableTreeStructure<LighterASTNode> structure) {
    final CharSequence name = getAttributeName(attrNode, structure);
    if (Comparing.equal(name, XMLNS)) {
      myPrefixesStack.push("");
      myNamespacesStack.push(getAttributeValue(attrNode, structure).toString());
    }
    else if (StringUtil.startsWith(name, XMLNS_COLON)) {
      myPrefixesStack.push(name.subSequence(XMLNS_COLON.length(), name.length()).toString());
      myNamespacesStack.push(getAttributeValue(attrNode, structure).toString());
    }
  }


  private CharSequence getAttributeName(LighterASTNode attrNode, FlyweightCapableTreeStructure<LighterASTNode> structure) {
    return findTextByTokenType(attrNode, structure, XmlTokenType.XML_NAME);
  }

  private CharSequence getAttributeValue(LighterASTNode attrNode, FlyweightCapableTreeStructure<LighterASTNode> structure) {
    final CharSequence fullValue = findTextByTokenType(attrNode, structure, XmlElementType.XML_ATTRIBUTE_VALUE);
    int start = 0;
    if (fullValue.length() > 0 && fullValue.charAt(0) == '\"') start++;

    int end = fullValue.length();
    if (fullValue.length() > start && fullValue.charAt(fullValue.length() - 1) == '\"') end--;

    return fullValue.subSequence(start, end);
  }

  private CharSequence findTextByTokenType(LighterASTNode attrNode,
                                           FlyweightCapableTreeStructure<LighterASTNode> structure,
                                           IElementType tt) {
    attrNode = structure.prepareForGetChildren(attrNode);

    final Ref<LighterASTNode[]> childrenRef = Ref.create(null);
    final int count = structure.getChildren(attrNode, childrenRef);
    LighterASTNode[] children = childrenRef.get();

    CharSequence name = "";
    for (int i = 0; i < count; i++) {
      LighterASTNode child = children[i];
      if (child.getTokenType() == tt) {
        name = myText.subSequence(child.getStartOffset(), child.getEndOffset());
        break;
      }
    }

    structure.disposeChildren(children, count);

    return name;
  }

}