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

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.xml.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.SimpleTemplateLanguageFormattingModelBuilder;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Suitable for XML/HTML templates. Creates a model which provides correct indentation for a hierarchy of nested markup/template language
 * blocks. For example:
 * <pre>
 *    &lt;div&gt;
 *       &lt;?if (condition):?&gt;
 *         &lt;div&gt;content&lt;/div&gt;
 *       &lt;?endif?&gt;
 *     &lt;/div&gt;
 * </pre>
 * where template conditional block is indented inside HTML &lt;div&gt; tag and in turn &lt;div&gt;content&lt;div&gt; tag is indented
 * inside its surrounding 'if' block.
 */
public abstract class AbstractXmlTemplateFormattingModelBuilder extends SimpleTemplateLanguageFormattingModelBuilder {
  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
      final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)psiFile.getViewProvider();
      if (isTemplateFile(psiFile)) {
        Language templateDataLanguage = viewProvider.getTemplateDataLanguage();
        if (templateDataLanguage != psiFile.getLanguage()) {
          return createDataLanguageFormattingModel(
            viewProvider.getPsi(templateDataLanguage),
            templateDataLanguage,
            settings,
            psiFile,
            Indent.getNoneIndent());
        }
      }
      else if (element instanceof OuterLanguageElement && isOuterLanguageElement(element)) {
        FormattingModel model =
          createTemplateFormattingModel(psiFile, viewProvider, (OuterLanguageElement)element, settings, Indent.getNoneIndent());
        if (model != null) return model;
      }
    }
    return super.createModel(element, settings);
  }

  @Nullable
  FormattingModel createTemplateFormattingModel(@NotNull PsiFile psiFile,
                                                       @NotNull TemplateLanguageFileViewProvider viewProvider,
                                                       @NotNull OuterLanguageElement outerTemplateElement,
                                                       @NotNull CodeStyleSettings settings,
                                                       @Nullable Indent indent) {
    List<PsiElement> templateElements = TemplateFormatUtil.findAllTemplateLanguageElementsInside(outerTemplateElement, viewProvider);
    return createTemplateFormattingModel(psiFile, settings, getPolicy(settings, psiFile), templateElements, indent);
  }

  @Nullable
  public FormattingModel createTemplateFormattingModel(PsiFile file,
                                                       CodeStyleSettings settings,
                                                       XmlFormattingPolicy xmlFormattingPolicy,
                                                       List<PsiElement> elements,
                                                       Indent indent) {
    if (elements.size() == 0) return null;
    List<Block> templateBlocks = new ArrayList<>();
    for (PsiElement element : elements) {
      if (!isMarkupLanguageElement(element) && !FormatterUtil.containsWhiteSpacesOnly(element.getNode())) {
        templateBlocks.add(createTemplateLanguageBlock(element.getNode(), settings, xmlFormattingPolicy, indent, null, null));
      }
    }
    if (templateBlocks.size() == 0) return null;
    Block topBlock = templateBlocks.size() == 1 ? templateBlocks.get(0) : new CompositeTemplateBlock(templateBlocks);
    return new DocumentBasedFormattingModel(topBlock, file.getProject(), settings, file.getFileType(), file);
  }

  /**
   * Checks if the file is a template file (typically a template view provider contains two roots: one for template language and one for
   * HTML). When creating a model for an element, the builder needs to know to which of the roots the element belongs. In most cases
   * a simple 'instanceof' check should be sufficient.
   *
   * @param file The file to check
   * @return True if the file is a template file.
   */
  protected abstract boolean isTemplateFile(PsiFile file);

  /**
   * Checks if the element is an outer language element inside XML/HTML. Such elements are created as placeholders for template language
   * fragments.
   *
   * @param element The element to check.
   * @return True if the element is an outer (template) language fragment in XML/HTML.
   */
  public abstract boolean isOuterLanguageElement(PsiElement element);

  /**
   * Checks if the element is a placeholder for XML/HTML fragment inside a template language PSI tree.
   * @param element The element to check.
   * @return True if the element covers a fragment of XML/HTML inside a template language.
   */
  public abstract boolean isMarkupLanguageElement(PsiElement element);

  private FormattingModel createDataLanguageFormattingModel(PsiElement dataElement,
                                                           Language language,
                                                           CodeStyleSettings settings,
                                                           PsiFile psiFile,
                                                           @Nullable Indent indent) {
    Block block = createDataLanguageRootBlock(dataElement, language, settings, getPolicy(settings, psiFile), psiFile, indent);
    return new DocumentBasedFormattingModel(block, psiFile.getProject(), settings, psiFile.getFileType(), psiFile);
  }

  public Block createDataLanguageRootBlock(PsiElement dataElement,
                                            Language language,
                                            CodeStyleSettings settings,
                                            XmlFormattingPolicy xmlFormattingPolicy,
                                            PsiFile psiFile,
                                            Indent indent) {
    Block block;
    if (dataElement instanceof XmlTag) {
      block = createXmlTagBlock(dataElement.getNode(), null, null, xmlFormattingPolicy, indent);
    }
    else {
      if (language.isKindOf(XMLLanguage.INSTANCE)) {
        block =
          createXmlBlock(dataElement.getNode(), null, Alignment.createAlignment(), xmlFormattingPolicy,
                         indent,
                         dataElement.getTextRange());
      }
      else {
        final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(language, dataElement);
        if (builder != null && !isInsideXmlAttributeValue(dataElement)) {
          FormattingModel otherLanguageModel = builder.createModel(dataElement, settings);
          block = otherLanguageModel.getRootBlock();
        }
        else {
          block = new ReadOnlyBlock(dataElement.getNode());
        }
      }
    }
    return block;
  }

  /**
   * Creates a template language block. Although it is not strictly required, for the builder to merge blocks with XML/HTML sub-blocks,
   * it is necessary to inherit the template language block from {@link TemplateLanguageBlock}. Actually the merge happens inside
   * {@link TemplateLanguageBlock#buildChildren()} method.
   *
   * @param node                  The AST node to create the block for.
   * @param settings              The current code style settings (note: you need to use {@link CodeStyleSettings#getCommonSettings(Language)}
   *                              and {@link CodeStyleSettings#getCustomSettings(Class)} for template language settings.
   * @param xmlFormattingPolicy   The current XML formatting policy.
   * @param indent                The default indent to be used with the template block. It can be modified when XML/HTML and template
   *                              blocks are merged.
   * @param alignment             The template block alignment.
   * @param wrap                  The template block wrap.
   * @return The newly created template block.
   */
  protected abstract Block createTemplateLanguageBlock(ASTNode node,
                                                       CodeStyleSettings settings,
                                                       XmlFormattingPolicy xmlFormattingPolicy,
                                                       Indent indent,
                                                       @Nullable Alignment alignment,
                                                       @Nullable Wrap wrap);

  /**
   * Creates an xml block. Override this method to create your own xml block if you want
   * to control spacing etc. By default the method returns {@code TemplateXmlTagBlock} instance.
   */
  protected XmlTagBlock createXmlTagBlock(ASTNode node,
                                          @Nullable Wrap wrap,
                                          @Nullable Alignment alignment,
                                          XmlFormattingPolicy policy,
                                          @Nullable Indent indent) {
    return new TemplateXmlTagBlock(this, node, wrap, alignment, policy, indent);
  }

  protected XmlBlock createXmlBlock(ASTNode node,
                                    @Nullable Wrap wrap,
                                    @Nullable Alignment alignment,
                                    XmlFormattingPolicy policy,
                                    @Nullable Indent indent,
                                    @Nullable TextRange textRange) {
    return new TemplateXmlBlock(this, node, wrap, alignment, policy, indent, textRange);
  }

  /**
   * Creates a synthetic block containing given sub-blocks. Override this method to create your own synthetic block if you want
   * to control spacing etc. between child blocks. By default the method returns {@code TemplateSyntheticBlock} instance.
   *
   * @param subBlocks   The sub-blocks which will be contained in the synthetic block.
   * @param parent      Synthetic block's parent.
   * @param indent      The sub-block default indent. Block merge algorithm may overwrite it if synthetic block is
   *                    implementing {@code IndentInheritingBlock} interface.
   * @param policy      Xml formatting policy.
   * @param childIndent The indent to be used with child blocks.
   * @return A newly created template synthetic block.
   */
  protected SyntheticBlock createSyntheticBlock(List<Block> subBlocks,
                                                Block parent,
                                                Indent indent,
                                                XmlFormattingPolicy policy,
                                                Indent childIndent) {
    return new TemplateSyntheticBlock(subBlocks, parent, indent, policy, childIndent);
  }

  public List<Block> mergeWithTemplateBlocks(List<Block> markupBlocks,
                                             CodeStyleSettings settings,
                                             XmlFormattingPolicy xmlFormattingPolicy,
                                             Indent childrenIndent) throws FragmentedTemplateException {
    int templateLangRangeStart = Integer.MAX_VALUE;
    int templateLangRangeEnd = -1;
    int rangeStart = Integer.MAX_VALUE;
    int rangeEnd = -1;
    PsiFile templateFile = null;
    List<Block> pureMarkupBlocks = new ArrayList<>();
    for (Block block : markupBlocks) {
      TextRange currRange = block.getTextRange();
      rangeStart = Math.min(currRange.getStartOffset(), rangeStart);
      rangeEnd = Math.max(currRange.getEndOffset(), rangeEnd);
      boolean isMarkupBlock = true;
      if (block instanceof AnotherLanguageBlockWrapper) {
        AnotherLanguageBlockWrapper wrapper = (AnotherLanguageBlockWrapper)block;
        PsiElement otherLangElement = wrapper.getNode().getPsi();
        if (isOuterLanguageElement(otherLangElement)) {
          isMarkupBlock = false;
          if (templateFile == null) {
            FileViewProvider provider = otherLangElement.getContainingFile().getViewProvider();
            templateFile = provider.getPsi(provider.getBaseLanguage());
          }
          templateLangRangeStart = Math.min(currRange.getStartOffset(), templateLangRangeStart);
          templateLangRangeEnd = Math.max(currRange.getEndOffset(), templateLangRangeEnd);
        }
      }
      if (isMarkupBlock) {
        pureMarkupBlocks.add(block);
      }
    }
    if (templateLangRangeEnd > templateLangRangeStart && templateFile != null) {
      List<Block> templateBlocks =
        buildTemplateLanguageBlocksInside(templateFile, new TextRange(templateLangRangeStart, templateLangRangeEnd), settings,
                                          xmlFormattingPolicy, childrenIndent);
      if (pureMarkupBlocks.isEmpty()) {
        return afterMerge(templateBlocks, true, settings, xmlFormattingPolicy);
      }
      return afterMerge(TemplateFormatUtil.mergeBlocks(pureMarkupBlocks, templateBlocks, new TextRange(rangeStart, rangeEnd)), false,
                        settings, xmlFormattingPolicy);
    }
    return markupBlocks;
  }


  private List<Block> buildTemplateLanguageBlocksInside(@NotNull PsiFile templateFile,
                                                        @NotNull TextRange range,
                                                        CodeStyleSettings settings,
                                                        XmlFormattingPolicy xmlFormattingPolicy,
                                                        Indent childrenIndent) {
    List<Block> templateBlocks = new ArrayList<>();
    TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)templateFile.getViewProvider();
    List<PsiElement> templateElements = TemplateFormatUtil.findAllElementsInside(range,
                                                                                 viewProvider,
                                                                                 true);
    FormattingModel localModel = createTemplateFormattingModel(templateFile, settings, xmlFormattingPolicy, templateElements, childrenIndent);
    if (localModel != null) {
      Block rootBlock = localModel.getRootBlock();
      if (rootBlock instanceof CompositeTemplateBlock) {
        templateBlocks.addAll(rootBlock.getSubBlocks());
      }
      else {
        templateBlocks.add(rootBlock);
      }
    }
    return templateBlocks;
  }

  /**
   * The method is called after markup blocks are merged with template language blocks which may require some additional block
   * rearrangement. By default returns the same block sequence.
   *
   * @param originalBlocks A sequence of template and markup blocks.
   * @param templateOnly   True if originalBlocks contain only template blocks and no markup.
   * @return Rearranged blocks.
   */
  protected List<Block> afterMerge(List<Block> originalBlocks,
                                   boolean templateOnly,
                                   CodeStyleSettings settings,
                                   @NotNull XmlFormattingPolicy xmlFormattingPolicy) {
    return originalBlocks;
  }

  protected static XmlFormattingPolicy getPolicy(CodeStyleSettings settings, PsiFile psiFile) {
    final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(psiFile);
    return new HtmlPolicy(settings, documentModel);
  }
  
  private static boolean isInsideXmlAttributeValue(PsiElement element) {
    XmlAttributeValue value = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class, true);
    return value != null;
  }
}
