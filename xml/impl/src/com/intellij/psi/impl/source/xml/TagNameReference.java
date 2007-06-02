package com.intellij.psi.impl.source.xml;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.TagFileQuickFixProvider;
import com.intellij.jsp.impl.TldDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.SchemaNSDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TagNameReference implements PsiReference, QuickFixProvider {
  private final boolean myStartTagFlag;
  private final ASTNode myNameElement;
  @NonNls protected static final String TAG_EXTENSION = ".tag";
  @NonNls protected static final String TAGX_EXTENSION = ".tagx";

  public TagNameReference(ASTNode nameElement, boolean startTagFlag) {
    myStartTagFlag = startTagFlag;
    myNameElement = nameElement;
  }

  public PsiElement getElement() {
    final XmlTag tag = PsiTreeUtil.getParentOfType(myNameElement.getPsi(), XmlTag.class);
    return tag != null ? tag:myNameElement.getPsi();
  }

  private XmlTag getTagElement() {
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

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XmlTag element = getTagElement();
    if (element == null) return null;

    if ((newElementName.endsWith(TAG_EXTENSION) || newElementName.endsWith(TAGX_EXTENSION)) &&
        PsiUtil.isInJspFile(element.getContainingFile())
       ) {
      final String namespacePrefix = element.getNamespacePrefix();
      newElementName = newElementName.substring(0,newElementName.lastIndexOf('.'));

      if (namespacePrefix.length() > 0) {
        newElementName = namespacePrefix + ":" + newElementName;
      }
    } else if (newElementName.indexOf(':') == -1) {
      final String namespacePrefix = element.getNamespacePrefix();

      if (namespacePrefix.length() > 0) {

        final XmlNSDescriptor nsDescriptor = element.getNSDescriptor(element.getNamespace(), true);

        if (nsDescriptor instanceof TldDescriptor || nsDescriptor instanceof SchemaNSDescriptor) {
          newElementName = namespacePrefix + ":" + newElementName;
        }
      }
    }

    element.setName(newElementName);

    return element;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    PsiMetaDataBase metaData = null;

    if (element instanceof PsiMetaBaseOwner){
      final PsiMetaBaseOwner owner = (PsiMetaBaseOwner)element;
      metaData = owner.getMetaData();

      if (metaData instanceof XmlElementDescriptor){
        getTagElement().setName(metaData.getName(getElement()));
      }
    } else if (PsiUtil.isInJspFile(element) && element instanceof PsiFile) {
      // implicit reference to tag file
      return getElement();
    }

    throw new IncorrectOperationException("Cant bind to not a xml element definition!"+element+","+metaData);
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants(){
    final List<XmlElementDescriptor> variants = new ArrayList<XmlElementDescriptor>();
    final PsiElement element = getElement();
    if (element instanceof JspDirective) return EMPTY_ARRAY;

    if(!myStartTagFlag){
      XmlTag fromJspTree = null;
      final PsiFile containingFile = element.getContainingFile();
      if(containingFile.getViewProvider().getBaseLanguage() == StdLanguages.JSP){
        final JspFile jspFile = PsiUtil.getJspFile(containingFile);
        final int startOffset = element.getTextRange().getStartOffset() + getRangeInElement().getStartOffset();
        PsiElement current = jspFile.getDocument().findElementAt(startOffset);
        if(current != element && (current = PsiTreeUtil.getParentOfType(current, XmlText.class)) != null) {
          fromJspTree = ((XmlText)current).getParentTag();
          if (XmlChildRole.EMPTY_TAG_END_FINDER.findChild(fromJspTree.getNode()) != null
              || XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(fromJspTree.getNode()) != null
              || fromJspTree instanceof JspXmlRootTag)
            fromJspTree = null;
          while ((current = current.getPrevSibling()) != null) {
            if (current instanceof XmlTag) {
              final XmlTag xmlTag = (XmlTag)current;

              if (XmlChildRole.EMPTY_TAG_END_FINDER.findChild(xmlTag.getNode()) == null
                  && XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(xmlTag.getNode()) == null) {
                fromJspTree = xmlTag;
                break;
              }
            }
          }
        }
      }

      final boolean jspTreeSuggestionIsNotValid = fromJspTree == null || fromJspTree.getName().length() == 0;

      if (element instanceof XmlTag) {
        final XmlTag tag = (XmlTag)element;

        if(jspTreeSuggestionIsNotValid) return new Object[]{tag.getName()};
        return new Object[]{tag.getName(), fromJspTree.getName()};
      } else {
        return jspTreeSuggestionIsNotValid ? EMPTY_ARRAY : new Object[] { fromJspTree.getName() };
      }
    }
    return getTagNameVariants((XmlTag)element, variants);
  }

  public static Object[] getTagNameVariants(final XmlTag element, final List<XmlElementDescriptor> variants) {
    final Map<String, XmlElementDescriptor> descriptorsMap = new HashMap<String, XmlElementDescriptor>();
    XmlElementDescriptor elementDescriptor = null;
    String elementNamespace = null;

    {
      PsiElement curElement = element.getParent();
      
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
    final List<String> namespaces = new ArrayList<String>(Arrays.asList(element.knownNamespaces()));
    namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
    final Iterator<String> nsIterator = namespaces.iterator();

    final Set<XmlNSDescriptor> visited = new HashSet<XmlNSDescriptor>();
    while (nsIterator.hasNext()) {
      final String namespace = nsIterator.next();

      if(descriptorsMap.containsKey(namespace)){
        final XmlElementDescriptor descriptor = descriptorsMap.get(namespace);
        
        if(isAcceptableNs(element, elementDescriptor, elementNamespace, namespace)){
          for(XmlElementDescriptor containedDescriptor:descriptor.getElementsDescriptors(element)) {
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
        if (namespace == null) continue;
        if(namespace.length() == 0 && !visited.isEmpty()) continue;
        XmlNSDescriptor nsDescriptor = element.getNSDescriptor(namespace, false);
        if(nsDescriptor == null && PsiUtil.isInJspFile(element))
          nsDescriptor = PsiUtil.getJspFile(element).getDocument().getRootTag().getNSDescriptor(namespace, false);

        if(nsDescriptor != null && !visited.contains(nsDescriptor) &&
           isAcceptableNs(element, elementDescriptor, elementNamespace, namespace)
          ){
          visited.add(nsDescriptor);
          final XmlElementDescriptor[] rootElementsDescriptors =
            nsDescriptor.getRootElementsDescriptors(PsiTreeUtil.getParentOfType(element, XmlDocument.class));
          
          for(XmlElementDescriptor containedDescriptor:rootElementsDescriptors) {
            if (containedDescriptor != null) variants.add(containedDescriptor);
          }
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

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    TagFileQuickFixProvider.registerTagFileReferenceQuickFix(info, (TagNameReference)reference);
  }
}
