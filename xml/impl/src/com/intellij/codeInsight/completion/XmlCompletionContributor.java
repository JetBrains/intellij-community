package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.AsyncConsumer;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XmlCompletionContributor extends CompletionContributor{
  private static final Key<XmlAttributeValue> XML_ATTRIBUTE_VALUE = Key.create("XML_ATTRIBUTE_VALUE");
  private static final TailType EAT_QUOTE_TAIL_TYPE = new TailType() {
    public int processTail(final Editor editor, final int tailOffset) {
      return XmlCompletionData.eatClosingQuote(UNKNOWN, editor);
    }
  };

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(XmlPatterns.xmlAttributeValue().save(XML_ATTRIBUTE_VALUE)),
                     new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet<LookupElement> result) {
        for (final PsiReference reference : context.get(XML_ATTRIBUTE_VALUE).getReferences()) {
          if (reference instanceof FileReference) {
            result.setSuccessorFilter(new AsyncConsumer<LookupElement>() {
              public void consume(final LookupElement lookupElement) {
                if (lookupElement.getTailType() == TailType.UNKNOWN) {
                  lookupElement.setTailType(TailType.NONE);
                }
                result.addElement(lookupElement);
              }
            });
            return;
          }
        }
      }
    });

    registrar.extend(CompletionType.CLASS_NAME, PlatformPatterns.psiElement().withParent(XmlPatterns.xmlAttributeValue()),
                     new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet<LookupElement> result) {
        result.setSuccessorFilter(new AsyncConsumer<LookupElement>() {
          public void consume(final LookupElement lookupElement) {
            if (lookupElement.getTailType() == TailType.UNKNOWN) {
              lookupElement.setTailType(EAT_QUOTE_TAIL_TYPE);
            }
            result.addElement(lookupElement);
          }
        });

      }
    });

    registrar.extend(CompletionType.CLASS_NAME, PlatformPatterns.psiElement(XmlTokenType.XML_NAME), new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        result.stopHere();
        final PsiElement element = parameters.getPosition();
        if (!(element.getParent() instanceof XmlTag)) {
          return;
        }
        final XmlTag parent = (XmlTag)element.getParent();
        final String namespace = parent.getNamespace();
        final XmlElementDescriptor parentDescriptor =
            ApplicationManager.getApplication().runReadAction(new Computable<XmlElementDescriptor>() {
              public XmlElementDescriptor compute() {
                return parent.getDescriptor();
              }
            });
        final String prefix = result.getPrefixMatcher().getPrefix();
        final int pos = prefix.indexOf(':');
        final String namespacePrefix = pos > 0 ? prefix.substring(0, pos) : null;

        final PsiReference reference = parent.getReference();
        if (reference != null && namespace.length() > 0 && parentDescriptor != null && !(parentDescriptor instanceof AnyXmlElementDescriptor)) {
          final Set<LookupItem> set = new HashSet<LookupItem>();
          new XmlCompletionData().completeReference(reference, set, element, result.getPrefixMatcher(), parameters.getOriginalFile(), parameters.getOffset());
          for (final LookupItem item : set) {
            result.addElement(item);
          }
        } else {

          result.setPrefixMatcher(pos >= 0 ? prefix.substring(pos + 1) : prefix);

          final XmlFile file = (XmlFile)parameters.getOriginalFile();
          final List<Pair<String,String>> names =
              ApplicationManager.getApplication().runReadAction(new Computable<List<Pair<String, String>>>() {
                public List<Pair<String, String>> compute() {
                  return XmlExtension.getExtension(file).getAvailableTagNames(file, parent);
                }
              });
          for (Pair<String, String> pair : names) {
            final String name = pair.getFirst();
            final String ns = pair.getSecond();
            final LookupItem item = new LookupItem<String>(name, name);
            final XmlTagInsertHandler insertHandler = new ExtendedTagInsertHandler(name, ns, namespacePrefix);
            item.setInsertHandler(insertHandler);
            if (!StringUtil.isEmpty(ns)) {
              item.setAttribute(LookupItem.TAIL_TEXT_ATTR, " (" + ns + ")");
              item.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
            }
            result.addElement(item);
          }
        }
      }
    });
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final int offset = context.getStartOffset();
    final XmlAttributeValue attributeValue = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, XmlAttributeValue.class, true);
    if (attributeValue != null && offset == attributeValue.getTextRange().getStartOffset()) {
      context.setDummyIdentifier("");
    }
  }
}
