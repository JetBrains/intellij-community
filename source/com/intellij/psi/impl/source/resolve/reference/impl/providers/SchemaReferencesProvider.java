package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 4, 2005
 * Time: 3:58:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class SchemaReferencesProvider implements PsiReferenceProvider {
  @NonNls private static final String VALUE_ATTR_NAME = "value";
  @NonNls private static final String PATTERN_TAG_NAME = "pattern";
  @NonNls private static final String NAME_ATTR_NAME = "name";
  @NonNls private static final String MEMBER_TYPES_ATTR_NAME = "memberTypes";

  static class RegExpReference extends JspReferencesProvider.BasicAttributeValueReference implements
                                                                                          EmptyResolveMessageProvider  {
    private String message;

    public RegExpReference(final PsiElement element) {
      super(element);
    }

    private static Pattern pattern = Pattern.compile("^(?:\\\\i|\\\\l)");
    private static Pattern pattern2 = Pattern.compile("([^\\\\])(?:\\\\i|\\\\l)");

    @Nullable
    public PsiElement resolve() {
      try {
        String text = getCanonicalText();

        // \i and \l are special classes that does not present in java reg exps, so replace their occurences with more usable \w
        text = pattern2.matcher(pattern.matcher(text).replaceFirst("\\\\w")).replaceAll("$1\\\\w");

        Pattern.compile(text);
        message = null;
        return myElement;
      }
      catch (Exception e) {
        message = PsiBundle.message("invalid.reqular.expression.message", getCanonicalText());
        return null;
      }
    }

    public Object[] getVariants() {
      return new Object[0];
    }

    public boolean isSoft() {
      return false;
    }

    public String getUnresolvedMessagePattern() {
      return message;
    }
  }

  static class NameReference implements PsiReference {
    private PsiElement myElement;

    NameReference(PsiElement element) {
      myElement = element;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return new TextRange(1,myElement.getTextLength()-1);
    }

    @Nullable
    public PsiElement resolve() {
      return myElement.getParent().getParent();
    }

    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length()- 1);
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement).handleContentChange(
        myElement,
        getRangeInElement(),
        newElementName.substring(newElementName.indexOf(':') + 1)
      );
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return true;
    }
  }

  static class TypeOrElementOrAttributeReference implements PsiReference {
    private PsiElement myElement;
    private TextRange myRange;
    @NonNls private static final String XML_NS_PREFIX = "xml";
    @NonNls private static final String LANG_XML_NS_ATTR_NAME = "lang";
    @NonNls private static final String BASE_XML_NS_ATTR_NAME = "base";
    @NonNls private static final String SPACE_XML_NS_ATTR_NAME = "space";
    @NonNls private static final String GROUP_TAG_NAME = "group";
    @NonNls private static final String ATTRIBUTE_GROUP_TAG_NAME = "attributeGroup";
    @NonNls private static final String ATTRIBUTE_TAG_NAME = "attribute";
    @NonNls private static final String ELEMENT_TAG_NAME = "element";
    @NonNls private static final String SIMPLE_TYPE_TAG_NAME = "simpleType";
    @NonNls private static final String COMPLEX_TYPE_TAG_NAME = "complexType";

    enum ReferenceType {
      ElementReference, AttributeReference, GroupReference, AttributeGroupReference, TypeReference
    }

    private ReferenceType myType;

    TypeOrElementOrAttributeReference(PsiElement element) {
      this(element,new TextRange(1,element.getTextLength()-1));
    }

    TypeOrElementOrAttributeReference(PsiElement element, TextRange range) {
      myElement = element;
      myRange   = range;

      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
      final XmlTag tag = attribute.getParent();
      final String localName = tag.getLocalName();
      final String attributeLocalName = attribute.getLocalName();

      if ("ref".equals(attributeLocalName) || "substitutionGroup".equals(attributeLocalName)) {
        if (localName.equals(GROUP_TAG_NAME)) {
          myType = ReferenceType.GroupReference;
        } else if (localName.equals(ATTRIBUTE_GROUP_TAG_NAME)) {
          myType = ReferenceType.AttributeGroupReference;
        } else if (ELEMENT_TAG_NAME.equals(localName)) {
          myType = ReferenceType.ElementReference;
        } else if (ATTRIBUTE_TAG_NAME.equals(localName)) {
          myType = ReferenceType.AttributeReference;
        }
      } else if ("type".equals(attributeLocalName) ||
                 BASE_XML_NS_ATTR_NAME.equals(attributeLocalName) ||
                 MEMBER_TYPES_ATTR_NAME.equals(attributeLocalName)
                ) {
        myType = ReferenceType.TypeReference;
      }
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return myRange;
    }

    @Nullable
    public PsiElement resolve() {
      final PsiManager manager = getElement().getManager();
      final PsiElement psiElement;

      if(manager instanceof PsiManagerImpl){
        psiElement = ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, new ResolveCache.Resolver() {
          public PsiElement resolve(PsiReference ref, boolean incompleteCode) {
            return resolveInner();
          }
        }, false, false);
      } else {
        psiElement = resolveInner();
      }

      return psiElement != PsiUtil.NULL_PSI_ELEMENT ? psiElement:null;
    }

    private PsiElement resolveInner() {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
      if (tag == null) return PsiUtil.NULL_PSI_ELEMENT;

      String canonicalText = getCanonicalText();
      XmlNSDescriptorImpl nsDescriptor = getDescriptor(tag,canonicalText);

      if (myType != null && nsDescriptor != null) {

        switch(myType) {
          case GroupReference: return nsDescriptor.findGroup(canonicalText);
          case AttributeGroupReference: return nsDescriptor.findAttributeGroup(canonicalText);
          case ElementReference: {
            XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(
              XmlUtil.findLocalNameByQualifiedName(canonicalText),
              tag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(canonicalText)),
              new HashSet<XmlNSDescriptorImpl>(),
              true
            );

            return descriptor != null ? descriptor.getDeclaration(): PsiUtil.NULL_PSI_ELEMENT;
          }
          case AttributeReference: {
            final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(canonicalText);
            final String localNameByQualifiedName = XmlUtil.findLocalNameByQualifiedName(canonicalText);
            XmlAttributeDescriptor descriptor = nsDescriptor.getAttribute(
              localNameByQualifiedName,
              tag.getNamespaceByPrefix(prefixByQualifiedName)
            );

            if (descriptor != null) return descriptor.getDeclaration();

            if (XML_NS_PREFIX.equals(prefixByQualifiedName) &&
                ( LANG_XML_NS_ATTR_NAME.equals(localNameByQualifiedName) ||
                  BASE_XML_NS_ATTR_NAME.equals(localNameByQualifiedName) ||
                  SPACE_XML_NS_ATTR_NAME.equals(localNameByQualifiedName)
                )
               ) {
              return myElement; // for compatibility
            }

            return PsiUtil.NULL_PSI_ELEMENT;
          }
          case TypeReference: {
            TypeDescriptor typeDescriptor = nsDescriptor.getTypeDescriptor(canonicalText,tag);
            if (typeDescriptor instanceof ComplexTypeDescriptor) {
              return ((ComplexTypeDescriptor)typeDescriptor).getDeclaration();
            } else if (typeDescriptor instanceof TypeDescriptor) {
              return myElement;
            }
          }
        }
      }

      return PsiUtil.NULL_PSI_ELEMENT;
    }

    private XmlNSDescriptorImpl getDescriptor(final XmlTag tag, String text) {
      XmlNSDescriptor nsDescriptor = nsDescriptor = tag.getNSDescriptor(
        tag.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(text)),
        true
      );

      if (nsDescriptor == null) { // import
        nsDescriptor = (XmlNSDescriptor)((XmlFile)tag.getContainingFile()).getDocument().getMetaData();
      }

      return nsDescriptor instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)nsDescriptor:null;
    }

    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(myRange.getStartOffset(),myRange.getEndOffset());
    }

    public PsiElement handleElementRename(String _newElementName) throws IncorrectOperationException {
      final String canonicalText = getCanonicalText();
      final String newElementName = canonicalText.substring(0,canonicalText.indexOf(':') + 1) + _newElementName;

      return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement).handleContentChange(
        myElement,
        getRangeInElement(),
        newElementName
      );
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    static class CompletionProcessor implements PsiElementProcessor<XmlTag> {
      List<String> myElements = new ArrayList<String>(1);
      String namespace;
      XmlTag tag;

      public boolean execute(final XmlTag element) {
        String name = element.getAttributeValue(NAME_ATTR_NAME);
        final String prefixByNamespace = tag.getPrefixByNamespace(namespace);
        if (prefixByNamespace != null && prefixByNamespace.length() > 0) {
          name = prefixByNamespace + ":" + name;
        }
        myElements.add( name );
        return true;
      }
    }

    public Object[] getVariants() {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
      if (tag == null) return null;

      String[] tagNames = null;

      switch (myType) {
        case GroupReference:
          tagNames = new String[] {GROUP_TAG_NAME};
          break;
        case AttributeGroupReference:
          tagNames = new String[] {ATTRIBUTE_GROUP_TAG_NAME};
          break;
        case AttributeReference:
          tagNames = new String[] {ATTRIBUTE_TAG_NAME};
          break;
        case ElementReference:
          tagNames = new String[] {ELEMENT_TAG_NAME};
          break;
        case TypeReference:
          tagNames = new String[] {SIMPLE_TYPE_TAG_NAME,COMPLEX_TYPE_TAG_NAME};
          break;
      }

      CompletionProcessor processor = new CompletionProcessor();
      processor.tag = tag;
      HashSet<String> visitedNamespaces = new HashSet<String>(1);

      XmlDocument document = ((XmlFile)myElement.getContainingFile()).getDocument();
      final XmlTag rootTag = document.getRootTag();
      String ourNamespace = rootTag != null ? rootTag.getAttributeValue("targetNamespace") : "";
      if (ourNamespace == null) ourNamespace = "";

      for(String namespace:tag.knownNamespaces()) {
        if (ourNamespace.equals(namespace)) continue;
        final XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace, true);

        if (nsDescriptor instanceof XmlNSDescriptorImpl) {
          processNamespace(namespace, processor, nsDescriptor, tagNames);
          visitedNamespaces.add(namespace);
        }
      }

      if (ourNamespace != null && ourNamespace.length() > 0) {
        XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
        if (nsDescriptor != null) {
          processNamespace(
            ourNamespace,
            processor,
            nsDescriptor,
            tagNames
          );
        }
      }

      return processor.myElements.toArray(new String[processor.myElements.size()]);
    }

    private void processNamespace(final String namespace,
                                  final CompletionProcessor processor,
                                  final XmlNSDescriptor nsDescriptor,
                                  final String[] tagNames) {
      processor.namespace = namespace;

      final XmlNSDescriptorImpl xmlNSDescriptor = ((XmlNSDescriptorImpl)nsDescriptor);
      xmlNSDescriptor.processTagsInNamespace(
        xmlNSDescriptor.getTag(),
        tagNames,
        processor
      );
    }

    public boolean isSoft() {
      return false;
    }
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof XmlAttribute)) return PsiReference.EMPTY_ARRAY;
    final String attrName = ((XmlAttribute)parent).getName();

    if (VALUE_ATTR_NAME.equals(attrName)) {
      if (PATTERN_TAG_NAME.equals(((XmlAttribute)parent).getParent().getLocalName())) {
        return new PsiReference[] { new RegExpReference(element) };
      } else {
        return PsiReference.EMPTY_ARRAY;
      }
    } else if (NAME_ATTR_NAME.equals(attrName)) {
      return new PsiReference[] { new NameReference(element) };
    } else if (MEMBER_TYPES_ATTR_NAME.equals(attrName)) {
      final List<PsiReference> result = new ArrayList<PsiReference>(1);
      final String text = element.getText();
      int lastIndex = 1;
      int index = text.indexOf(' ');

      while(index != -1) {
        result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, index) ) );
        lastIndex = index + 1;
        index = text.indexOf(' ',lastIndex);
      }

      result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, text.length() - 1) ) );

      return result.toArray(new PsiReference[result.size()]);
    } else {
      return new PsiReference[] { new TypeOrElementOrAttributeReference(element) };
    }
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
