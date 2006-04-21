package com.intellij.psi.impl.source.xml;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.XmlAttributeSetImpl;
import com.intellij.pom.xml.impl.events.XmlTagNameChangedImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlTagTextUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Mike
 */

public class XmlTagImpl extends XmlElementImpl implements XmlTag/*, ModificationTracker */{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTagImpl");
  private static final Class ourReferenceClass = XmlTag.class;

  private String myName = null;
  private XmlAttribute[] myAttributes = null;
  private Map<String, String> myAttributeValueMap = null;
  private XmlTag[] myTags = null;
  private XmlTagValue myValue = null;
  private Map<String, CachedValue<XmlNSDescriptor>> myNSDescriptorsMap = null;

  private boolean myHaveNamespaceDeclarations = false;
  private BidirectionalMap<String, String> myNamespaceMap = null;

  public XmlTagImpl() {
    this(XML_TAG);
  }

  protected XmlTagImpl(IElementType type) {
    super(type);
  }

  public void clearCaches() {
    myName = null;
    myNamespaceMap = null;
    myAttributes = null;
    myAttributeValueMap = null;
    myHaveNamespaceDeclarations = false;
    myValue = null;
    myTags = null;
    myNSDescriptorsMap = null;
    super.clearCaches();
  }

  @NotNull
  public PsiReference[] getReferences() {
    ProgressManager.getInstance().checkCanceled();
    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(this);
    if (startTagName == null) return PsiReference.EMPTY_ARRAY;
    final ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(this);
    final PsiReference[] referencesFromProviders = ResolveUtil.getReferencesFromProviders(this, ourReferenceClass);

    if (endTagName != null){
      final PsiReference[] psiReferences = new PsiReference[referencesFromProviders.length + 2];
      psiReferences[0] = new TagNameReference(startTagName, true);
      psiReferences[1] = new TagNameReference(endTagName, false);

      System.arraycopy(referencesFromProviders, 0, psiReferences, 2, referencesFromProviders.length);
      return psiReferences;
    }
    else{
      final PsiReference[] psiReferences = new PsiReference[referencesFromProviders.length + 1];
      psiReferences[0] = new TagNameReference(startTagName, true);

      System.arraycopy(referencesFromProviders, 0, psiReferences, 1, referencesFromProviders.length);
      return psiReferences;
    }
  }

  public XmlNSDescriptor getNSDescriptor(final String namespace, boolean strict) {
    initNSDescriptorsMap();

    final CachedValue<XmlNSDescriptor> descriptor = myNSDescriptorsMap.get(namespace);
    if(descriptor != null) return descriptor.getValue();

    final XmlTag parent = getParentTag();
    if(parent == null){
      final XmlDocument parentOfType = PsiTreeUtil.getParentOfType(this, XmlDocument.class);
      if(parentOfType == null) return null;
      return parentOfType.getDefaultNSDescriptor(namespace, strict);
    }

    return parent.getNSDescriptor(namespace, strict);
  }

  public boolean isEmpty() {
    return XmlChildRole.CLOSING_TAG_START_FINDER.findChild(this) == null;
  }

