/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.xml;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementDescriptorAwareAboutChildren;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TagNameReference implements PsiReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.TagNameReference");

  protected final boolean myStartTagFlag;
  private final ASTNode myNameElement;

  public TagNameReference(ASTNode nameElement, boolean startTagFlag) {
    myStartTagFlag = startTagFlag;
    myNameElement = nameElement;
  }

  public PsiElement getElement() {
    PsiElement element = myNameElement.getPsi();
    final PsiElement parent = element.getParent();
    return parent instanceof XmlTag ? parent : element;
  }

  @Nullable
  protected XmlTag getTagElement() {
    final PsiElement element = getElement();
    if(element == myNameElement.getPsi()) return null;
    return (XmlTag)element;
  }

  public TextRange getRangeInElement() {
    final ASTNode nameElement = getNameElement();
    if (nameElement == null){
      return TextRange.EMPTY_RANGE;
    }

    int colon = nameElement.getText().indexOf(':') + 1;
    if (myStartTagFlag) {
      final int parentOffset = ((TreeElement)nameElement).getStartOffsetInParent();
      return new TextRange(parentOffset + colon, parentOffset + nameElement.getTextLength());
    }
    else {
      final PsiElement element = getElement();
      if (element == myNameElement) return new TextRange(colon, myNameElement.getTextLength());

      final int elementLength = element.getTextLength();
      int diffFromEnd = 0;

      for(ASTNode node = element.getNode().getLastChildNode(); node != nameElement && node != null; node = node.getTreePrev()) {
        diffFromEnd += node.getTextLength();
      }

      final int nameEnd = elementLength - diffFromEnd;
      return new TextRange(nameEnd - nameElement.getTextLength() + colon, nameEnd);
    }
  }

  private ASTNode getNameElement() {
    return myNameElement;
  }

  public PsiElement resolve() {
    final XmlTag tag = getTagElement();
    final XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor():null;

    LOG.debug("Descriptor for tag " +
              (tag != null ? tag.getName() : "NULL") +
              " is " +
              (descriptor != null ? (descriptor.toString() + ": " + descriptor.getClass().getCanonicalName()) : "NULL"));

    if (descriptor != null){
      return descriptor instanceof AnyXmlElementDescriptor ? tag : descriptor.getDeclaration();
    }
    return null;
  }

  @NotNull
  public String getCanonicalText() {
    return getNameElement().getText();
  }

  @Nullable
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XmlTag element = getTagElement();
    if (element == null || !myStartTagFlag) return element;

    if (newElementName.indexOf(':') == -1) {
      final String namespacePrefix = element.getNamespacePrefix();
      final int index = newElementName.lastIndexOf('.');

      if (index != -1) {
        final PsiElement psiElement = resolve();
        
        if (psiElement instanceof PsiFile || (psiElement != null && psiElement.isEquivalentTo(psiElement.getContainingFile()))) {
          newElementName = newElementName.substring(0, index);
        }
      }
      newElementName = prependNamespacePrefix(newElementName, namespacePrefix);
    }
    element.setName(newElementName);
    return element;
  }

  private static String prependNamespacePrefix(String newElementName, String namespacePrefix) {
    newElementName = (namespacePrefix.length() > 0 ? namespacePrefix + ":":namespacePrefix) + newElementName;
    return newElementName;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    PsiMetaData metaData = null;

    if (element instanceof PsiMetaOwner){
      final PsiMetaOwner owner = (PsiMetaOwner)element;
      metaData = owner.getMetaData();

      if (metaData instanceof XmlElementDescriptor){
        return getTagElement().setName(metaData.getName(getElement())); // TODO: need to evaluate new ns prefix
      }
    } else if (element instanceof PsiFile) {
      final XmlTag tagElement = getTagElement();
      if (tagElement == null || !myStartTagFlag) return tagElement;
      String newElementName = ((PsiFile)element).getName();
      final int index = newElementName.lastIndexOf('.');

      // TODO: need to evaluate new ns prefix
      newElementName = prependNamespacePrefix(newElementName.substring(0, index), tagElement.getNamespacePrefix());

      return getTagElement().setName(newElementName);
    }

    throw new IncorrectOperationException("Cant bind to not a xml element definition!"+element+","+metaData);
  }

  public boolean isReferenceTo(PsiElement element) {
    return getElement().getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public LookupElement[] getVariants(){
    final PsiElement element = getElement();
    if(!myStartTagFlag){
      if (element instanceof XmlTag) {
        return new LookupElement[]{createClosingTagLookupElement((XmlTag)element, false)};
      }
      return LookupElement.EMPTY_ARRAY;
    }
    return getTagNameVariants((XmlTag)element, ((XmlTag)element).getNamespacePrefix());
  }

  public LookupElement createClosingTagLookupElement(XmlTag tag, boolean includePrefix) {
    LookupElementBuilder builder = LookupElementBuilder.create(includePrefix || !myNameElement.getText().contains(":") ? tag.getName() : tag.getLocalName());
    return TailTypeDecorator.withTail(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder),
                                      TailType.createSimpleTailType('>'));
  }

  public static LookupElement[] getTagNameVariants(final @NotNull XmlTag tag, final String prefix) {
    final List<String> namespaces;
    if (prefix.isEmpty()) {
      namespaces = new ArrayList<String>(Arrays.asList(tag.knownNamespaces()));
      namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
    }
    else {
      namespaces = new ArrayList<String>(Collections.singletonList(tag.getNamespace()));
    }
    List<String> nsInfo = new ArrayList<String>();
    final String[] variants = getTagNameVariants(tag, namespaces, nsInfo);
    List<LookupElement> elements = new ArrayList<LookupElement>(variants.length);
    for (int i = 0, variantsLength = variants.length; i < variantsLength; i++) {
      String qname = variants[i];
      if (!prefix.isEmpty() && qname.startsWith(prefix + ":")) {
        qname = qname.substring(prefix.length() + 1);
      }
      LookupElementBuilder lookupElement = LookupElementBuilder.create(qname);
      final int separator = qname.indexOf(':');
      if (separator > 0) {
        lookupElement = lookupElement.withLookupString(qname.substring(separator + 1));
      }
      String ns = nsInfo.get(i);
      if (StringUtil.isNotEmpty(ns)) {
        lookupElement = lookupElement.withTypeText(ns, true);
      }
      elements.add(lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE));
    }
    return elements.toArray(new LookupElement[elements.size()]);
  }

  public static String[] getTagNameVariants(final XmlTag element,
                                            final Collection<String> namespaces,
                                            @Nullable List<String> nsInfo) {

    final List<String> variants = getTagNameVariants(element, namespaces, nsInfo, new Function<XmlElementDescriptor, String>() {
      @Override
      public String fun(XmlElementDescriptor descriptor) {
        return descriptor.getName(element);
      }
    });
    return ArrayUtil.toStringArray(variants);
  }
  
  public static <T> List<T> getTagNameVariants(final XmlTag element,
                                               final Collection<String> namespaces,
                                               @Nullable List<String> nsInfo,
                                               final Function<XmlElementDescriptor, T> f) {

    XmlElementDescriptor elementDescriptor = null;
    String elementNamespace = null;

    final Map<String, XmlElementDescriptor> descriptorsMap = new HashMap<String, XmlElementDescriptor>();
    PsiElement context = element.getParent();
    PsiElement curElement = element.getParent();

    {
      while(curElement instanceof XmlTag){
        final XmlTag declarationTag = (XmlTag)curElement;
        final String namespace = declarationTag.getNamespace();

        if(!descriptorsMap.containsKey(namespace)) {
          final XmlElementDescriptor descriptor = declarationTag.getDescriptor();

          if(descriptor != null) {
            descriptorsMap.put(namespace, descriptor);
            if(elementDescriptor == null) {
              elementDescriptor = descriptor;
              elementNamespace = namespace;
            }
          }
        }
        curElement = curElement.getContext();
      }
    }

    final Set<XmlNSDescriptor> visited = new HashSet<XmlNSDescriptor>();
    final XmlExtension extension = XmlExtension.getExtension(element.getContainingFile());
    final ArrayList<XmlElementDescriptor> variants = new ArrayList<XmlElementDescriptor>();
    for (final String namespace: namespaces) {
      final int initialSize = variants.size();
      processVariantsInNamespace(namespace, element, variants, elementDescriptor, elementNamespace, descriptorsMap, visited,
                                 context instanceof XmlTag ? (XmlTag)context : element, extension);
      if (nsInfo != null) {
        for (int i = initialSize; i < variants.size(); i++) {
          nsInfo.add(namespace);
        }
      }
    }

    final boolean hasPrefix = StringUtil.isNotEmpty(element.getNamespacePrefix());
    return ContainerUtil.mapNotNull(variants, new NullableFunction<XmlElementDescriptor, T>() {
      public T fun(XmlElementDescriptor descriptor) {
        if (descriptor instanceof AnyXmlElementDescriptor) {
          return null;
        }
        else if (hasPrefix && descriptor instanceof XmlElementDescriptorImpl && 
                 !namespaces.contains(((XmlElementDescriptorImpl)descriptor).getNamespace())) {
          return null;
        }

        return f.fun(descriptor);
      }
    });
  }

  private static void processVariantsInNamespace(final String namespace,
                                                 final XmlTag element,
                                                 final List<XmlElementDescriptor> variants,
                                                 final XmlElementDescriptor elementDescriptor,
                                                 final String elementNamespace,
                                                 final Map<String, XmlElementDescriptor> descriptorsMap,
                                                 final Set<XmlNSDescriptor> visited,
                                                 XmlTag parent,
                                                 final XmlExtension extension) {
    if(descriptorsMap.containsKey(namespace)){
        final XmlElementDescriptor descriptor = descriptorsMap.get(namespace);

      if(isAcceptableNs(element, elementDescriptor, elementNamespace, namespace)){
        for(XmlElementDescriptor containedDescriptor: descriptor.getElementsDescriptors(parent)) {
          if (containedDescriptor != null) variants.add(containedDescriptor);
        }
      }

      if (element instanceof HtmlTag) {
        HtmlUtil.addHtmlSpecificCompletions(descriptor, element, variants);
      }
      visited.add(descriptor.getNSDescriptor());
    }
    else{
      // Don't use default namespace in case there are other namespaces in scope
      // If there are tags from default namespace they will be handeled via
      // their element descriptors (prev if section)
      if (namespace == null) return;
      if(namespace.length() == 0 && !visited.isEmpty()) return;

      XmlNSDescriptor nsDescriptor = getDescriptor(element, namespace, true, extension);
      if (nsDescriptor == null) {
        if(!descriptorsMap.isEmpty()) return;
        nsDescriptor = getDescriptor(element, namespace, false, extension);
      }

      if(nsDescriptor != null && !visited.contains(nsDescriptor) &&
         isAcceptableNs(element, elementDescriptor, elementNamespace, namespace)
        ){
        visited.add(nsDescriptor);
        final XmlElementDescriptor[] rootElementsDescriptors =
          nsDescriptor.getRootElementsDescriptors(PsiTreeUtil.getParentOfType(element, XmlDocument.class));

        final XmlTag parentTag = extension.getParentTagForNamespace(element, nsDescriptor);
        final XmlElementDescriptor parentDescriptor;
        if (parentTag == element.getParentTag()) {
          parentDescriptor = elementDescriptor;
        }
        else {
          assert parentTag != null;
          parentDescriptor = parentTag.getDescriptor();
        }

        for(XmlElementDescriptor candidateDescriptor: rootElementsDescriptors) {
          if (candidateDescriptor != null &&
              couldContainDescriptor(parentTag, parentDescriptor, candidateDescriptor, namespace, false)) {
            variants.add(candidateDescriptor);
          }
        }
      }
    }
  }

  private static XmlNSDescriptor getDescriptor(final XmlTag element, final String namespace, final boolean strict,
                                               final XmlExtension extension) {
    return extension.getNSDescriptor(element, namespace, strict);
  }

  public static boolean couldContain(XmlTag parent, XmlTag child) {
    return couldContainDescriptor(parent, parent.getDescriptor(), child.getDescriptor(), child.getNamespace(), true);
  }

  private static boolean couldContainDescriptor(final XmlTag parentTag,
                                                final XmlElementDescriptor parentDescriptor,
                                                final XmlElementDescriptor childDescriptor,
                                                String childNamespace, boolean strict) {

    if (XmlUtil.nsFromTemplateFramework(childNamespace)) return true;
    if (parentTag == null) return true;
    if (parentDescriptor == null) return false;
    final XmlTag childTag = parentTag.createChildTag(childDescriptor.getName(), childNamespace, null, false);
    childTag.putUserData(XmlElement.INCLUDING_ELEMENT, parentTag);
    XmlElementDescriptor descriptor = parentDescriptor.getElementDescriptor(childTag, parentTag);
    return descriptor != null && (!strict || !(descriptor instanceof AnyXmlElementDescriptor));
  }

  private static boolean isAcceptableNs(final XmlTag element, final XmlElementDescriptor elementDescriptor,
                                        final String elementNamespace,
                                        final String namespace) {
    return !(elementDescriptor instanceof XmlElementDescriptorAwareAboutChildren) ||
        elementNamespace == null ||
        elementNamespace.equals(namespace) ||
         ((XmlElementDescriptorAwareAboutChildren)elementDescriptor).allowElementsFromNamespace(namespace, element.getParentTag());
  }

  public boolean isSoft() {
    return false;
  }

  @Nullable
  static TagNameReference createTagNameReference(XmlElement element, @NotNull ASTNode nameElement, boolean startTagFlag) {
    final XmlExtension extension = XmlExtension.getExtensionByElement(element);
    return extension == null ? null : extension.createTagNameReference(nameElement, startTagFlag);
  }
}
