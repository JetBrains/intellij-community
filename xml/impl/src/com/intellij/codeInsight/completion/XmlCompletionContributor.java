// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.InsertHandlerDecorator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.template.emmet.completion.EmmetAbbreviationCompletionProvider;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.xml.util.XmlUtil.VALUE_ATTR_NAME;
import static com.intellij.xml.util.XmlUtil.findDescriptorFile;

/**
 * @author Dmitry Avdeev
 */
public final class XmlCompletionContributor extends CompletionContributor {
  public static final Key<Boolean> WORD_COMPLETION_COMPATIBLE = Key.create("WORD_COMPLETION_COMPATIBLE");
  public static final EntityRefInsertHandler ENTITY_INSERT_HANDLER = new EntityRefInsertHandler();

  @NonNls public static final String TAG_NAME_COMPLETION_FEATURE = "tag.name.completion";
  private static final InsertHandler<LookupElementDecorator<LookupElement>> QUOTE_EATER = new InsertHandlerDecorator<>() {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElementDecorator<LookupElement> item) {
      final char completionChar = context.getCompletionChar();
      if (completionChar == '\'' || completionChar == '\"') {
        context.setAddCompletionChar(false);
        item.getDelegate().handleInsert(context);

        final Editor editor = context.getEditor();
        final Document document = editor.getDocument();
        int tailOffset = editor.getCaretModel().getOffset();
        if (document.getTextLength() > tailOffset) {
          final char c = document.getCharsSequence().charAt(tailOffset);
          if (c == completionChar || completionChar == '\'') {
            editor.getCaretModel().moveToOffset(tailOffset + 1);
          }
        }
      }
      else {
        item.getDelegate().handleInsert(context);
      }
    }
  };

  public XmlCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inside(XmlPatterns.xmlFile()), new EmmetAbbreviationCompletionProvider());
    extend(CompletionType.BASIC, psiElement().inside(XmlPatterns.xmlFile()), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        IElementType type = position.getNode().getElementType();
        if (type != XmlTokenType.XML_DATA_CHARACTERS && type != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
          return;
        }
        if ((position.getPrevSibling() != null && position.getPrevSibling().textMatches("&")) || position.textContains('&')) {
          PrefixMatcher matcher = result.getPrefixMatcher();
          String prefix = matcher.getPrefix();
          if (prefix.startsWith("&")) {
            prefix = prefix.substring(1);
          }
          else if (prefix.contains("&")) {
            prefix = prefix.substring(prefix.indexOf("&") + 1);
          }
          matcher = matcher.cloneWithPrefix(prefix);
          if (parameters.getInvocationCount() == 0) {
            matcher = new StartOnlyMatcher(matcher);
          }
          addEntityRefCompletions(position, result.withPrefixMatcher(matcher));
        }
      }
    });
    extend(CompletionType.BASIC,
           psiElement().inside(XmlPatterns.xmlAttributeValue()),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull final CompletionResultSet result) {
               final PsiElement position = parameters.getPosition();
               if (!position.getLanguage().isKindOf(XMLLanguage.INSTANCE)) {
                 return;
               }
               final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(position, XmlAttributeValue.class, false);
               if (attributeValue == null) {
                 // we are injected, only getContext() returns attribute value
                 return;
               }

               final Set<String> usedWords = new HashSet<>();
               final Ref<Boolean> addWordVariants = Ref.create(true);
               result.runRemainingContributors(parameters, r -> {
                 if (r.getLookupElement().getUserData(WORD_COMPLETION_COMPATIBLE) == null) {
                   addWordVariants.set(false);
                 }
                 usedWords.add(r.getLookupElement().getLookupString());
                 result.passResult(r.withLookupElement(LookupElementDecorator.withInsertHandler(r.getLookupElement(), QUOTE_EATER)));
               });
               if (addWordVariants.get().booleanValue()) {
                 addWordVariants.set(attributeValue.getReferences().length == 0);
               }

               if (addWordVariants.get().booleanValue() && parameters.getInvocationCount() > 0) {
                 WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
               }
             }
           });
    extend(CompletionType.BASIC, psiElement().withElementType(XmlTokenType.XML_DATA_CHARACTERS),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               XmlTag tag = PsiTreeUtil.getParentOfType(parameters.getPosition(), XmlTag.class, false);
               if (tag != null && !hasEnumerationReference(parameters, result)) {
                 final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent(tag);
                 if (simpleContent != null) {
                   XmlUtil.processEnumerationValues(simpleContent, (element) -> {
                     String value = element.getAttributeValue(VALUE_ATTR_NAME);
                     assert value != null;
                     result.addElement(LookupElementBuilder.create(value));
                     return true;
                   });
                 }
               }
             }
           });
  }

  static boolean hasEnumerationReference(CompletionParameters parameters, CompletionResultSet result) {
    Ref<Boolean> hasRef = Ref.create(false);
    LegacyCompletionContributor.processReferences(parameters, result, (reference, resultSet) -> {
      if (reference instanceof XmlEnumeratedValueReference) {
        hasRef.set(true);
      }
    });
    return hasRef.get();
  }

  public static boolean isXmlNameCompletion(final CompletionParameters parameters) {
    final ASTNode node = parameters.getPosition().getNode();
    return node != null && node.getElementType() == XmlTokenType.XML_NAME;
  }

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
    if (result.isStopped()) {
      return;
    }

    final PsiElement element = parameters.getPosition();

    if (parameters.isExtendedCompletion()) {
      completeTagName(parameters, result);
    }

    else if (parameters.getCompletionType() == CompletionType.SMART) {
      new XmlSmartCompletionProvider().complete(parameters, result, element);
    }
  }

  static void completeTagName(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement element = parameters.getPosition();
    if (!isXmlNameCompletion(parameters)) return;
    PsiElement parent = element.getParent();
    if (!(parent instanceof XmlTag) ||
        !(parameters.getOriginalFile() instanceof XmlFile)) {
      return;
    }
    result.stopHere();
    final XmlTag tag = (XmlTag)parent;
    final String namespace = tag.getNamespace();
    final String prefix = result.getPrefixMatcher().getPrefix();
    final int pos = prefix.indexOf(':');

    final PsiReference reference = tag.getReference();
    String namespacePrefix = tag.getNamespacePrefix();

    if (reference != null && !namespace.isEmpty() && !namespacePrefix.isEmpty()) {
      // fallback to simple completion
      result.runRemainingContributors(parameters, true);
    }
    else {

      final CompletionResultSet newResult = result.withPrefixMatcher(pos >= 0 ? prefix.substring(pos + 1) : prefix);

      final XmlFile file = (XmlFile)parameters.getOriginalFile();
      final List<XmlExtension.TagInfo> names = XmlExtension.getExtension(file).getAvailableTagNames(file, tag);
      for (XmlExtension.TagInfo info : names) {
        final LookupElement item = createLookupElement(info, info.namespace, namespacePrefix.isEmpty() ? null : namespacePrefix);
        newResult.addElement(item);
      }
    }
  }

  public static LookupElement createLookupElement(XmlExtension.TagInfo tagInfo,
                                                  final String tailText, @Nullable String namespacePrefix) {
    LookupElementBuilder builder =
      LookupElementBuilder.create(tagInfo, tagInfo.name).withInsertHandler(
        new ExtendedTagInsertHandler(tagInfo.name, tagInfo.namespace, namespacePrefix));
    if (!StringUtil.isEmpty(tailText)) {
      builder = builder.withTypeText(tailText, true);
    }
    return builder;
  }

  @Override
  public String advertise(@NotNull final CompletionParameters parameters) {
    if (isXmlNameCompletion(parameters) && parameters.getCompletionType() == CompletionType.BASIC) {
      if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(TAG_NAME_COMPLETION_FEATURE, parameters.getPosition().getProject())) {
        final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION);
        return XmlBundle.message("tag.name.completion.hint", shortcut);
      }
    }
    return super.advertise(parameters);
  }

  @Override
  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final int offset = context.getStartOffset();
    final PsiFile file = context.getFile();
    final XmlAttributeValue attributeValue = PsiTreeUtil.findElementOfClassAtOffset(file, offset, XmlAttributeValue.class, true);
    if (attributeValue != null && offset == attributeValue.getTextRange().getStartOffset()) {
      context.setDummyIdentifier("");
    }

    final PsiElement at = file.findElementAt(offset);
    if (at != null && at.getNode().getElementType() == XmlTokenType.XML_NAME && at.getParent() instanceof XmlAttribute) {
      context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, at.getTextRange().getEndOffset());
    }
    if (at != null && at.getParent() instanceof XmlAttributeValue) {
      final int end = at.getParent().getTextRange().getEndOffset();
      final Document document = context.getEditor().getDocument();
      final int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
      if (lineEnd < end) {
        context.setReplacementOffset(lineEnd);
      }
    }
  }

  private static void addEntityRefCompletions(PsiElement context, CompletionResultSet resultSet) {
    XmlFile containingFile = ObjectUtils.tryCast(context.getContainingFile(), XmlFile.class);
    if (containingFile == null) {
      return;
    }
    List<XmlFile> descriptorFiles = XmlExtension.getExtension(containingFile).getCharEntitiesDTDs(containingFile);
    final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
    if (tag != null && descriptorFiles.isEmpty()) {
      descriptorFiles = ContainerUtil.packNullables(findDescriptorFile(tag, containingFile));
    }

    final boolean acceptSystemEntities = containingFile.getFileType() == XmlFileType.INSTANCE;
    final PsiElementProcessor<PsiElement> processor = new PsiElementProcessor<>() {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        if (element instanceof XmlEntityDecl) {
          final XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)element;
          if (xmlEntityDecl.isInternalReference() || acceptSystemEntities) {
            final LookupElementBuilder _item = buildEntityLookupItem(xmlEntityDecl);
            if (_item != null) {
              resultSet.addElement(_item);
              resultSet.stopHere();
            }
          }
        }
        return true;
      }
    };

    for (XmlFile descriptorFile: descriptorFiles) {
      XmlUtil.processXmlElements(descriptorFile, processor, true);
    }

    final XmlDocument document = containingFile.getDocument();
    if (acceptSystemEntities && document != null) {
      final XmlProlog element = document.getProlog();
      if (element != null) XmlUtil.processXmlElements(element, processor, true);
    }
  }

  @Nullable
  private static LookupElementBuilder buildEntityLookupItem(@NotNull final XmlEntityDecl decl) {
    final String name = decl.getName();
    if (name == null) {
      return null;
    }

    final LookupElementBuilder result = LookupElementBuilder.create(name).withInsertHandler(ENTITY_INSERT_HANDLER);
    final XmlAttributeValue value = decl.getValueElement();
    final ASTNode node = value.getNode();
    if (node != null) {
      final ASTNode[] nodes = node.getChildren(TokenSet.create(XmlTokenType.XML_CHAR_ENTITY_REF));
      if (nodes.length == 1) {
        final String valueText = nodes[0].getText();
        final int i = valueText.indexOf('#');
        if (i > 0) {
          String s = valueText.substring(i + 1);
          s = StringUtil.trimEnd(s, ";");

          try {
            final char unicodeChar = (char)Integer.valueOf(s).intValue();
            return result.withTypeText(String.valueOf(unicodeChar)).withLookupString(String.valueOf(unicodeChar));
          }
          catch (NumberFormatException e) {
            return result;
          }
        }
      }
    }

    return result;
  }

  private static class EntityRefInsertHandler extends BasicInsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      super.handleInsert(context, item);
      context.setAddCompletionChar(false);
      Editor editor = context.getEditor();
      final CaretModel caretModel = editor.getCaretModel();
      int caretOffset = caretModel.getOffset();
      if (!CharArrayUtil.regionMatches(editor.getDocument().getCharsSequence(), caretOffset, ";")) {
        editor.getDocument().insertString(caretOffset, ";");
      }
      caretModel.moveToOffset(caretOffset + 1);
    }
  }
}