  private Map<String, CachedValue<XmlNSDescriptor>> initNSDescriptorsMap() {
    boolean exceptionOccurred = false;

    if(myNSDescriptorsMap == null){
      try{
        {
          // XSD aware attributes processing
          final String noNamespaceDeclaration = getAttributeValue("noNamespaceSchemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
          final String schemaLocationDeclaration = getAttributeValue("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
          if(noNamespaceDeclaration != null){
            initializeSchema(XmlUtil.EMPTY_URI, noNamespaceDeclaration);
          }
          if(schemaLocationDeclaration != null){
            final StringTokenizer tokenizer = new StringTokenizer(schemaLocationDeclaration);
            while(tokenizer.hasMoreTokens()){
              final String uri = tokenizer.nextToken();
              if(tokenizer.hasMoreTokens()){
                initializeSchema(uri, tokenizer.nextToken());
              }
            }
          }
        }
        {
          // namespace attributes processing (XSD declaration via ExternalResourceManager)
          if(hasNamespaceDeclarations()){
            final XmlAttribute[] attributes = getAttributes();
            for (final XmlAttribute attribute : attributes) {
              if (attribute.isNamespaceDeclaration()) {
                String ns = attribute.getValue();
                if (ns == null) ns = XmlUtil.EMPTY_URI;
                if (myNSDescriptorsMap == null || !myNSDescriptorsMap.containsKey(ns)) initializeSchema(ns, ns);
              }
            }
          }
        }
      }
      catch(RuntimeException e){
        myNSDescriptorsMap = null;
        exceptionOccurred = true;
        throw e;
      }
      finally{
        if(myNSDescriptorsMap == null && !exceptionOccurred) {
          myNSDescriptorsMap = Collections.emptyMap();
        }
      }
    }
    return myNSDescriptorsMap;
  }

  private boolean initializeSchema(final String namespace, final String fileLocation) {
    if(myNSDescriptorsMap == null) myNSDescriptorsMap = new HashMap<String, CachedValue<XmlNSDescriptor>>();

    XmlFile file = retrieveFile(fileLocation);
    PsiMetaOwner owner = retrieveOwner(file, namespace);

    if (owner != null){
      myNSDescriptorsMap.put(namespace, getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlNSDescriptor>() {
        public CachedValueProvider.Result<XmlNSDescriptor> compute() {
          XmlFile currentFile = retrieveFile(fileLocation);
          PsiMetaOwner currentOwner = retrieveOwner(currentFile, namespace);
          if (currentOwner == null) return new Result<XmlNSDescriptor>(null, XmlTagImpl.this);

          final XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)currentOwner.getMetaData();
          return new Result<XmlNSDescriptor>(
            nsDescriptor,
            nsDescriptor != null ? nsDescriptor.getDependences() : currentFile
          );
        }
      }, false));
    }
    return true;
  }

  private XmlFile retrieveFile(final String fileLocation) {
    return XmlUtil.findXmlFile(XmlUtil.getContainingFile(this),
                               ExternalResourceManager.getInstance().getResourceLocation(fileLocation));
  }

