// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.util.documentation.MimeTypeDictionary;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.completion.CompletionService.getCompletionService;
import static com.intellij.html.impl.util.MicrodataUtil.*;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.util.ObjectUtils.doIfNotNull;

public class HtmlCompletionContributor extends CompletionContributor implements DumbAware {

  public static final String[] TARGET = {"_blank", "_top", "_self", "_parent"};
  public static final String[] ENCTYPE = {"multipart/form-data", "application/x-www-form-urlencoded"};
  public static final String[] REL = {"alternate", "author", "bookmark", "help", "icon", "license", "next", "nofollow",
    "noreferrer", "noopener", "prefetch", "prev", "search", "stylesheet", "tag", "start", "contents", "index",
    "glossary", "copyright", "chapter", "section", "subsection", "appendix", "script", "import",
    "apple-touch-icon", "apple-touch-icon-precomposed", "apple-touch-startup-image"};
  public static final String[] MEDIA = {"all", "braille", "embossed", "handheld", "print", "projection", "screen", "speech", "tty", "tv"};
  public static final String[] LANGUAGE =
    {"JavaScript", "VBScript", "JScript", "JavaScript1.2", "JavaScript1.3", "JavaScript1.4", "JavaScript1.5"};
  public static final String[] TYPE = {"text/css", "text/html", "text/plain", "text/xml"};
  public static final String[] SANDBOX = {"allow-forms", "allow-pointer-lock", "allow-popups", "allow-same-origin",
    "allow-scripts", "allow-top-navigation"};
  public static final String[] LANG =
    {"aa", "ab", "ae", "af", "ak", "am", "an", "ar", "as", "av", "ay", "az", "ba", "be", "bg", "bh", "bi", "bm", "bn", "bo", "br", "bs",
      "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy", "da", "de", "dv", "dz", "ee", "el", "en", "eo", "es", "et", "eu", "fa", "ff",
      "fi", "fj", "fo", "fr", "fy", "ga", "gd", "gl", "gn", "gu", "gv", "ha", "he", "hi", "ho", "hr", "ht", "hu", "hy", "hz", "ia", "id",
      "ie", "ig", "ii", "ik", "io", "is", "it", "iu", "ja", "jv", "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko", "kr", "ks", "ku",
      "kv", "kw", "ky", "la", "lb", "lg", "li", "ln", "lo", "lt", "lu", "lv", "mg", "mh", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my",
      "na", "nb", "nd", "ne", "ng", "nl", "nn", "no", "nr", "nv", "ny", "oc", "oj", "om", "or", "os", "pa", "pi", "pl", "ps", "pt", "qu",
      "rm", "rn", "ro", "ru", "rw", "sa", "sc", "sd", "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr", "ss", "st", "su", "sv",
      "sw", "ta", "te", "tg", "th", "ti", "tk", "tl", "tn", "to", "tr", "ts", "tt", "tw", "ty", "ug", "uk", "ur", "uz", "ve", "vi", "vo",
      "wa", "wo", "xh", "yi", "yo", "za", "zh", "zu",};

