// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

public class XmlTagBlock extends AbstractXmlBlock {
  private final @Nullable Indent myIndent;

  public XmlTagBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull XmlFormattingPolicy policy,
                     @Nullable Indent indent) {
    super(node, wrap, alignment, policy, false);
    myIndent = indent;
  }

  public XmlTagBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull XmlFormattingPolicy policy,
                     @Nullable Indent indent,
                     final boolean preserveSpace) {
    super(node, wrap, alignment, policy, preserveSpace);
    myIndent = indent;
  }

  @Override
  protected List<Block> buildChildren() {
    ASTNode child = myNode.getFirstChildNode();
    final Wrap attrWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getAttributesWrap()), false);
    final Wrap textWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getTextWrap(getTag())), true);
    final Wrap tagBeginWrap = createTagBeginWrapping(getTag());
    final Alignment attrAlignment = Alignment.createAlignment();
    final Alignment textAlignment = Alignment.createAlignment();
    final ArrayList<Block> result = new ArrayList<>(3);
    ArrayList<Block> localResult = new ArrayList<>(1);

    boolean insideTag = true;

    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {

        Wrap wrap = chooseWrap(child, tagBeginWrap, attrWrap, textWrap);
        Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);

        if (child.getElementType() == XmlTokenType.XML_TAG_END) {
          child = processChild(localResult, child, wrap, alignment, myXmlFormattingPolicy.getTagEndIndent());
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<>(1);
          insideTag = true;
        }
        else if (child.getElementType() == XmlTokenType.XML_START_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
          }
          localResult = new ArrayList<>(1);
          child = processChild(localResult, child, wrap, alignment, null);
        }
        else if (child.getElementType() == XmlTokenType.XML_END_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
            localResult = new ArrayList<>(1);
          }
          child = processChild(localResult, child, wrap, alignment, null);
        }
        else if (child.getElementType() == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          child = processChild(localResult, child, wrap, alignment, myXmlFormattingPolicy.getTagEndIndent());
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<>(1);
        }
        else if (isTagListStart(child.getElementType())) {
          child = processChild(localResult, child, wrap, alignment, null);
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<>(1);
          insideTag = true;
        }
        else if (isTagListEnd(child.getElementType())) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
            localResult = new ArrayList<>(1);
          }
          child = processChild(localResult, child, wrap, alignment, myXmlFormattingPolicy.getTagEndIndent());
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<>(1);
        }
        else if (isJspxJavaContainingNode(child)) {
          createJspTextNode(localResult, child, getChildIndent());
        }
        /*
        else if (child.getElementType() == ElementType.XML_TEXT) {
          child  = createXmlTextBlocks(localResult, child, wrap, alignment);
        }
        */
        else {
          final Indent indent;

          if (isJspResult(localResult)) {
            //indent = FormatterEx.getInstance().getNoneIndent();
            indent = getChildrenIndent();
          }
          else if (!insideTag) {
            indent = null;
          }
          else {
            indent = getChildrenIndent();
          }

          child = processChild(localResult, child, wrap, alignment, indent);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    if (!localResult.isEmpty()) {
      result.add(createTagContentNode(localResult));
    }

    return result;
  }

  protected boolean isTagListEnd(IElementType elementType) {
    return false;
  }

  protected boolean isTagListStart(IElementType elementType) {
    return false;
  }

  protected boolean isJspResult(final ArrayList<Block> localResult) {
    return false;
  }

  @Override
  protected @Nullable ASTNode processChild(@NotNull List<Block> result,
                                           final @NotNull ASTNode child,
                                           final Wrap wrap,
                                           final Alignment alignment,
                                           final Indent indent) {
    IElementType type = child.getElementType();
    if (type == XmlElementType.XML_TEXT) {
      List<Block> injections = new SmartList<>();
      if (buildInjectedPsiBlocks(injections, child, wrap, alignment, indent)) {
        List<Block> regular = new SmartList<>();
        createXmlTextBlocks(regular, child, wrap, alignment);
        combineRegularBlocksWithInjected(child, result, injections, regular);
        return child;
      }
      else {
        return createXmlTextBlocks(result, child, wrap, alignment);
      }
    }
    else if (type == XmlElementType.HTML_RAW_TEXT) {
      return createXmlTextBlocks(result, child, wrap, alignment);
    }
    else if (type == XmlElementType.XML_COMMENT) {
      if (buildInjectedPsiBlocks(result, child, wrap, alignment, indent)) return child;
      return super.processChild(result, child, wrap, alignment, indent);
    }
    else {
      return super.processChild(result, child, wrap, alignment, indent);
    }
  }

  /**
   * <p>When a single {@code XmlText} element contains one or more injections, which do not span over the whole element, we need to combine
   * regular formatting blocks with injected language blocks. For instance, following example HTML text containing JavaScript interpolation
   * between <code>{{</code> and <code>}}</code>:
   * <pre> foo   bar{{1+ 2   }}   a {{ 12*23}} b </pre>
   * <p>by Injected Block Builder ({@code withInjections} parameter) is split into 5 blocks (T - HTML text block, FL - foreign language block):
   * <p>{@code
   * T< foo   bar{{>,
   * FL<1+ 2   >,
   * T<}}   a {{>,
   * FL< 12*23 >,
   * T<}} b >,
   * }
   * <p>Such division will allow to properly format injections, but will fail to format HTML text, where continuous spaces should be reduced
   * to a single space. On the other hand the regular HTML formatting ({@code regularBlocks} parameter) of the fragment would result in
   * following text blocks: <p><code><foo>, <bar{{1+>, <2>, <}}>, <a>, <{{>, <12*23}}>, <b></code>
   * <p>
   * Here HTML text blocks intersect with injection blocks. These two representations are combined together by the procedure into {@code result}
   * parameter and for the given example following formatting blocks are created:
   * <p>{@code
   * T<foo>, T<bar{{>, FL<1+ 2 >, T<}}>, T<a>, T<{{>, FL<12*23>, T<}}>, T<b>
   * }
   */
  private void combineRegularBlocksWithInjected(@NotNull ASTNode injectionHost, @NotNull List<Block> result,
                                                @NotNull List<Block> withInjections, @NotNull List<Block> regularBlocks) {
    int i = 0;
    int j = 0;
    int injectionHostOffset = injectionHost.getStartOffset();
    // Since there are possible injections without formatter blocks (i.e. whitespace only block),
    // we need to detect such scenarios and correctly split as if there was an injection.
    List<TextRange> injectedRanges = new ArrayList<>(withInjections.size());
    Int2ObjectMap<Block> injectedBlocksMap = new Int2ObjectOpenHashMap<>();
    boolean lastInjected = true;
    int lastOffset = 0;
    for (Block block : withInjections) {
      TextRange range = block.getTextRange();
      if (block instanceof AnotherLanguageBlockWrapper) {
        injectedRanges.add(range);
        injectedBlocksMap.put(range.getStartOffset(), block);
        lastInjected = true;
      }
      else {
        if (!lastInjected) {
          // add range for empty injection
          int offset = range.getStartOffset();
          injectedRanges.add(new TextRange(lastOffset, offset));
        }
        lastOffset = range.getEndOffset();
        lastInjected = false;
      }
    }
    // Perform actual splitting
    int injectedRangesCount = injectedRanges.size();
    while (i < regularBlocks.size()) {
      Block reg = regularBlocks.get(i);
      if (j < injectedRangesCount) {
        TextRange injRange = injectedRanges.get(j);
        TextRange regRange = reg.getTextRange();
        if (regRange.getEndOffset() <= injRange.getStartOffset()) {
          // Regular block does not intersect with injected - add
          result.add(reg);
          i++;
        }
        else if (injRange.contains(regRange)) {
          // Regular block completely within injected - skip
          i++;
        }
        else {
          if (regRange.getStartOffset() < injRange.getStartOffset()) {
            // Regular block ends within or after an injected - split
            ASTNode node = notNull(injectionHost.findLeafElementAt(injRange.getStartOffset() - 1 - injectionHostOffset), injectionHost);
            result.add(createSimpleChild(node, reg.getIndent(), reg.getWrap(), reg.getAlignment(), new TextRange(
              regRange.getStartOffset(), injRange.getStartOffset())));
            if (regRange.getEndOffset() <= injRange.getEndOffset()) {
              // Block ends within injected - move to the next block
              i++;
              continue;
            }
            // Case of a regular block spanning over multiple injected ones
          }
          // Add injected block to the result
          addIfNotNull(result, injectedBlocksMap.get(injRange.getStartOffset()));
          j++;
          if (regRange.getStartOffset() < injRange.getEndOffset()) {
            // we have a regular block starting within or before last added injected block - split
            int lastInjection = injRange.getEndOffset();
            while (j < injectedRangesCount) {
              // check if single block does not span over next injected block
              TextRange nextRange = injectedRanges.get(j);
              if (nextRange.getStartOffset() < regRange.getEndOffset()) {
                // out regular block ends within or after the next injected block - add a split if it's not empty
                if (lastInjection < nextRange.getStartOffset()) {
                  ASTNode node = notNull(injectionHost.findLeafElementAt(lastInjection - injectionHostOffset), injectionHost);
                  result.add(createSimpleChild(node, reg.getIndent(), null, reg.getAlignment(),
                                               new TextRange(lastInjection, nextRange.getStartOffset())));
                }
                lastInjection = nextRange.getEndOffset();
                if (lastInjection <= regRange.getEndOffset()) {
                  // Add current injected block and repeat if regular block ends after it
                  addIfNotNull(result, injectedBlocksMap.get(nextRange.getStartOffset()));
                  j++;
                  continue;
                }
              }
              break;
            }
            // We might have some leftover of regular block after last added injected one,
            // which does not end within or after the next injected block - add a split
            if (lastInjection < regRange.getEndOffset()) {
              ASTNode node = notNull(injectionHost.findLeafElementAt(lastInjection - injectionHostOffset), injectionHost);
              result.add(createSimpleChild(node, reg.getIndent(), null, reg.getAlignment(),
                                           new TextRange(lastInjection, regRange.getEndOffset())));
            }
            i++;
          }
        }
      }
      else {
        // No more injected blocks to process, just add the regular ones
        result.add(reg);
        i++;
      }
    }
    // Add any leftover injected blocks
    while (j < injectedRangesCount) {
      addIfNotNull(result, injectedBlocksMap.get(injectedRanges.get(j++).getStartOffset()));
    }
  }

  protected Indent getChildrenIndent() {
    return myXmlFormattingPolicy.indentChildrenOf(getTag())
           ? Indent.getNormalIndent()
           : Indent.getNoneIndent();
  }

  @Override
  public @Nullable Indent getIndent() {
    return myIndent;
  }

  private ASTNode createXmlTextBlocks(final List<Block> list, final @NotNull ASTNode textNode, final Wrap wrap, final Alignment alignment) {
    ASTNode child = textNode.getFirstChildNode();
    return createXmlTextBlocks(list, textNode, child, wrap, alignment);
  }

  private ASTNode createXmlTextBlocks(final List<Block> list, final ASTNode textNode, ASTNode child,
                                      final Wrap wrap,
                                      final Alignment alignment) {
    while (child != null) {
      if (!AbstractXmlBlock.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        final Indent indent = getChildrenIndent();
        child = processChild(list, child, wrap, alignment, indent);
        if (child == null) return null;
        if (child.getTreeParent() != textNode) {
          if (child.getTreeParent() != myNode) {
            return createXmlTextBlocks(list, child.getTreeParent(), child.getTreeNext(), wrap, alignment);
          }
          else {
            return child;
          }
        }
      }
      child = child.getTreeNext();
    }
    return textNode;
  }

  private Block createTagContentNode(@NotNull ArrayList<@NotNull Block> localResult) {
    return createSyntheticBlock(localResult, getChildrenIndent());
  }

  protected Block createSyntheticBlock(@NotNull ArrayList<@NotNull Block> localResult, @Nullable Indent childrenIndent) {
    return new SyntheticBlock(localResult, this, Indent.getNoneIndent(), myXmlFormattingPolicy, childrenIndent);
  }

  private Block createTagDescriptionNode(@NotNull ArrayList<@NotNull Block> localResult) {
    return createSyntheticBlock(localResult, null);
  }

  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    if (isPreserveSpace()) return Spacing.getReadOnlySpacing();
    if (child1 instanceof AbstractSyntheticBlock && child2 instanceof AbstractSyntheticBlock) {
      return getSpacing((AbstractSyntheticBlock)child1, (AbstractSyntheticBlock)child2);
    }
    return null;
  }

  protected Spacing getSpacing(final AbstractSyntheticBlock syntheticBlock1, final AbstractSyntheticBlock syntheticBlock2) {
    if (syntheticBlock2.startsWithCDATA() || syntheticBlock1.endsWithCDATA()) {
      return Spacing.getReadOnlySpacing();
    }

    if (syntheticBlock1.containsCDATA() && syntheticBlock2.isTagDescription()
        || syntheticBlock1.isTagDescription() && syntheticBlock2.containsCDATA()) {
      int lineFeeds = 0;
      switch (myXmlFormattingPolicy.getWhiteSpaceAroundCDATAOption()) {
        case XmlCodeStyleSettings.WS_AROUND_CDATA_NONE -> { }
        case XmlCodeStyleSettings.WS_AROUND_CDATA_NEW_LINES -> lineFeeds = 1;
        case XmlCodeStyleSettings.WS_AROUND_CDATA_PRESERVE -> {
          return Spacing.getReadOnlySpacing();
        }
        default -> {
          assert false : "Unexpected whitespace around CDATA code style option.";
        }
      }
      return Spacing.createSpacing(0, 0, lineFeeds, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                   myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock2.isJspTextBlock() || syntheticBlock1.isJspTextBlock()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock2.isJspxTextBlock() || syntheticBlock1.isJspxTextBlock()) {
      return Spacing.createSpacing(0, 0, 1, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(getTag())) return Spacing.getReadOnlySpacing();

    if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
      return Spacing.getReadOnlySpacing();
    }

    if (syntheticBlock2.startsWithTag()) {
      final XmlTag startTag = syntheticBlock2.getStartTag();
      if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(startTag) && startTag.textContains('\n')) {
        return getChildrenIndent() != Indent.getNoneIndent()
               ? Spacing.getReadOnlySpacing()
               : Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    boolean saveSpacesBetweenTagAndText = myXmlFormattingPolicy.shouldSaveSpacesBetweenTagAndText() &&
                                          syntheticBlock1.getTextRange().getEndOffset() < syntheticBlock2.getTextRange().getStartOffset();

    if (syntheticBlock1.endsWithTextElement() && syntheticBlock2.startsWithTextElement()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaksInText(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock1.endsWithText()) { //text</tag
      if (syntheticBlock1.insertLineFeedAfter()) {
        return Spacing.createDependentLFSpacing(0, 0, getTag().getTextRange(), myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                                myXmlFormattingPolicy.getKeepBlankLines());
      }
      if (saveSpacesBetweenTagAndText) {
        return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
      }
      return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }
    else if (syntheticBlock1.isTagDescription() && syntheticBlock2.isTagDescription()) { //></
      return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }
    else if (syntheticBlock2.startsWithText()) { //>text
      if (saveSpacesBetweenTagAndText) {
        return Spacing.createSafeSpacing(true, myXmlFormattingPolicy.getKeepBlankLines());
      }
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    else if (syntheticBlock1.isTagDescription() && syntheticBlock2.startsWithTag()) {
      return Spacing.createSpacing(0, 0, myXmlFormattingPolicy.insertLineBreakAfterTagBegin(getTag()) ? 2 : 0,
                                   true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    else if (syntheticBlock1.insertLineFeedAfter()) {
      return Spacing.createSpacing(0, 0, 1, true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    else if (syntheticBlock1.endsWithTag() && syntheticBlock2.isTagDescription()) {
      return Spacing.createSpacing(0, 0, myXmlFormattingPolicy.insertLineBreakAfterTagBegin(getTag()) ? 2 : 0,
                                   true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(true, true);
    }
  }

  @Override
  public boolean insertLineBreakBeforeTag() {
    return myXmlFormattingPolicy.insertLineBreakBeforeTag(getTag());
  }

  @Override
  public int getBlankLinesBeforeTag() {
    return myXmlFormattingPolicy.getBlankLinesBeforeTag(getTag());
  }

  @Override
  public boolean removeLineBreakBeforeTag() {
    return myXmlFormattingPolicy.removeLineBreakBeforeTag(getTag());
  }

  @Override
  public boolean isTextElement() {
    return myXmlFormattingPolicy.isTextElement(getTag());
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isAfterAttribute(newChildIndex)) {
      List<Block> subBlocks = getSubBlocks();
      Block subBlock = subBlocks.get(newChildIndex - 1);
      int prevSubBlockChildrenCount = subBlock.getSubBlocks().size();
      return subBlock.getChildAttributes(prevSubBlockChildrenCount);
    }
    else {
      if (myXmlFormattingPolicy.indentChildrenOf(getTag())) {
        return new ChildAttributes(Indent.getNormalIndent(), null);
      }
      else {
        return new ChildAttributes(Indent.getNoneIndent(), null);
      }
    }
  }

  private boolean isAfterAttribute(final int newChildIndex) {
    List<Block> subBlocks = getSubBlocks();
    int index = newChildIndex - 1;
    Block prevBlock = index < subBlocks.size() ? subBlocks.get(index) : null;
    return prevBlock instanceof SyntheticBlock && ((SyntheticBlock)prevBlock).endsWithAttribute();
  }
}
