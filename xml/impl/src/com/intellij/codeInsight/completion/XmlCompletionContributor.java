package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
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
  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(CompletionType.CLASS_NAME, PlatformPatterns.psiElement(XmlTokenType.XML_NAME)).
      dependingOn(JavaCompletionContributor.JAVA_LEGACY).
      withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        result.stopHere();
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final PsiElement element = parameters.getPosition();
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
          new XmlCompletionData().completeReference(reference, set, element, result.getPrefixMatcher(), context.file, context.getStartOffset());
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

}