  public HtmlCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inside(XmlPatterns.xmlAttributeValue()), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (!hasHtmlAttributesCompletion(position)) {
          return;
        }
        final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(position, XmlAttributeValue.class, false);
        if (attributeValue != null && attributeValue.getParent() instanceof XmlAttribute) {
          for (String element : addSpecificCompletions((XmlAttribute)attributeValue.getParent())) {
            result.addElement(LookupElementBuilder.create(element));
          }
        }
      }
    });
    extend(CompletionType.BASIC, getHtmlElementInTextPattern(),
           new HtmlElementInTextCompletionProvider());
  }

  public static boolean hasHtmlAttributesCompletion(PsiElement position) {
    if (PsiTreeUtil.getParentOfType(position, HtmlTag.class, false) != null) {
      return true;
    }
    XmlTag xmlTag = PsiTreeUtil.getParentOfType(position, XmlTag.class, false);
    return xmlTag != null && xmlTag.getLanguage() == XHTMLLanguage.INSTANCE;
  }

  @NonNls
  public static String @NotNull [] addSpecificCompletions(final XmlAttribute attribute) {
    @NonNls String name = attribute.getName();
    final XmlTag tag = attribute.getParent();
    if (tag == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;

    @NonNls String tagName = tag.getName();
    if (tag.getDescriptor() instanceof HtmlElementDescriptorImpl) {
      name = StringUtil.toLowerCase(name);
      tagName = StringUtil.toLowerCase(tagName);
    }

    final String namespace = tag.getNamespace();
    if (XmlUtil.XHTML_URI.equals(namespace) || XmlUtil.HTML_URI.equals(namespace)) {

      if ("target".equals(name) || "formtarget".equals(name)) {
        return TARGET;
      }
      else if (("lang".equals(name) || "xml:lang".equals(name)) && tagName.equalsIgnoreCase("html")) {
        return LANG;
      }
      else if ("enctype".equals(name)) {
        return ENCTYPE;
      }
      else if ("rel".equals(name) || "rev".equals(name)) {
        return REL;
      }
      else if ("media".equals(name)) {
        return MEDIA;
      }
      else if ("language".equals(name)) {
        return LANGUAGE;
      }
      else if ("sandbox".equals(name)) {
        return SANDBOX;
      }
      else if ("type".equals(name) && "link".equals(tagName)) {
        return TYPE;
      }
      else if ("http-equiv".equals(name) && "meta".equals(tagName)) {
        return HtmlUtil.RFC2616_HEADERS;
      }
      else if ("content".equals(name) && "meta".equals(tagName) && tag.getAttribute("name") == null) {
        return MimeTypeDictionary.HTML_CONTENT_TYPES;
      }
      else if ("accept".equals(name) && "input".equals(tagName)) {
        return MimeTypeDictionary.HTML_CONTENT_TYPES;
      }
      else if ("accept-charset".equals(name) || "charset".equals(name)) {
        Charset[] charSets = CharsetToolkit.getAvailableCharsets();
        String[] names = new String[charSets.length];
        for (int i = 0; i < names.length; i++) {
          names[i] = charSets[i].toString();
        }
        return names;
      }
      else if ("itemprop".equals(name) && !DumbService.isDumb(attribute.getProject())) {
        XmlTag scopeTag = findScopeTag(tag);
        return scopeTag != null ? findItemProperties(scopeTag) : ArrayUtilRt.EMPTY_STRING_ARRAY;
      }
    }

    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  private static String[] findItemProperties(@NotNull XmlTag tag) {
    final XmlAttribute typeAttribute = tag.getAttribute(ITEM_TYPE);
    if (typeAttribute != null) {
      final XmlAttributeValue valueElement = typeAttribute.getValueElement();
      final PsiReference[] references = valueElement != null ? valueElement.getReferences() : PsiReference.EMPTY_ARRAY;
      List<String> result = new ArrayList<>();
      for (PsiReference reference : references) {
        final PsiElement target = reference != null ? reference.resolve() : null;
        if (target instanceof PsiFile) {
          result.addAll(extractProperties((PsiFile)target, StringUtil.unquoteString(reference.getCanonicalText())));
        }
      }
      return ArrayUtilRt.toStringArray(result);
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Contract("null->false")
  private static boolean shouldTryDeselectingFirstPopupItem(@Nullable Lookup lookup) {
    PsiFile file = doIfNotNull(lookup, Lookup::getPsiFile);
    if (!(file instanceof HtmlFileImpl)) {
      return false;
    }
    PsiElement element = lookup.getPsiElement();
    if (element == null) {
      return false;
    }
    IElementType elementType = element.getNode().getElementType();

    if ((elementType == XmlTokenType.XML_DATA_CHARACTERS
         || element.getNode().getElementType() == XmlTokenType.XML_WHITE_SPACE)
        && (element.getParent() instanceof XmlText || element.getParent() instanceof XmlDocument)
    ) {
      return !element.getText().endsWith("<");
    }

    if (elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      return element.getText().contains("&");
    }
    return false;
  }

  @ApiStatus.Internal
  public static ElementPattern<PsiElement> getHtmlElementInTextPattern() {
    return psiElement(XmlTokenType.XML_DATA_CHARACTERS)
      .withParent(StandardPatterns.or(psiElement(XmlText.class), psiElement(XmlDocument.class)))
      .inFile(PlatformPatterns.psiFile(HtmlFileImpl.class));
  }

  @ApiStatus.Internal
  public static CompletionResultSet patchResultSetForHtmlElementInTextCompletion(@NotNull CompletionResultSet result,
                                                                                 @NotNull CompletionParameters parameters) {
    // We want live templates to be mixed with tags and other contributions
    result = result.withRelevanceSorter(withoutLiveTemplatesWeigher(null, parameters, result.getPrefixMatcher()));
    if (parameters.getInvocationCount() == 0) {
      // We only want results which start with the prefix first char
      result = result.withPrefixMatcher(new StartOnlyMatcher(result.getPrefixMatcher()));
    }
    return result;
  }

  @ApiStatus.Internal
  public static boolean canProvideHtmlElementInTextCompletion(@NotNull CompletionParameters parameters) {
    // Do not provide HTML text completions in multi view files like PHP
    return ContainerUtil.and(parameters.getOriginalFile().getViewProvider().getAllFiles(), f -> f instanceof HtmlFileImpl);
  }

  private static CompletionSorter withoutLiveTemplatesWeigher(@Nullable CompletionSorter sorter,
                                                              @NotNull CompletionParameters parameters,
                                                              @NotNull PrefixMatcher prefixMatcher) {
    if (sorter == null) {
      sorter = getCompletionService().defaultSorter(parameters, prefixMatcher);
    }
    return ((CompletionSorterImpl)sorter).withoutClassifiers(f -> "templates".equals(f.getId()));
  }

  private static class HtmlElementInTextCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      if (!canProvideHtmlElementInTextCompletion(parameters)) return;
      // We cannot modify the file in injections - disable the feature
      if (parameters.getPosition().getContainingFile().isPhysical()) return;
      PsiFile completionFile = parameters.getPosition().getContainingFile();
      int offset = parameters.getOffset();
      var offsets = new OffsetsInFile(completionFile);
      offsets.getOffsets().addOffset(CompletionInitializationContext.START_OFFSET, offset);
      offsets = offsets.copyWithReplacement(offset, offset, "<");
      PsiElement tag = doIfNotNull(offsets.getFile().findElementAt(offset + 1), PsiElement::getParent);
      if (tag instanceof XmlTag) {
        CompletionResultSet patchedResultSet = patchResultSetForHtmlElementInTextCompletion(result, parameters);
        for (LookupElement variant : TagNameReferenceCompletionProvider.getTagNameVariants((XmlTag)tag, "")) {
          LookupElement decorated = new LookupElementDecorator<>(variant) {

            @Override
            public @NotNull String getLookupString() {
              return "<" + super.getLookupString();
            }

            @Override
            public void renderElement(LookupElementPresentation presentation) {
              super.renderElement(presentation);
              presentation.setItemText("<" + presentation.getItemText());
            }
          };
          if (variant instanceof PrioritizedLookupElement) {
            decorated = PrioritizedLookupElement.withGrouping(
              PrioritizedLookupElement.withExplicitProximity(
                PrioritizedLookupElement.withPriority(decorated,
                                                      ((PrioritizedLookupElement<?>)variant).getPriority()),
                ((PrioritizedLookupElement<?>)variant).getExplicitProximity()),
              ((PrioritizedLookupElement<?>)variant).getGrouping());
          }
          patchedResultSet.consume(decorated);
        }
        patchedResultSet.runRemainingContributors(parameters, r -> {
          patchedResultSet.withPrefixMatcher(r.getPrefixMatcher())
            .withRelevanceSorter(withoutLiveTemplatesWeigher(r.getSorter(), parameters, r.getPrefixMatcher()))
            .addElement(r.getLookupElement());
        });
      }
      if (result.getPrefixMatcher().getPrefix().length() < 2) {
        result.restartCompletionOnAnyPrefixChange();
      }
    }
  }

  public static class HtmlElementInTextLookupManagerListener implements LookupManagerListener {

    @Override
    public void activeLookupChanged(@Nullable Lookup oldLookup,
                                    @Nullable Lookup newLookup) {
      if (newLookup instanceof LookupImpl && shouldTryDeselectingFirstPopupItem(newLookup)) {
        LookupImpl lookup = (LookupImpl)newLookup;
        lookup.setPrefixChangeListener(new PrefixChangeListener() {
          @Override
          public void afterAppend(char c) {
            // Select first item when two chars are typed after '&'
            if (lookup.getCurrentItemOrEmpty() == null && hasTwoCharsAfterAmp(lookup)) {
              lookup.setSelectedIndex(0);
            }
          }
        });
        lookup.addLookupListener(new LookupListener() {
          @Override
          public void uiRefreshed() {
            var currentCompletion = getCompletionService().getCurrentCompletion();
            if (currentCompletion != null
                && currentCompletion.isAutopopupCompletion()
                && !lookup.isSelectionTouched()
                && !hasTwoCharsAfterAmp(lookup)) {
              // Deselect topmost item
              lookup.getList().setSelectedValue(null, false);
              ListSelectionModel selectionModel = lookup.getList().getSelectionModel();
              selectionModel.setAnchorSelectionIndex(-1);
              selectionModel.setLeadSelectionIndex(-1);
            }
          }
        });
      }
    }

    private static boolean hasTwoCharsAfterAmp(LookupImpl lookup) {
      int start = Math.max(lookup.getLookupStart() - 1, 0);
      int end = lookup.getEditor().getCaretModel().getOffset();
      if (end - start < 3) return false;
      Document doc = lookup.getEditor().getDocument();
      return doc.getTextLength() > start && doc.getCharsSequence().charAt(start) == '&';
    }
  }
}