  private PsiMetaOwner retrieveOwner(final XmlFile file, final String namespace) {
    final PsiMetaOwner owner;
    if (file == null) {
      final String attributeValue = getAttributeValue("targetNamespace");
      if (namespace.equals(attributeValue)) {
        owner = this;
      } else {
        owner = null;
      }
    } else {
      owner = file.getDocument();
    }
    return owner;
  }

  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    if (references != null && references.length > 0){
      return references[0];
    }
    return null;
  }

  public XmlElementDescriptor getDescriptor() {
    final String namespace = getNamespace();
    XmlElementDescriptor elementDescriptor;

    if (XmlUtil.EMPTY_URI.equals(namespace)) { //nonqualified items
      final PsiElement parent = getParent();

      if (parent instanceof XmlTag) {
        final XmlElementDescriptor descriptor = ((XmlTag)parent).getDescriptor();

        if (descriptor != null) {
          elementDescriptor = descriptor.getElementDescriptor(this);

          if (elementDescriptor != null && !(elementDescriptor instanceof AnyXmlElementDescriptor)) {
            return elementDescriptor;
          }
        }
      }
    }

    final XmlNSDescriptor nsDescriptor = getNSDescriptor(namespace, false);
    elementDescriptor = (nsDescriptor != null) ? nsDescriptor.getElementDescriptor(this) : null;

    if(elementDescriptor == null){
      elementDescriptor = XmlUtil.findXmlDescriptorByType(this);
    }

    return elementDescriptor;
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_NAME || i == XML_TAG_NAME) {
      return ChildRole.XML_TAG_NAME;
    }
    else if (i == XML_ATTRIBUTE) {
      return ChildRole.XML_ATTRIBUTE;
    }
    else {
      return ChildRole.NONE;
    }
  }

  @NotNull
  public String getName() {
    if (myName != null) return myName;

    final ASTNode nameElement = XmlChildRole.START_TAG_NAME_FINDER.findChild(this);
    if (nameElement != null){
      myName = nameElement.getText();
    }
    else{
      myName = "";
    }

    return myName;
  }

  public PsiElement setName(final String name) throws IncorrectOperationException {
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      public PomModelEvent runInner() throws IncorrectOperationException{
        final String oldName = getName();
        final XmlTagImpl dummyTag = (XmlTagImpl)getManager().getElementFactory().createTagFromText(XmlTagTextUtil.composeTagText(name, "aa"));
        final XmlTagImpl tag = XmlTagImpl.this;
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(tag);
        ChangeUtil.replaceChild(tag, (TreeElement)XmlChildRole.START_TAG_NAME_FINDER.findChild(tag), ChangeUtil.copyElement((TreeElement)XmlChildRole.START_TAG_NAME_FINDER.findChild(dummyTag), charTableByTree));
        final ASTNode childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag);
        if(childByRole != null) ChangeUtil.replaceChild(tag, (TreeElement)childByRole, ChangeUtil.copyElement((TreeElement)XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(dummyTag), charTableByTree));

        return XmlTagNameChangedImpl.createXmlTagNameChanged(model, tag, oldName);
      }
    });
    return this;
  }

  public XmlAttribute[] getAttributes() {
    if(myAttributes != null) return myAttributes;
    myAttributeValueMap = new HashMap<String, String>();

    myAttributes = calculateAttributes();

    return myAttributes;
  }

  @NotNull
  protected XmlAttribute[] calculateAttributes() {
    final List<XmlAttribute> result = new ArrayList<XmlAttribute>(10);
    processElements(
      new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof XmlAttribute){
            XmlAttribute attribute = (XmlAttribute)element;
            result.add(attribute);
            cacheOneAttributeValue(attribute.getName(),attribute.getValue());
            myHaveNamespaceDeclarations = myHaveNamespaceDeclarations || attribute.isNamespaceDeclaration();
          }
          else if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_TAG_END) {
            return false;
          }
          return true;
        }
      }, this
    );
    if (result.isEmpty()) {
      myAttributeValueMap = Collections.emptyMap();
      return XmlAttribute.EMPTY_ARRAY;
    }
    else {
      return ContainerUtil.toArray(result, new XmlAttribute[result.size()]);
    }
  }

  protected void cacheOneAttributeValue(String name, String value) {
    myAttributeValueMap.put(name, value);
  }

  public String getAttributeValue(String qname) {
    if(myAttributeValueMap == null) getAttributes();
    return myAttributeValueMap.get(qname);
  }

  public String getAttributeValue(String name, String namespace) {
    final String prefix = getPrefixByNamespace(namespace);
    if(prefix != null && prefix.length() > 0) name = prefix + ":" + name;
    return getAttributeValue(name);
  }

  public XmlTag[] getSubTags() {
    if(myTags != null) return myTags;
    final List<XmlTag> result = new ArrayList<XmlTag>();

    processElements(
      new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof XmlTag) result.add((XmlTag)element);
          return true;
        }
      }, this);

    myTags = result.toArray(new XmlTag[result.size()]);
    return myTags;
  }

  public XmlTag[] findSubTags(String name) {
    return findSubTags(name, null);
  }

  public XmlTag[] findSubTags(final String name, final String namespace) {
    final XmlTag[] subTags = getSubTags();
    final List<XmlTag> result = new ArrayList<XmlTag>();
    for (final XmlTag subTag : subTags) {
      if (namespace == null) {
        if (name.equals(subTag.getName())) result.add(subTag);
      }
      else if (name.equals(subTag.getLocalName()) && namespace.equals(subTag.getNamespace())) {
        result.add(subTag);
      }
    }
    return result.toArray(new XmlTag[result.size()]);
  }

  public XmlTag findFirstSubTag(String name) {
    final XmlTag[] subTags = findSubTags(name);
    if(subTags.length > 0) return subTags[0];
    return null;
  }

  public XmlAttribute getAttribute(String name, String namespace) {
    if(namespace == null || namespace == XmlUtil.ANY_URI || namespace.equals(getNamespace())) return getAttribute(name);
    final String prefix = getPrefixByNamespace(namespace);
    String qname =  prefix != null && prefix.length() > 0 ? prefix + ":" + name : name;
    return getAttribute(qname);
  }

  private XmlAttribute getAttribute(String qname){
    if(qname == null) return null;
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(this);
    final XmlAttribute[] attributes = getAttributes();

    final CharSequence charTableIndex = charTableByTree.intern(qname);

    for (final XmlAttribute attribute : attributes) {
      final LeafElement attrNameElement = (LeafElement)XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(attribute.getNode());
      if (attrNameElement.getInternedText() == charTableIndex) return attribute;
    }
    return null;
  }

  @NotNull
  public String getNamespace() {
    return getNamespaceByPrefix(getNamespacePrefix());
  }

  @NotNull
  public String getNamespacePrefix() {
    final String name = getName();
    final int index = name.indexOf(':');
    if(index >= 0){
      return name.substring(0, index);
    }
    return "";
  }

  @NotNull
  public String getNamespaceByPrefix(String prefix){
    final PsiElement parent = getParent();
    initNamespaceMaps(parent);
    if(myNamespaceMap != null){
      final String ns = myNamespaceMap.get(prefix);
      if(ns != null) return ns;
    }
    if(parent instanceof XmlTag) return ((XmlTag)parent).getNamespaceByPrefix(prefix);
    return XmlUtil.EMPTY_URI;
  }

  public String getPrefixByNamespace(String namespace){
    final PsiElement parent = getParent();
    initNamespaceMaps(parent);
    if(myNamespaceMap != null){
      List<String> keysByValue = myNamespaceMap.getKeysByValue(namespace);
      final String ns = keysByValue == null ? null : keysByValue.get(0);
      if(ns != null) return ns;
    }
    if(parent instanceof XmlTag) return ((XmlTag)parent).getPrefixByNamespace(namespace);
    return null;
  }

  public String[] knownNamespaces(){
    final PsiElement parent = getParent();
    initNamespaceMaps(parent);
    List<String> known = Collections.emptyList();
    if(myNamespaceMap != null){
      known = new ArrayList<String>(myNamespaceMap.values());
    }
    if(parent instanceof XmlTag){
      if(known.isEmpty()) return ((XmlTag)parent).knownNamespaces();
      known.addAll(Arrays.asList(((XmlTag)parent).knownNamespaces()));
    }
    else if (PsiUtil.isInJspFile(getContainingFile())) {
      final XmlTag rootTag = (PsiUtil.getJspFile(getContainingFile())).getDocument().getRootTag();
      if (rootTag != this) known.addAll(Arrays.asList((rootTag.knownNamespaces())));
    }
    return known.toArray(new String[known.size()]);
  }

  private void initNamespaceMaps(PsiElement parent) {
    if(myNamespaceMap == null && hasNamespaceDeclarations()){
      final BidirectionalMap<String, String> namespaceMap = new BidirectionalMap<String, String>();
      final XmlAttribute[] attributes = getAttributes();

      for (final XmlAttribute attribute : attributes) {
        if (attribute.isNamespaceDeclaration()) {
          final String name = attribute.getName();
          int splitIndex = name.indexOf(':');
          final String value = attribute.getValue();

          if (value != null) {
            if (splitIndex < 0) {
              namespaceMap.put("", value);
            }
            else {
              namespaceMap.put(XmlUtil.findLocalNameByQualifiedName(name), value);
            }
          }
        }
      }

      myNamespaceMap = namespaceMap; // assign to field should be as last statement, to prevent incomplete initialization due to ProcessCancelledException
    }

    if(parent instanceof XmlDocument && myNamespaceMap == null){
      final BidirectionalMap<String, String> namespaceMap = new BidirectionalMap<String, String>();
      final String[][] defaultNamespace = XmlUtil.getDefaultNamespaces((XmlDocument)parent);
      for (final String[] prefix2ns : defaultNamespace) {
        namespaceMap.put(prefix2ns[0], prefix2ns[1]);
      }

      myNamespaceMap = namespaceMap; // assign to field should be as last statement, to prevent incomplete initialization due to ProcessCancelledException
    }
  }

  @NotNull
  public String getLocalName() {
    final String name = getName();
    return name.substring(name.indexOf(':') + 1);
  }

  public boolean hasNamespaceDeclarations() {
    getAttributes();
    return myHaveNamespaceDeclarations;
  }

  @NotNull
  public Map<String, String> getLocalNamespaceDeclarations() {
    Map<String, String> namespaces = new HashMap<String, String>();
    for (final XmlAttribute attribute : getAttributes()) {
      if (!attribute.isNamespaceDeclaration() || attribute.getValue() == null) continue;
      namespaces.put(attribute.getLocalName(), attribute.getValue());
    }
    return namespaces;
  }

  public XmlAttribute setAttribute(String qname, String value) throws IncorrectOperationException {
    final XmlAttribute attribute = getAttribute(qname);

    if(attribute != null){
      if(value == null){
        deleteChildInternal(attribute.getNode());
        return null;
      }
      attribute.setValue(value);
      return attribute;
    }
    else if (value == null) {
      return null;
    }
    else {
      PsiElement xmlAttribute = add(getManager().getElementFactory().createXmlAttribute(qname, value));
      while(!(xmlAttribute instanceof XmlAttribute)) xmlAttribute = xmlAttribute.getNextSibling();
      return (XmlAttribute)xmlAttribute;
    }
  }

  public XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException {
    final String prefix = getPrefixByNamespace(namespace);
    if(prefix != null && prefix.length() > 0) name = prefix + ":" + name;
    return setAttribute(name, value);
  }

  public XmlTag createChildTag(String localName, String namespace, String bodyText, boolean enforceNamespacesDeep) {
    return XmlUtil.createChildTag(this, localName, namespace, bodyText, enforceNamespacesDeep);
  }

  @NotNull
  public XmlTagValue getValue() {
    if(myValue != null) return myValue;
    final PsiElement[] elements = getElements();
    final List<PsiElement> bodyElements = new ArrayList<PsiElement>(elements.length);

    boolean insideBody = false;
    for (final PsiElement element : elements) {
      final ASTNode treeElement = element.getNode();
      if (insideBody) {
        if (treeElement.getElementType() == XmlTokenType.XML_END_TAG_START) break;
        if (!(element instanceof XmlTagChild)) continue;
        bodyElements.add(element);
      }
      else if (treeElement.getElementType() == XmlTokenType.XML_TAG_END) insideBody = true;
    }

    return myValue = new XmlTagValueImpl(bodyElements.toArray(XmlTagChild.EMPTY_ARRAY), this);
  }

  private PsiElement[] getElements() {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    processElements(new PsiElementProcessor() {
      public boolean execute(PsiElement psiElement) {
        elements.add(psiElement);
        return true;
      }
    }, this);
    return elements.toArray(new PsiElement[elements.size()]);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlTag(this);
  }

  public String toString() {
    return "XmlTag:" + getName();
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean beforeB) {
    //ChameleonTransforming.transformChildren(this);
    TreeElement firstAppended = null;
    boolean before = beforeB != null ? beforeB.booleanValue() : true;
    try{
      TreeElement next;
      do {
        next = first.getTreeNext();

        if (firstAppended == null) {
          firstAppended = addInternal(first, anchor, before);
          anchor = firstAppended;
        }
        else {
          anchor = addInternal(first, anchor, false);
        }
      }
      while (first != last && (first = next) != null);
    }
    catch(IncorrectOperationException ioe){}
    finally{
      clearCaches();
    }
    return firstAppended;
  }

  private TreeElement addInternal(TreeElement child, ASTNode anchor, boolean before) throws IncorrectOperationException{
    final PomModel model = getProject().getModel();
    final InsertTransaction transaction;
    if (anchor != null && child.getElementType() == XmlElementType.XML_TEXT) {
      XmlText psi = null;
      if(anchor.getPsi() instanceof XmlText)
        psi = (XmlText)anchor.getPsi();
      else {
        final ASTNode other = before ? anchor.getTreePrev() : anchor.getTreeNext();
        if(other != null && other.getPsi() instanceof XmlText) {
          before = !before;
          psi = (XmlText)other.getPsi();
        }
      }

      if(psi != null){
        if(before)
          psi.insertText(((XmlText)child.getPsi()).getValue(), 0);
        else
          psi.insertText(((XmlText)child.getPsi()).getValue(), psi.getValue().length());
        return (TreeElement)psi.getNode();
      }
    }
    LOG.assertTrue(child.getPsi() instanceof XmlAttribute || child.getPsi() instanceof XmlTagChild);
    if (child.getElementType() == XmlElementType.XML_ATTRIBUTE) {
      transaction = new InsertAttributeTransaction(child, anchor, before, model);
    }
    else if (anchor == null){
      transaction = getBodyInsertTransaction(child);
    }
    else{
      transaction = new GenericInsertTransaction(child, anchor, before);
    }
    model.runTransaction(transaction);
    return transaction.getFirstInserted();
  }

  protected InsertTransaction getBodyInsertTransaction(final TreeElement child) {
    return new BodyInsertTransaction(child);
  }

  public void deleteChildInternal(final ASTNode child) {
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);

    if(child.getElementType() == XmlElementType.XML_ATTRIBUTE){
      try {
        model.runTransaction(new PomTransactionBase(this, aspect) {
          public PomModelEvent runInner() {
            final String name = ((XmlAttribute)child).getName();
            XmlTagImpl.super.deleteChildInternal(child);
            return XmlAttributeSetImpl.createXmlAttributeSet(model, XmlTagImpl.this, name, null);
          }
        });
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    else{
      final ASTNode treePrev = child.getTreePrev();
      final ASTNode treeNext = child.getTreeNext();
      XmlTagImpl.super.deleteChildInternal(child);
      if(treePrev != null && treeNext != null &&
         treePrev.getElementType() == XmlElementType.XML_TEXT && treeNext.getElementType() == XmlElementType.XML_TEXT){
        final XmlText prevText = (XmlText)treePrev.getPsi();
        final XmlText nextText = (XmlText)treeNext.getPsi();
        try {
          prevText.setValue(prevText.getValue() + nextText.getValue());
          nextText.delete();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private ASTNode expandTag() throws IncorrectOperationException{
    ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(XmlTagImpl.this);
    if(endTagStart == null){
      final XmlTagImpl tagFromText = (XmlTagImpl)getManager().getElementFactory().createTagFromText("<" + getName() + "></" + getName() + ">");
      final ASTNode startTagStart = XmlChildRole.START_TAG_END_FINDER.findChild(tagFromText);
      endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tagFromText);
      final LeafElement emptyTagEnd = (LeafElement)XmlChildRole.EMPTY_TAG_END_FINDER.findChild(this);
      if(emptyTagEnd != null) removeChild(emptyTagEnd);
      addChildren(startTagStart, null, null);
    }
    return endTagStart;
  }

  public XmlTag getParentTag() {
    final PsiElement parent = getParent();
    if(parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    final PsiElement nextSibling = getNextSibling();
    if(nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if(prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  protected class BodyInsertTransaction extends InsertTransaction{
    private TreeElement myChild;
    private ASTNode myNewElement;

    public BodyInsertTransaction(TreeElement child) {
      super(XmlTagImpl.this);
      this.myChild = child;
    }

    public PomModelEvent runInner() throws IncorrectOperationException {
      final ASTNode anchor = expandTag();
      if(myChild.getElementType() == XmlElementType.XML_TAG){
        // compute where to insert tag according to DTD or XSD
        final XmlElementDescriptor parentDescriptor = getDescriptor();
        final XmlTag[] subTags = getSubTags();
        final PsiElement declaration = parentDescriptor != null ? parentDescriptor.getDeclaration() : null;
        if ((declaration != null && declaration.getContainingFile() != null && declaration.getContainingFile().isPhysical()) // filtring out generated dtds
            && subTags.length > 0){
          final XmlElementDescriptor[] childElementDescriptors = parentDescriptor.getElementsDescriptors(XmlTagImpl.this);
          int subTagNum = -1;
          for (final XmlElementDescriptor childElementDescriptor : childElementDescriptors) {
            final String childElementName = childElementDescriptor.getName();
            while (subTagNum < subTags.length - 1 && subTags[subTagNum + 1].getName().equals(childElementName)) {
              subTagNum++;
            }
            if (childElementName.equals(XmlChildRole.START_TAG_NAME_FINDER.findChild(myChild).getText())) {
              // insert child just after anchor
              // insert into the position specified by index
              if (subTagNum >= 0) {
                final ASTNode subTag = (ASTNode)subTags[subTagNum];
                if (subTag.getTreeParent() != XmlTagImpl.this) {
                  // in entity
                  final XmlEntityRef entityRef = PsiTreeUtil.getParentOfType(subTags[subTagNum], XmlEntityRef.class);
                  throw new IncorrectOperationException(
                    "Can't insert subtag to entity! Entity reference text: " + entityRef != null ? entityRef.getText() : "");
                }
                myNewElement = XmlTagImpl.super.addInternal(myChild, myChild, subTag, Boolean.FALSE);
              }
              else {
                final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(XmlTagImpl.this);
                myNewElement = XmlTagImpl.super.addInternal(myChild, myChild, child, Boolean.FALSE);
              }
              return null;
            }
          }
        }
        else {
          final ASTNode child = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(XmlTagImpl.this);
          myNewElement = XmlTagImpl.super.addInternal(myChild, myChild, child, Boolean.TRUE);
          return null;
        }
      }
      myNewElement = XmlTagImpl.super.addInternal(myChild, myChild, anchor, Boolean.TRUE);
      return null;
    }

    public TreeElement getFirstInserted(){
      return (TreeElement)myNewElement;
    }
  }

  protected XmlText splitText(final XmlTextImpl childText, final int displayOffset) throws IncorrectOperationException{
    if(displayOffset == 0) return childText;
    if(displayOffset >= childText.getValue().length()) return null;

    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);

    class MyTransaction extends PomTransactionBase {
      private XmlTextImpl myRight;

      public MyTransaction() {
        super(XmlTagImpl.this, aspect);
      }

      public PomModelEvent runInner() throws IncorrectOperationException{
        final PsiFile containingFile = getContainingFile();
        final FileElement holder = new DummyHolder(containingFile.getManager(), null, ((PsiFileImpl)containingFile).getTreeElement().getCharTable()).getTreeElement();
        final XmlTextImpl rightText = (XmlTextImpl)Factory.createCompositeElement(XmlElementType.XML_TEXT);
        TreeUtil.addChildren(holder, rightText);

        addChild(rightText, childText.getTreeNext());

        final String value = childText.getValue();

        childText.setValue(value.substring(0, displayOffset));
        rightText.setValue(value.substring(displayOffset));

        myRight = rightText;
        return null;
      }

      public XmlText getResult() {
        return myRight;
      }
    }
    final MyTransaction transaction = new MyTransaction();
    model.runTransaction(transaction);
    return transaction.getResult();
  }

  protected class InsertAttributeTransaction extends InsertTransaction{
    private final TreeElement myChild;
    private final ASTNode myAnchor;
    private final boolean myBefore;
    private final PomModel myModel;
    private TreeElement myFirstInserted = null;

    public InsertAttributeTransaction(final TreeElement child, final ASTNode anchor, final boolean before, final PomModel model) {
      super(XmlTagImpl.this);
      myChild = child;
      myAnchor = anchor;
      myBefore = before;
      myModel = model;
    }

    public PomModelEvent runInner(){
      final String value = ((XmlAttribute)myChild).getValue();
      final String name = ((XmlAttribute)myChild).getName();
      if (myAnchor == null) {
        ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(XmlTagImpl.this);
        if (startTagEnd == null) startTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(XmlTagImpl.this);

        if (startTagEnd == null) {
          myFirstInserted = XmlTagImpl.super.addInternal(myChild, myChild, null, null);
        }
        else {
          myFirstInserted = XmlTagImpl.super.addInternal(myChild, myChild, startTagEnd, Boolean.TRUE);
        }
      }
      else {
        myFirstInserted = XmlTagImpl.super.addInternal(myChild, myChild, myAnchor, Boolean.valueOf(myBefore));
      }
      return XmlAttributeSetImpl.createXmlAttributeSet(myModel, XmlTagImpl.this, name, value);
    }

    public TreeElement getFirstInserted(){
      return myFirstInserted;
    }
  }

  protected class GenericInsertTransaction extends InsertTransaction{
    private final TreeElement myChild;
    private final ASTNode myAnchor;
    private final boolean myBefore;
    private TreeElement myRetHolder;

    public GenericInsertTransaction(final TreeElement child, final ASTNode anchor, final boolean before) {
      super(XmlTagImpl.this);
      myChild = child;
      myAnchor = anchor;
      myBefore = before;
    }

    public PomModelEvent runInner() {
      myRetHolder = XmlTagImpl.super.addInternal(myChild, myChild, myAnchor, Boolean.valueOf(myBefore));
      return null;
    }

    public TreeElement getFirstInserted() {
      return myRetHolder;
    }
  }

  protected abstract class InsertTransaction extends PomTransactionBase{
    public InsertTransaction(final PsiElement scope) {
      super(scope, getProject().getModel().getModelAspect(XmlAspect.class));
    }

    public abstract TreeElement getFirstInserted();
  }
}
