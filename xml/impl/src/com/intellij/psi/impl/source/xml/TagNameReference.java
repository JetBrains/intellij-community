package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
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
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
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
  protected final boolean myStartTagFlag;
  private final ASTNode myNameElement;

  public TagNameReference(ASTNode nameElement, boolean startTagFlag) {
    myStartTagFlag = startTagFlag;
    myNameElement = nameElement;
  }

  public PsiElement getElement() {
    final XmlTag tag = PsiTreeUtil.getParentOfType(myNameElement.getPsi(), XmlTag.class);
    return tag != null ? tag:myNameElement.getPsi();
  }

  protected XmlTag getTagElement() {
    final PsiElement element = getElement();
    if(element == myNameElement.getPsi()) return null;
    return (XmlTag)element;
  }

  public TextRange getRangeInElement() {
    final ASTNode nameElement = getNameElement();
    if (nameElement == null){
      return new TextRange(0, 0);
    }

    if (myStartTagFlag) {
      final int parentOffset = ((TreeElement)nameElement).getStartOffsetInParent();
      return new TextRange(parentOffset, parentOffset + nameElement.getTextLength());
    } else {
      final PsiElement element = getElement();
      if (element == myNameElement) return new TextRange(0,myNameElement.getTextLength());

      final int elementLength = element.getTextLength();
      int diffFromEnd = 0;

      for(ASTNode node = element.getNode().getLastChildNode(); node != nameElement && node != null; node = node.getTreePrev()) {
        diffFromEnd += node.getTextLength();
      }

      final int nameEnd = elementLength - diffFromEnd;
      return new TextRange(nameEnd - nameElement.getTextLength(), nameEnd);
    }
  }

  private ASTNode getNameElement() {
    return myNameElement;
  }

  public PsiElement resolve() {
    final XmlTag tag = getTagElement();
    final XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor():null;

    if (descriptor != null){
      return descriptor instanceof AnyXmlElementDescriptor ? tag : descriptor.getDeclaration();
    }
    return null;
  }

  public String getCanonicalText() {
    return getNameElement().getText();
  }

  @Nullable
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XmlTag element = getTagElement();
    if (element == null) return null;

    if (newElementName.indexOf(':') == -1) {
      final String namespacePrefix = element.getNamespacePrefix();
      if (namespacePrefix.length() > 0) {
        final PsiElement psiElement = resolve();
        final int index = newElementName.lastIndexOf('.');
        if (psiElement instanceof PsiFile && index > 0) {
          newElementName = newElementName.substring(0, index);
        }
        newElementName = namespacePrefix + ":" + newElementName;
      }
    }
    element.setName(newElementName);
    return element;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    PsiMetaData metaData = null;

    if (element instanceof PsiMetaOwner){
      final PsiMetaOwner owner = (PsiMetaOwner)element;
      metaData = owner.getMetaData();

      if (metaData instanceof XmlElementDescriptor){
        getTagElement().setName(metaData.getName(getElement()));
      }
    } 

    throw new IncorrectOperationException("Cant bind to not a xml element definition!"+element+","+metaData);
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public String[] getVariants(){

    final PsiElement element = getElement();
    if(!myStartTagFlag){
      if (element instanceof XmlTag) {
        return new String[]{((XmlTag)element).getName()};
      } else {
        return new String[0];
      }
    }
    return getTagNameVariants((XmlTag)element);
  }


  public static String[] getTagNameVariants(final XmlTag element) {
    final ArrayList<String> namespaces = new ArrayList<String>(Arrays.asList(element.knownNamespaces()));
    namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
    return getTagNameVariants(element, namespaces, null);
  }

  public static String[] getTagNameVariants(final XmlTag element,
                                            final Collection<String> namespaces,
                                            @Nullable List<String> nsInfo) {

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
    final XmlExtension extension = XmlExtension.getExtension((XmlFile)element.getContainingFile());
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

    final Iterator<XmlElementDescriptor> iterator = variants.iterator();
    String[] ret = new String[variants.size()];
    int index = 0;
    while(iterator.hasNext()){
      final XmlElementDescriptor descriptor = iterator.next();
      ret[index++] = descriptor.getName(element);
    }
    return ret;
  }

  private static void processVariantsInNamespace(final String namespace, final XmlTag element, final List<XmlElementDescriptor> variants,
                                                 final XmlElementDescriptor elementDescriptor,
                                                 final String elementNamespace,
                                                 final Map<String, XmlElementDescriptor> descriptorsMap, final Set<XmlNSDescriptor> visited,
                                                 XmlTag parent, final XmlExtension extension) {
    if(descriptorsMap.containsKey(namespace)){
        final XmlElementDescriptor descriptor = descriptorsMap.get(namespace);

      if(isAcceptableNs(element, elementDescriptor, elementNamespace, namespace)){
        for(XmlElementDescriptor containedDescriptor:descriptor.getElementsDescriptors(parent)) {
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

        XmlTag parentTag = extension.getParentTagForNamespace(element, namespace);
        XmlElementDescriptor parentDescriptor = elementDescriptor;
        if (parentTag != element.getParentTag()) {
          parentDescriptor = parentTag.getDescriptor();
        }

        for(XmlElementDescriptor containedDescriptor:rootElementsDescriptors) {
          if (containedDescriptor != null && couldContainDescriptor(parentTag, parentDescriptor, containedDescriptor, namespace)) {
            variants.add(containedDescriptor);
          }
        }
      }
    }
  }

  private static XmlNSDescriptor getDescriptor(final XmlTag element, final String namespace, final boolean strict,
                                               final XmlExtension extension) {
    return extension.getNSDescriptor(element, namespace, strict);
  }

  private static boolean couldContainDescriptor(final XmlTag parentTag, final XmlElementDescriptor elementDescriptor,
                                                final XmlElementDescriptor containedDescriptor, String containedDescriptorNs) {
    if (XmlUtil.nsFromTemplateFramework(containedDescriptorNs)) return true;
    if (parentTag == null) return true;
    final XmlTag childTag = parentTag.createChildTag(containedDescriptor.getName(), containedDescriptorNs, "", false);
    childTag.putUserData(XmlElement.INCLUDING_ELEMENT, parentTag);
    return elementDescriptor != null && elementDescriptor.getElementDescriptor(childTag, parentTag) != null;
  }

  private static boolean isAcceptableNs(final XmlTag element, final XmlElementDescriptor elementDescriptor,
                                        final String elementNamespace,
                                        final String namespace) {
    return !(elementDescriptor instanceof XmlElementDescriptorImpl) ||
        elementNamespace == null ||
        elementNamespace.equals(namespace) ||
         ((XmlElementDescriptorImpl)elementDescriptor).allowElementsFromNamespace(namespace, element.getParentTag());
  }

  public boolean isSoft() {
    return false;
  }

  @Nullable
  public static TagNameReference create(XmlElement element, ASTNode nameElement, boolean startTagFlag) {
    final XmlExtension extension = XmlExtension.getExtensionByElement(element);
    return extension == null ? null : extension.createTagNameReference(nameElement, startTagFlag);
  }
}
