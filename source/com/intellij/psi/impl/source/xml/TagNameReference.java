package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.openapi.util.TextRange;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

class TagNameReference implements PsiReference {
  private final boolean myStartTagFlag;
  private final TreeElement myNameElement;

  public TagNameReference(TreeElement nameElement, boolean startTagFlag) {
    myStartTagFlag = startTagFlag;
    myNameElement = nameElement;
  }

  public XmlTag getElement() {
    TreeElement parent = TreeUtil.findParent(myNameElement, XmlElementType.XML_TAG);
    if(parent == null) parent = TreeUtil.findParent(myNameElement, XmlElementType.HTML_TAG);
    return (XmlTag)SourceTreeToPsiMap.treeElementToPsi(parent);
  }

  public TextRange getRangeInElement() {
    if (getNameElement() == null){
      return new TextRange(0, 0);
    }
    final int parentOffset = getNameElement().getStartOffsetInParent();
    return new TextRange(parentOffset, parentOffset + getNameElement().getTextLength());
  }

  private TreeElement getNameElement() {
    return myNameElement;
  }

  public PsiElement resolve() {
    final XmlElementDescriptor descriptor = getElement().getDescriptor();
    if (descriptor != null){
      return descriptor.getDeclaration();
    }
    return null;
  }

  public String getCanonicalText() {
    return getNameElement().getText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getElement().setName(newElementName);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMetaOwner){
      final PsiMetaOwner owner = (PsiMetaOwner)element;
      if (owner.getMetaData() instanceof XmlElementDescriptor){
        getElement().setName(owner.getMetaData().getName(getElement()));
      }
    }
    throw new IncorrectOperationException("Cant bind to not a xml element definition!");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants(){
    final List<XmlElementDescriptor> variants = new ArrayList<XmlElementDescriptor>();
    final XmlTag element = getElement();
    if(!myStartTagFlag) return new Object[]{element.getName()};
    final Map<String, XmlElementDescriptor> descriptorsMap = new HashMap<String, XmlElementDescriptor>();

    {
      PsiElement curElement = element.getParent();
      while(curElement instanceof XmlTag){
        final XmlTag declarationTag = (XmlTag)curElement;
        final String namespace = declarationTag.getNamespace();
        if(!descriptorsMap.containsKey(namespace)) {
          final XmlElementDescriptor descriptor = declarationTag.getDescriptor();
          if(descriptor != null)
            descriptorsMap.put(namespace, descriptor);
        }
        curElement = curElement.getContext();
      }
    }
    final List<String> namespaces = new ArrayList<String>(Arrays.asList(element.knownNamespaces()));
    namespaces.add(XmlUtil.EMPTY_NAMESPACE); // empty namespace
    final Iterator<String> nsIterator = namespaces.iterator();

    final Set<XmlNSDescriptor> visited = new HashSet<XmlNSDescriptor>();
    while (nsIterator.hasNext()) {
      final String namespace = nsIterator.next();

      if(descriptorsMap.containsKey(namespace)){
        final XmlElementDescriptor descriptor = descriptorsMap.get(namespace);
        variants.addAll(Arrays.asList(descriptor.getElementsDescriptors(element)));

        if (element instanceof HtmlTag) {
          HtmlUtil.addHtmlSpecificCompletions(descriptor, element, variants);
        }
        visited.add(descriptor.getNSDescriptor());
      }
      else{
        // Don't use default namespace in case there are other namespaces in scope
        // If there are tags from default namespace they will be handeled via
        // their element descriptors (prev if section)
        if(namespace.length() == 0 && !visited.isEmpty()) continue;
        final XmlNSDescriptor NSDescriptor = element.getNSDescriptor(namespace, false);
        if(NSDescriptor != null && !visited.contains(NSDescriptor)){
          visited.add(NSDescriptor);
          variants.addAll(Arrays.asList(NSDescriptor.getRootElementsDescriptors()));
        }
      }
    }

    final Iterator<XmlElementDescriptor> iterator = variants.iterator();
    String[] ret = new String[variants.size()];
    int index = 0;
    while(iterator.hasNext()){
      final XmlElementDescriptor descriptor = iterator.next();
      ret[index++] = descriptor.getName(element.getParent());
    }
    return ret;
  }

  public boolean isSoft() {
    return false;
  }
}
