/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.template.formatter;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.Indent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TemplateFormatUtil {

  private final static List<PsiElement> EMPTY_PSI_ELEMENT_LIST = new ArrayList<>();
  
  private final static String[] IGNORABLE_ERROR_MESSAGES = {
    XmlErrorMessages.message("xml.parsing.closing.tag.matches.nothing"),
    XmlErrorMessages.message("xml.parsing.closing.tag.name.missing")
  };

  private TemplateFormatUtil() {
  }

  @NotNull
  static List<PsiElement> findAllMarkupLanguageElementsInside(PsiElement outerLangElement) {
    PsiFile file = outerLangElement.getContainingFile();
    if (file != null && file.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
      TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)file.getViewProvider();
      return findAllElementsInside(outerLangElement.getTextRange(), viewProvider, false);
    }
    return EMPTY_PSI_ELEMENT_LIST;
  }

  @NotNull
  static List<PsiElement> findAllTemplateLanguageElementsInside(@NotNull PsiElement outerLangElement,
                                                                       @NotNull TemplateLanguageFileViewProvider viewProvider) {
    return findAllElementsInside(outerLangElement.getTextRange(), viewProvider, true);
  }

  @NotNull
  static List<PsiElement> findAllElementsInside(@NotNull TextRange range,
                                                @NotNull TemplateLanguageFileViewProvider viewProvider,
                                                boolean fromTemplate) {
    return findAllElementsInside(range, viewProvider, viewProvider.getBaseLanguage(),
                                 fromTemplate ? viewProvider.getBaseLanguage() : viewProvider.getTemplateDataLanguage());
  }

  @NotNull
  public static List<PsiElement> findAllElementsInside(TextRange range,
                                                       TemplateLanguageFileViewProvider viewProvider,
                                                       Language templateLanguage, Language language) {
    List<PsiElement> matchingElements = new ArrayList<>();
    PsiElement currElement = viewProvider.findElementAt(range.getStartOffset(), language);
    while (currElement instanceof OuterLanguageElement) {
      currElement = currElement.getNextSibling();
    }
    if (currElement != null) {
      currElement = findTopmostElementInRange(currElement, range);
      Pair<Integer, PsiElement> result =
        addElementSequence(currElement, templateLanguage, range, matchingElements, templateLanguage == language);
      int lastOffset = result.first;
      assert lastOffset >= 0 : "Failed to process elements in range: " + range;
      if (lastOffset < range.getEndOffset()) {
        List<PsiElement> moreElements =
          findAllElementsInside(new TextRange(lastOffset, range.getEndOffset()), viewProvider, templateLanguage, language);
        matchingElements.addAll(moreElements);
      }
    }
    return matchingElements;
  }

  private static Pair<Integer,PsiElement> addElementSequence(PsiElement startElement, Language templateLanguage, TextRange range, List<PsiElement> targetList, boolean fromTemplate) {
    PsiElement currElement = startElement;
    int lastOffset = -1;
    while (currElement != null && (lastOffset = currElement.getTextRange().getEndOffset()) <= range.getEndOffset()) {
      boolean isTemplateLanguage = currElement.getLanguage().is(templateLanguage);
      if (fromTemplate == isTemplateLanguage) {
        targetList.add(currElement);
      }
      currElement = currElement.getNextSibling();
    }
    if (currElement != null && currElement.getTextRange().intersects(range)) {
      PsiElement child = currElement.getFirstChild();
      if (child != null) {
        addElementSequence(child, templateLanguage, range, targetList, fromTemplate);
      }
    }
    return new Pair<>(lastOffset, currElement);
  }


  @NotNull
  public static PsiElement findTopmostElementInRange(@NotNull PsiElement original, TextRange fitToRange) {
    PsiElement currElement = original;
    PsiElement prevElement = original;
    while (currElement != null) {
      if ((currElement instanceof PsiFile) || !fitToRange.contains(currElement.getTextRange())) {
        if (!fitToRange.contains(prevElement.getTextRange())) {
          return original;
        }
        return prevElement;
      }
      prevElement = currElement;
      currElement = currElement.getParent();
    }
    return original;
  }

  static List<Block> mergeBlocks(List<Block> originalBlocks, List<Block> blocksToMerge, TextRange range)
    throws FragmentedTemplateException {
    if (blocksToMerge.isEmpty()) return originalBlocks;
    List<Block> result = new ArrayList<>();
    if (originalBlocks.isEmpty()) {
      for (Block mergeCandidate : blocksToMerge) {
        if (range.contains(mergeCandidate.getTextRange())) {
          result.add(mergeCandidate);
        }
      }
      return result;
    }
    List<TextRange> originalRanges = new ArrayList<>();
    for (Block originalBlock : originalBlocks) {
      originalRanges.add(originalBlock.getTextRange());
    }
    int lastOffset = range.getStartOffset();
    for (Iterator<Block> originalBlockIterator = originalBlocks.iterator(); originalBlockIterator.hasNext();) {
      Block originalBlock = originalBlockIterator.next();
      int startOffset = originalBlock.getTextRange().getStartOffset();
      if (lastOffset < startOffset) {
        lastOffset = fillGap(originalRanges, blocksToMerge, result, lastOffset, startOffset);
        if (lastOffset < startOffset) {
          lastOffset = fillGap(originalRanges, originalBlocks, result, lastOffset, startOffset);
        }
      }
      Block mergeableBlock = getBlockContaining(blocksToMerge, originalRanges, originalBlock.getTextRange());
      if (mergeableBlock != null) {
        if (mergeableBlock.getTextRange().getStartOffset() >= lastOffset) {
          result.add(mergeableBlock);
          lastOffset = mergeableBlock.getTextRange().getEndOffset();
        }
      }
      else {
        if (startOffset >= lastOffset) {
          result.add(originalBlock);
          originalBlockIterator.remove();
          lastOffset = originalBlock.getTextRange().getEndOffset();
        }
      }
    }
    if (lastOffset < range.getEndOffset()) {
      lastOffset = fillGap(originalRanges, blocksToMerge, result, lastOffset, range.getEndOffset());
      if (lastOffset < range.getEndOffset()) {
        fillGap(originalRanges, originalBlocks, result, lastOffset, range.getEndOffset());
      }
    }
    return result;
  }

  private static int fillGap(List<TextRange> originalRanges, List<Block> blocks, List<Block> result, int startOffset, int endOffset)
    throws FragmentedTemplateException {
    return fillGap(null, originalRanges, blocks, result, startOffset, endOffset, 0);
  }

  private static int fillGap(@Nullable Block parent,
                             List<TextRange> originalRanges,
                             List<Block> blocks,
                             List<Block> result,
                             int startOffset,
                             int endOffset,
                             int depth) throws
                                        FragmentedTemplateException {
    int lastOffset = startOffset;
    TextRange currRange = new TextRange(lastOffset, endOffset);
    for (Block block : blocks) {
      if (lastOffset == endOffset || block.getTextRange().getStartOffset() > endOffset) return lastOffset;
      if (currRange.contains(block.getTextRange())) {
        result.add(block);
        if (parent != null && block instanceof IndentInheritingBlock) {
          ((IndentInheritingBlock)block).setIndent(parent.getIndent());
        }
        lastOffset = block.getTextRange().getEndOffset();
        currRange = new TextRange(lastOffset, endOffset);
      }
      else if (currRange.intersects(block.getTextRange()) && intersectsOneOf(block.getTextRange(), originalRanges)) {
        List<Block> subBlocks = block.getSubBlocks();
        if (block instanceof TemplateLanguageBlock && ((TemplateLanguageBlock)block).containsErrorElements()) {
          throw new FragmentedTemplateException();
        }
        lastOffset = fillGap(block, originalRanges, subBlocks, result, lastOffset, endOffset, depth + 1);
        currRange = new TextRange(lastOffset, endOffset);
      }
    }
    return lastOffset;
  }

  public static boolean intersectsOneOf(TextRange blockRange, List<TextRange> originalRanges) {
    return
      rangesContain(originalRanges, 0, originalRanges.size() - 1, blockRange.getStartOffset()) ||
      rangesContain(originalRanges, 0, originalRanges.size() - 1, blockRange.getEndOffset());
  }

  static boolean rangesContain(List<TextRange> ranges, int startIndex, int endIndex, int offset) {
    if (endIndex < startIndex || ranges.size() <= startIndex || ranges.size() <= endIndex) return false;
    int startOffset = ranges.get(startIndex).getStartOffset();
    int endOffset = ranges.get(endIndex).getEndOffset();
    if (offset < startOffset || offset > endOffset) return false;
    if (startIndex == endIndex) return true;
    int midIndex = (endIndex + startIndex) / 2;
    return rangesContain(ranges, startIndex, midIndex, offset)  || rangesContain(ranges, midIndex  + 1, endIndex, offset);
  }

  private static Block getBlockContaining(List<Block> blockList, List<TextRange> originalRanges, TextRange range) {
    return getBlockContaining(blockList, originalRanges, range, 0);
  }

  @Nullable
  private static Block getBlockContaining(List<Block> blockList, List<TextRange> originalRanges, TextRange range, int depth) {
    for (Block block : blockList) {
      if (block.getTextRange().contains(range)) {
        if (intersectsOneOf(block.getTextRange(), originalRanges)) {
          Block containingBlock = getBlockContaining(block.getSubBlocks(), originalRanges, range, depth + 1);
          if (containingBlock != null) return containingBlock;
        }
        return block;
      }
    }
    return null;
  }

  /**
   * Creates a template language block for the given outer element if possible. Finds all the elements matching the current outerElement in
   * a template language PSI tree and builds a submodel for them with a composite root block.
   * 
   * @param outerElement  The outer element for which the submodel (template language root block) is to be built.
   * @param settings      Code style settings to be used to build the submodel.
   * @param indent        The indent for the root block.
   * @return Template language root block (submodel) or null if it can't be built.
   */

  @Nullable
  public static Block buildTemplateLanguageBlock(@NotNull OuterLanguageElement outerElement,
                                                 @NotNull CodeStyleSettings settings,
                                                 @Nullable Indent indent) {
    try {
      PsiFile file = outerElement.getContainingFile();
      FileViewProvider viewProvider = outerElement.getContainingFile().getViewProvider();
      if (viewProvider instanceof TemplateLanguageFileViewProvider) {
        Language language = outerElement.getLanguage();
        FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(language, outerElement);
        if (builder instanceof AbstractXmlTemplateFormattingModelBuilder) {
          FormattingModel model = ((AbstractXmlTemplateFormattingModelBuilder)builder)
            .createTemplateFormattingModel(file, (TemplateLanguageFileViewProvider)viewProvider, outerElement, settings, indent);
          if (model != null) {
            return model.getRootBlock();
          }
        }
      }
    }
    catch (FragmentedTemplateException e) {
      // Ignore and return null
    }
    return null;
  }

  public static boolean isErrorElement(@NotNull PsiElement element) {
    if (element instanceof PsiErrorElement) {
      String description = ((PsiErrorElement)element).getErrorDescription();
      for (String ignorableMessage : IGNORABLE_ERROR_MESSAGES) {
        if (ignorableMessage.equals(description)) return false;
      }
      return true;
    }
    return false;
  }
}
