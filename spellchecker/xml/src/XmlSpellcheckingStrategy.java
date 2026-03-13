// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.xml;

import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.lang.Language;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.LanguageSpellchecking;
import com.intellij.spellchecker.tokenizer.SuppressibleSpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class XmlSpellcheckingStrategy extends SuppressibleSpellcheckingStrategy implements DumbAware {

  private final Tokenizer<? extends PsiElement> myXmlTextTokenizer = createTextTokenizer();
  private final Tokenizer<? extends PsiElement> myXmlCommentTokenizer = createCommentTokenizer();
  private final Tokenizer<? extends PsiElement> myXmlAttributeTokenizer = createAttributeValueTokenizer();

  private static final Set<Class<? extends Language>> TEXT_LEVEL_SUPPORTED_DIALECTS =
    Set.of(XMLLanguage.class, XHTMLLanguage.class, DTDLanguage.class, HTMLLanguage.class);

  @Override
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
    if (hasForeignLanguageChildren(element)) return EMPTY_TOKENIZER;
    if (element instanceof XmlText text) {
      return (useTextLevelSpellchecking(element) || text.getText().isBlank()) ? EMPTY_TOKENIZER : myXmlTextTokenizer;
    }
    if (isXmlComment(element)) {
      return useTextLevelSpellchecking(element) ? EMPTY_TOKENIZER : myXmlCommentTokenizer;
    }
    if (element instanceof XmlToken
        && ((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS
        && !isXmlDataCharactersParentHandledByItsStrategy(element.getParent())) {
      // Special case for all other XML_DATA_CHARACTERS, which are not handled through parent PSI
      return isInTemplateLanguageFile(element) ? EMPTY_TOKENIZER : TEXT_TOKENIZER;
    }
    if (element instanceof XmlAttributeValue attribute) {
      return (useTextLevelSpellchecking(element) || attribute.getValue().isEmpty()) ? EMPTY_TOKENIZER : myXmlAttributeTokenizer;
    }
    return super.getTokenizer(element);
  }

  private static boolean hasForeignLanguageChildren(PsiElement element) {
    for (PsiElement child : element.getChildren()) {
      if (child.getLanguage().isKindOf(XMLLanguage.INSTANCE)) continue;
      if (child.getLanguage() != element.getLanguage()) return true;
    }
    return false;
  }

  private static boolean hasOnlySupportedDialects(PsiElement element) {
    return TEXT_LEVEL_SUPPORTED_DIALECTS.containsAll(
      ContainerUtil.map(element.getContainingFile().getViewProvider().getLanguages(), Language::getClass)
    );
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String name) {
    DomElement domElement = DomUtil.getDomElement(element);
    if (domElement != null) {
      if (domElement.getAnnotation(NoSpellchecking.class) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public SuppressQuickFix[] getSuppressActions(@NotNull PsiElement element, @NotNull String name) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  protected Tokenizer<? extends PsiElement> createAttributeValueTokenizer() {
    return new XmlAttributeValueTokenizer(PlainTextSplitter.getInstance());
  }

  private boolean isXmlDataCharactersParentHandledByItsStrategy(@Nullable PsiElement parent) {
    if (parent == null) return false;
    var strategy = ContainerUtil.findInstance(
      LanguageSpellchecking.INSTANCE.allForLanguage(parent.getLanguage()),
      XmlSpellcheckingStrategy.class);
    return strategy != null ? strategy.isXmlDataCharactersParentHandled(parent)
                            : isXmlDataCharactersParentHandled(parent);
  }

  protected boolean isXmlDataCharactersParentHandled(@NotNull PsiElement parent) {
    return parent instanceof XmlText
           || parent.getNode().getElementType() == XmlElementType.XML_CDATA;
  }

  protected boolean isInTemplateLanguageFile(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file == null || file.getLanguage() instanceof TemplateLanguage;
  }

  @Override
  public boolean useTextLevelSpellchecking(PsiElement element) {
    return Registry.is("spellchecker.grazie.enabled", false) && hasOnlySupportedDialects(element);
  }

  @Override
  protected boolean isLiteral(@NotNull PsiElement element) {
    return element instanceof XmlAttributeValue || element instanceof XmlText;
  }

  @Override
  protected boolean isComment(@NotNull PsiElement element) {
    return isXmlComment(element) || element.getNode().getElementType() == XmlTokenType.XML_COMMENT_CHARACTERS;
  }

  private static boolean isXmlComment(@NotNull PsiElement element) {
    return element instanceof XmlComment;
  }

  protected Tokenizer<? extends PsiElement> createTextTokenizer() {
    return new XmlTextTokenizer(PlainTextSplitter.getInstance());
  }

  protected Tokenizer<? extends PsiElement> createCommentTokenizer() {
    return new XmlCommentTokenizer(PlainTextSplitter.getInstance());
  }

  protected abstract static class XmlTextContentTokenizer<T extends XmlElement> extends XmlTokenizerBase<T> {

    public XmlTextContentTokenizer(Splitter splitter) {
      super(splitter);
    }

    protected abstract boolean isContentToken(IElementType tokenType);

    @Override
    protected @NotNull List<@NotNull TextRange> getSpellcheckOuterContentRanges(@NotNull T element) {
      List<TextRange> result = new SmartList<>(super.getSpellcheckOuterContentRanges(element));
      element.acceptChildren(new XmlElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element.getNode().getElementType() == XmlElementType.XML_CDATA) {
            element.acceptChildren(this);
          }
          else if (!(element instanceof LeafPsiElement)
                   || !isContentToken(element.getNode().getElementType())) {
            result.add(element.getTextRangeInParent());
          }
        }
      });
      return result;
    }
  }

  private static class XmlCommentTokenizer extends XmlTokenizerBase<XmlComment> {

    private XmlCommentTokenizer(Splitter splitter) {
      super(splitter);
    }

    @Override
    protected @NotNull List<@NotNull SpellcheckRange> getSpellcheckRanges(@NotNull XmlComment comment) {
      var ranges = new SmartList<SpellcheckRange>();
      comment.acceptChildren(new XmlElementVisitor() {
        @Override
        public void visitXmlToken(@NotNull XmlToken token) {
          if (token.getNode().getElementType() == XmlTokenType.XML_COMMENT_CHARACTERS) {
            var text = token.getText();
            ranges.add(new SpellcheckRange(text, false, token.getStartOffsetInParent(), TextRange.allOf(text)));
          }
        }
      });
      return ranges;
    }
  }

  protected static class XmlTextTokenizer extends XmlTextContentTokenizer<XmlText> {

    public XmlTextTokenizer(Splitter splitter) {
      super(splitter);
    }

    @Override
    protected boolean isContentToken(IElementType tokenType) {
      return tokenType == XmlTokenType.XML_DATA_CHARACTERS
             || tokenType == XmlTokenType.XML_CDATA_START
             || tokenType == XmlTokenType.XML_CDATA_END
             || XmlTokenType.WHITESPACES.contains(tokenType);
    }
  }

  protected static class XmlAttributeValueTokenizer extends XmlTextContentTokenizer<XmlAttributeValue> {

    public XmlAttributeValueTokenizer(Splitter splitter) {
      super(splitter);
    }

    @Override
    protected boolean isContentToken(IElementType tokenType) {
      return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
             || tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER
             || tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
             || XmlTokenType.WHITESPACES.contains(tokenType);
    }

    @Override
    protected @NotNull List<@NotNull SpellcheckRange> getSpellcheckRanges(@NotNull XmlAttributeValue element) {
      TextRange range = ElementManipulators.getValueTextRange(element);
      if (range.isEmpty()) return emptyList();

      String text = ElementManipulators.getValueText(element);

      return singletonList(new SpellcheckRange(text, false, range.getStartOffset(), TextRange.allOf(text)));
    }

    @Override
    public void tokenize(@NotNull XmlAttributeValue element, @NotNull TokenConsumer consumer) {
      PsiReference[] references = element.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof XmlEnumeratedValueReference) {
          if (reference.resolve() != null) {
            // this is probably valid enumeration value from XSD/RNG schema, such as SVG
            return;
          }
        }
      }

      final String valueTextTrimmed = element.getValue().trim();
      // do not inspect colors like #00aaFF
      if (valueTextTrimmed.startsWith("#") && valueTextTrimmed.length() <= 9 && isHexString(valueTextTrimmed.substring(1))) {
        return;
      }

      // Do not inspect data URIs
      if (URLUtil.isDataUri(ElementManipulators.getValueText(element))) {
        return;
      }

      super.tokenize(element, consumer);
    }

    private static boolean isHexString(final String s) {
      for (int i = 0; i < s.length(); i++) {
        if (!StringUtil.isHexDigit(s.charAt(i))) {
          return false;
        }
      }
      return true;
    }
  }
}
