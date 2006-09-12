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
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

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

  public XmlTag getElement() {
    return PsiTreeUtil.getParentOfType(myNameElement.getPsi(), XmlTag.class);
  }

  public TextRange getRangeInElement() {
    if (getNameElement() == null){
      return new TextRange(0, 0);
    }
    final int parentOffset = ((TreeElement)getNameElement()).getStartOffsetInParent();
    return new TextRange(parentOffset, parentOffset + getNameElement().getTextLength());
  }

  private ASTNode getNameElement() {
    return myNameElement;
  }

  public PsiElement resolve() {
    final XmlTag tag = getElement();
    final XmlElementDescriptor descriptor = tag.getDescriptor();

    if (descriptor != null){
      return descriptor instanceof AnyXmlElementDescriptor ? tag : descriptor.getDeclaration();
    }
    return null;
  }

  public String getCanonicalText() {
    return getNameElement().getText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XmlTag element = getElement();
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
        if (PsiUtil.isInJspFile(element.getContainingFile())) {
          final XmlNSDescriptor nsDescriptor = element.getNSDescriptor(element.getNamespace(), true);

          if (nsDescriptor instanceof TldDescriptor) {
            newElementName = namespacePrefix + ":" + newElementName;
          }
        }
      }
    }

    element.setName(newElementName);

    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    PsiMetaDataBase metaData = null;

    if (element instanceof PsiMetaBaseOwner){
      final PsiMetaBaseOwner owner = (PsiMetaBaseOwner)element;
      metaData = owner.getMetaData();

      if (metaData instanceof XmlElementDescriptor){
        getElement().setName(metaData.getName(getElement()));
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
    final XmlTag element = getElement();
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
      if(fromJspTree == null || fromJspTree.getName().length() == 0) return new Object[]{element.getName()};
      return new Object[]{element.getName(), fromJspTree.getName()};
    }
    return getTagNameVariants(element, variants);
  }

  public static Object[] getTagNameVariants(final XmlTag element, final List<XmlElementDescriptor> variants) {
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
    namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
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
        if (namespace == null) continue;
        if(namespace.length() == 0 && !visited.isEmpty()) continue;
        XmlNSDescriptor nsDescriptor = element.getNSDescriptor(namespace, false);
        if(nsDescriptor == null && PsiUtil.isInJspFile(element))
          nsDescriptor = PsiUtil.getJspFile(element).getDocument().getRootTag().getNSDescriptor(namespace, false);

        if(nsDescriptor != null && !visited.contains(nsDescriptor)){
          visited.add(nsDescriptor);
          variants.addAll(Arrays.asList(nsDescriptor.getRootElementsDescriptors(PsiTreeUtil.getParentOfType(element, XmlDocument.class))));
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

  public boolean isSoft() {
    return false;
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    TagFileQuickFixProvider.registerTagFileReferenceQuickFix(info, (TagNameReference)reference);
  }
}
