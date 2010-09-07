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

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ImplicitNamespaceDescriptorProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.XmlAttributeSetImpl;
import com.intellij.pom.xml.impl.events.XmlTagNameChangedImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Mike
 */

public class XmlTagImpl extends XmlElementImpl implements XmlTag {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTagImpl");

  private volatile String myName = null;
  private volatile XmlAttribute[] myAttributes = null;
  private volatile Map<String, String> myAttributeValueMap = null;
  private volatile XmlTag[] myTags = null;
  private volatile XmlTagValue myValue = null;
  private volatile Map<String, CachedValue<XmlNSDescriptor>> myNSDescriptorsMap = null;
  private volatile String myCachedNamespace;
  private volatile long myModCount;

  private volatile XmlElementDescriptor myCachedDescriptor;
  private volatile long myDescriptorModCount = -1;
  private volatile long myExtResourcesModCount = -1;

  private volatile boolean myHaveNamespaceDeclarations = false;
  private volatile BidirectionalMap<String, String> myNamespaceMap = null;
  @NonNls private static final String XML_NS_PREFIX = "xml";

  private final int myHC = ourHC++;

  @Override
  public final int hashCode() {
    return myHC;
  }

  public XmlTagImpl() {
    this(XmlElementType.XML_TAG);
  }

  protected XmlTagImpl(IElementType type) {
    super(type);
  }

  public void clearCaches() {
    myName = null;
    myNamespaceMap = null;
    myCachedNamespace = null;
    myCachedDescriptor = null;
    myDescriptorModCount = -1;
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
    ProgressManager.checkCanceled();
    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(this);
    if (startTagName == null) return PsiReference.EMPTY_ARRAY;
    final ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(this);
    List<PsiReference> refs = new ArrayList<PsiReference>();
    String prefix = getNamespacePrefix();

    TagNameReference startTagRef = TagNameReference.createTagNameReference(this, startTagName, true);
    refs.add(startTagRef);
    if (prefix.length() > 0) {
      refs.add(createPrefixReference(startTagName, prefix, startTagRef));
    }
    if (endTagName != null) {
      TagNameReference endTagRef = TagNameReference.createTagNameReference(this, endTagName, false);
      refs.add(endTagRef);
      prefix = XmlUtil.findPrefixByQualifiedName(endTagName.getText());
      if (StringUtil.isNotEmpty(prefix)) {
        refs.add(createPrefixReference(endTagName, prefix, endTagRef));
      }
    }


    // ArrayList.addAll() makes a clone of the collection
    //noinspection ManualArrayToCollectionCopy
    for (PsiReference ref : ReferenceProvidersRegistry.getReferencesFromProviders(this, XmlTag.class)) {
      refs.add(ref);
    }

    return ContainerUtil.toArray(refs, new PsiReference[refs.size()]);
  }

  private SchemaPrefixReference createPrefixReference(ASTNode startTagName, String prefix, TagNameReference tagRef) {
    return new SchemaPrefixReference(this, TextRange.from(startTagName.getStartOffset() - this.getStartOffset(), prefix.length()), prefix, tagRef);
  }

  public XmlNSDescriptor getNSDescriptor(final String namespace, boolean strict) {
    final XmlTag parentTag = getParentTag();

    if (parentTag == null && namespace.equals(XmlUtil.XHTML_URI)) {
      final XmlNSDescriptor descriptor = getDtdDescriptor(XmlUtil.getContainingFile(this));
      if (descriptor != null) {
        return descriptor;
      }
    }

    Map<String, CachedValue<XmlNSDescriptor>> map = initNSDescriptorsMap();
    final CachedValue<XmlNSDescriptor> descriptor = map.get(namespace);
    if (descriptor != null) {
      final XmlNSDescriptor value = descriptor.getValue();
      if (value != null) {
        return value;
      }
    }

    if (parentTag == null) {
      final XmlDocument parentOfType = PsiTreeUtil.getParentOfType(this, XmlDocument.class);
      if (parentOfType == null) {
        return null;
      }
      return parentOfType.getDefaultNSDescriptor(namespace, strict);
    }

    return parentTag.getNSDescriptor(namespace, strict);
  }

  @Nullable
  private static XmlNSDescriptor getDtdDescriptor(@NotNull XmlFile containingFile) {
    final XmlDocument document = containingFile.getDocument();
    if (document == null) {
      return null;
    }
    final String url = XmlUtil.getDtdUri(document);
    if (url == null) {
      return null;
    }
    return document.getDefaultNSDescriptor(url, true);
  }

  public boolean isEmpty() {
    return XmlChildRole.CLOSING_TAG_START_FINDER.findChild(this) == null;
  }

  public void collapseIfEmpty() {
    final XmlTag[] tags = getSubTags();
    if (tags.length > 0) {
      return;
    }
    final ASTNode closingName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(this);
    final ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(this);
    if (closingName == null || startTagEnd == null) {
      return;
    }

    final PomModel pomModel = PomManager.getModel(getProject());
    final PomTransactionBase transaction = new PomTransactionBase(this, pomModel.getModelAspect(XmlAspect.class)) {

      @Nullable
      public PomModelEvent runInner() {
        final ASTNode closingBracket = closingName.getTreeNext();
        removeRange(startTagEnd, closingBracket);
        final LeafElement emptyTagEnd = Factory.createSingleLeafElement(XmlTokenType.XML_EMPTY_ELEMENT_END, "/>", 0, 2, null, getManager());
        replaceChild(closingBracket, emptyTagEnd);
        return null;
      }
    };
    try {
      pomModel.runTransaction(transaction);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  @NonNls
  public String getSubTagText(@NonNls String qname) {
    final XmlTag tag = findFirstSubTag(qname);
    if (tag == null) return null;
    return tag.getValue().getText();
  }

  protected final Map<String, CachedValue<XmlNSDescriptor>> initNSDescriptorsMap() {
    Map<String, CachedValue<XmlNSDescriptor>> map = myNSDescriptorsMap;
    if (map == null) {
      boolean exceptionOccurred = false;
      try {
        // XSD aware attributes processing

        final String noNamespaceDeclaration = getAttributeValue("noNamespaceSchemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
        final String schemaLocationDeclaration = getAttributeValue("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);

        if (noNamespaceDeclaration != null) {
          map = initializeSchema(XmlUtil.EMPTY_URI, null, noNamespaceDeclaration, map);
        }
        if (schemaLocationDeclaration != null) {
          final StringTokenizer tokenizer = new StringTokenizer(schemaLocationDeclaration);
          while (tokenizer.hasMoreTokens()) {
            final String uri = tokenizer.nextToken();
            if (tokenizer.hasMoreTokens()) {
              map = initializeSchema(uri, null, tokenizer.nextToken(), map);
            }
          }
        }
        // namespace attributes processing (XSD declaration via ExternalResourceManager)

        if (hasNamespaceDeclarations()) {
          for (final XmlAttribute attribute : getAttributes()) {
            if (attribute.isNamespaceDeclaration()) {
              String ns = attribute.getValue();
              if (ns == null) ns = XmlUtil.EMPTY_URI;
              ns = getRealNs(ns);

              if (map == null || !map.containsKey(ns)) {
                map = initializeSchema(ns, getNSVersion(ns, this), ns, map);
              }
            }
          }
        }
      }
      catch (RuntimeException e) {
        myNSDescriptorsMap = null;
        exceptionOccurred = true;
        throw e;
      }
      finally {
        if (map == null && !exceptionOccurred) {
          map = Collections.emptyMap();
        }
      }
      myNSDescriptorsMap = map;
    }
    return map;
  }

  @Nullable
  private static String getNSVersion(String ns, final XmlTagImpl xmlTag) {
    String versionValue = xmlTag.getAttributeValue("version");
    if (versionValue != null && xmlTag.getNamespace().equals(ns)) {
      return versionValue;
    }
    return null;
  }

  private Map<String, CachedValue<XmlNSDescriptor>> initializeSchema(final String namespace,
                                                                     final String version,
                                                                     final String fileLocation,
                                                                     Map<String, CachedValue<XmlNSDescriptor>> map) {
    if (map == null) map = new THashMap<String, CachedValue<XmlNSDescriptor>>();
    final ExternalResourceManagerEx externalResourceManager = ExternalResourceManagerEx.getInstanceEx();

    // We put cached value in any case to cause its value update on e.g. mapping change
    map.put(namespace, CachedValuesManager.getManager(getManager().getProject()).createCachedValue(new CachedValueProvider<XmlNSDescriptor>() {
      public Result<XmlNSDescriptor> compute() {
        XmlNSDescriptor descriptor = getImplicitNamespaceDescriptor(fileLocation);
        if (descriptor != null) {
          return new Result<XmlNSDescriptor>(descriptor, ArrayUtil.append(descriptor.getDependences(), XmlTagImpl.this));
        }

        XmlFile currentFile = retrieveFile(fileLocation, version);
        if (currentFile == null) {
          final XmlDocument document = XmlUtil.getContainingFile(XmlTagImpl.this).getDocument();
          if (document != null) {
            final String uri = XmlUtil.getDtdUri(document);
            if (uri != null) {
              final XmlFile containingFile = XmlUtil.getContainingFile(document);
              final XmlFile xmlFile = XmlUtil.findNamespace(containingFile, uri);
              descriptor = xmlFile == null ? null : (XmlNSDescriptor)xmlFile.getDocument().getMetaData();
            }
            if (descriptor != null) {
              final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(XmlTagImpl.this);
              if (elementDescriptor != null) {
                final XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("xmlns", XmlTagImpl.this);
                if (attributeDescriptor != null && attributeDescriptor.isFixed()) {
                  final String defaultValue = attributeDescriptor.getDefaultValue();
                  if (defaultValue != null && defaultValue.equals(namespace)) {
                    return new Result<XmlNSDescriptor>(descriptor, descriptor.getDependences(), XmlTagImpl.this,
                                                       ExternalResourceManager.getInstance());
                  }
                }
              }
            }
          }
        }
        PsiMetaOwner currentOwner = retrieveOwner(currentFile, namespace);
        if (currentOwner != null) {
          descriptor = (XmlNSDescriptor)currentOwner.getMetaData();
          if (descriptor != null) {
            return new Result<XmlNSDescriptor>(descriptor, descriptor.getDependences(), XmlTagImpl.this,
                                               ExternalResourceManager.getInstance());
          }
        }
        return new Result<XmlNSDescriptor>(null, XmlTagImpl.this, currentFile, ExternalResourceManager.getInstance());
      }
    }, false));

    return map;
  }

  @Nullable
  private XmlNSDescriptor getImplicitNamespaceDescriptor(String ns) {
    Module module = ModuleUtil.findModuleForPsiElement(this);
    if (module != null) {
      for (ImplicitNamespaceDescriptorProvider provider : Extensions.getExtensions(ImplicitNamespaceDescriptorProvider.EP_NAME)) {
        XmlNSDescriptor nsDescriptor = provider.getNamespaceDescriptor(module, ns);
        if (nsDescriptor != null) return nsDescriptor;
      }
    }
    return null;
  }

  @Nullable
  private XmlFile retrieveFile(final String fileLocation, String version) {
    final String targetNs = XmlUtil.getTargetSchemaNsFromTag(this);
    if (fileLocation.equals(targetNs)) {
      return null;
    }
    else {
      final XmlFile file = XmlUtil.getContainingFile(this);
      final PsiFile psiFile = ExternalResourceManager.getInstance().getResourceLocation(fileLocation, file, version);
      return psiFile instanceof XmlFile ? (XmlFile)psiFile : null;
    }
  }

  @Nullable
  private PsiMetaOwner retrieveOwner(final XmlFile file, final String namespace) {
    if (file == null) {
      return namespace.equals(XmlUtil.getTargetSchemaNsFromTag(this)) ? this : null;
    }
    return file.getDocument();
  }

  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    return references.length > 0 ? references[0] : null;
  }

  public XmlElementDescriptor getDescriptor() {
    final long curModCount = getManager().getModificationTracker().getModificationCount();
    long curExtResourcesModCount = ExternalResourceManagerEx.getInstanceEx().getModificationCount(getProject());
    if (myDescriptorModCount != curModCount || myExtResourcesModCount != curExtResourcesModCount) {
      myCachedDescriptor = computeElementDescriptor();
      myDescriptorModCount = curModCount;
      myExtResourcesModCount = curExtResourcesModCount;
    }
    return myCachedDescriptor;
  }

  @Nullable
  protected XmlElementDescriptor computeElementDescriptor() {
    for (XmlElementDescriptorProvider provider : Extensions.getExtensions(XmlElementDescriptorProvider.EP_NAME)) {
      XmlElementDescriptor elementDescriptor = provider.getDescriptor(this);
      if (elementDescriptor != null) {
        return elementDescriptor;
      }
    }

    final String namespace = getNamespace();
    if (XmlUtil.EMPTY_URI.equals(namespace)) { //nonqualified items
      final XmlTag parent = getParentTag();
      if (parent != null) {
        final XmlElementDescriptor descriptor = parent.getDescriptor();
        if (descriptor != null) {
          XmlElementDescriptor fromParent = descriptor.getElementDescriptor(this, parent);
          if (fromParent != null && !(fromParent instanceof AnyXmlElementDescriptor)) {
            return fromParent;
          }
        }
      }
    }

    XmlElementDescriptor elementDescriptor = null;
    final XmlNSDescriptor nsDescriptor = getNSDescriptor(namespace, false);
    if (nsDescriptor != null) {
      if (!DumbService.getInstance(getProject()).isDumb() || DumbService.isDumbAware(nsDescriptor)) {
        elementDescriptor = nsDescriptor.getElementDescriptor(this);
      }
    }
    if (elementDescriptor == null) {
      return XmlUtil.findXmlDescriptorByType(this);
    }

    return elementDescriptor;
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XmlTokenType.XML_NAME || i == XmlTokenType.XML_TAG_NAME) {
      return XmlChildRole.XML_TAG_NAME;
    }
    else if (i == XmlElementType.XML_ATTRIBUTE) {
      return XmlChildRole.XML_ATTRIBUTE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @NotNull
  public String getName() {
    String name = myName;
    if (name == null) {
      final ASTNode nameElement = XmlChildRole.START_TAG_NAME_FINDER.findChild(this);
      if (nameElement != null) {
        name = nameElement.getText();
      }
      else {
        name = "";
      }
      myName = name;
    }
    return name;
  }

  public PsiElement setName(@NotNull final String name) throws IncorrectOperationException {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      public PomModelEvent runInner() throws IncorrectOperationException {
        final String oldName = getName();
        final XmlTagImpl dummyTag =
          (XmlTagImpl)XmlElementFactory.getInstance(getProject()).createTagFromText(XmlTagUtil.composeTagText(name, "aa"));
        final XmlTagImpl tag = XmlTagImpl.this;
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(tag);
        tag.replaceChild(XmlChildRole.START_TAG_NAME_FINDER.findChild(tag),
                         ChangeUtil.copyElement((TreeElement)XmlChildRole.START_TAG_NAME_FINDER.findChild(dummyTag), charTableByTree));
        final ASTNode childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag);
        if (childByRole != null) {
          final TreeElement treeElement = (TreeElement)XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(dummyTag);
          if (treeElement != null) {
            tag.replaceChild(childByRole, ChangeUtil.copyElement(treeElement, charTableByTree));
          }
        }

        return XmlTagNameChangedImpl.createXmlTagNameChanged(model, tag, oldName);
      }
    });
    return this;
  }

  public XmlAttribute[] getAttributes() {
    XmlAttribute[] attributes = myAttributes;
    if (attributes == null) {
      Map<String, String> attributesValueMap = new THashMap<String, String>();
      attributes = calculateAttributes(attributesValueMap);
      myAttributeValueMap = attributesValueMap;
      myAttributes = attributes;
    }
    return attributes;
  }

  @NotNull
  protected XmlAttribute[] calculateAttributes(final Map<String, String> attributesValueMap) {
    final List<XmlAttribute> result = new ArrayList<XmlAttribute>(10);
    processChildren(new PsiElementProcessor() {
      public boolean execute(PsiElement element) {
        if (element instanceof XmlAttribute) {
          XmlAttribute attribute = (XmlAttribute)element;
          result.add(attribute);
          cacheOneAttributeValue(attribute.getName(), attribute.getValue(), attributesValueMap);
          myHaveNamespaceDeclarations = myHaveNamespaceDeclarations || attribute.isNamespaceDeclaration();
        }
        else if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_TAG_END) {
          return false;
        }
        return true;
      }
    });
    if (result.isEmpty()) {
      return XmlAttribute.EMPTY_ARRAY;
    }
    else {
      return ContainerUtil.toArray(result, new XmlAttribute[result.size()]);
    }
  }

  protected void cacheOneAttributeValue(String name, String value, final Map<String, String> attributesValueMap) {
    attributesValueMap.put(name, value);
  }

  public String getAttributeValue(String qname) { //todo ?
    Map<String, String> map = myAttributeValueMap;
    while (map == null) {
      final XmlAttribute[] xmlAttributes = getAttributes();
      map = myAttributeValueMap;

      if (map == null && xmlAttributes != null) {
        myAttributes = null;
      }
    }
    return map.get(qname);
  }

  public String getAttributeValue(String _name, String namespace) {
    if (namespace == null) {
      return getAttributeValue(_name);
    }

    XmlTagImpl current = this;
    PsiElement parent = getParent();

    while (current != null) {
      BidirectionalMap<String, String> map = current.initNamespaceMaps(parent);
      if (map != null) {
        List<String> keysByValue = map.getKeysByValue(namespace);
        if (keysByValue != null && !keysByValue.isEmpty()) {

          for (String prefix : keysByValue) {
            if (prefix != null && prefix.length() > 0) {
              final String value = getAttributeValue(prefix + ":" + _name);
              if (value != null) return value;
            }
          }
        }
      }

      current = parent instanceof XmlTag ? (XmlTagImpl)parent : null;
      parent = parent.getParent();
    }

    if (namespace.length() == 0 || getNamespace().equals(namespace)) {
      return getAttributeValue(_name);
    }
    return null;
  }

  @NotNull
  public XmlTag[] getSubTags() {
    XmlTag[] tags = myTags;
    if (tags == null) {
      final List<XmlTag> result = new ArrayList<XmlTag>();

      fillSubTags(result);

      final int s = result.size();
      myTags = tags = s > 0 ? ContainerUtil.toArray(result, new XmlTag[s]) : EMPTY;
    }
    return tags;
  }

  protected void fillSubTags(final List<XmlTag> result) {
    processElements(new PsiElementProcessor() {
      public boolean execute(PsiElement element) {
        if (element instanceof XmlTag) result.add((XmlTag)element);
        return true;
      }
    }, this);
  }

  @NotNull
  public XmlTag[] findSubTags(String name) {
    return findSubTags(name, null);
  }

  @NotNull
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
    return ContainerUtil.toArray(result, new XmlTag[result.size()]);
  }

  public XmlTag findFirstSubTag(String name) {
    final XmlTag[] subTags = findSubTags(name);
    if (subTags.length > 0) return subTags[0];
    return null;
  }

  public XmlAttribute getAttribute(String name, String namespace) {
    if ((name != null && name.indexOf(':') != -1) ||
        namespace == null ||
        XmlUtil.EMPTY_URI.equals(namespace) ||
        XmlUtil.ANY_URI.equals(namespace)) {
      return getAttribute(name);
    }

    final String prefix = getPrefixByNamespace(namespace);
    if (prefix == null || prefix.length() == 0) return null;
    return getAttribute(prefix + ":" + name);
  }

  @Nullable
  public XmlAttribute getAttribute(String qname) {
    if (qname == null) return null;
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(this);
    final XmlAttribute[] attributes = getAttributes();

    final CharSequence charTableIndex = charTableByTree.intern(qname);
    final boolean caseSensitive = isCaseSensitive();

    for (final XmlAttribute attribute : attributes) {
      final LeafElement attrNameElement = (LeafElement)XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(attribute.getNode());
      if (attrNameElement != null &&
          (caseSensitive && attrNameElement.getChars().equals(charTableIndex) ||
           !caseSensitive && Comparing.equal(attrNameElement.getChars(), charTableIndex, false))) {
        return attribute;
      }
    }
    return null;
  }

  protected boolean isCaseSensitive() {
    return true;
  }

  @NotNull
  public String getNamespace() {
    LOG.assertTrue(isValid());
    String cachedNamespace = myCachedNamespace;
    final long curModCount = getManager().getModificationTracker().getModificationCount();
    if (cachedNamespace != null && myModCount == curModCount) {
      return cachedNamespace;
    }
    final String prefix = getNamespacePrefix();
    cachedNamespace = getNamespaceByPrefix(prefix);
    myCachedNamespace = cachedNamespace;
    myModCount = curModCount;
    return cachedNamespace;
  }

  @NotNull
  public String getNamespacePrefix() {
    return XmlUtil.findPrefixByQualifiedName(getName());
  }

  private static final ThreadLocal<Boolean> ourGetNsByPrefixRecursionLock = new ThreadLocal<Boolean>();

  @NotNull
  public String getNamespaceByPrefix(String prefix) {
    final PsiElement parent = getParent();
    LOG.assertTrue(parent.isValid());
    BidirectionalMap<String, String> map = initNamespaceMaps(parent);
    if (map != null) {
      final String ns = map.get(prefix);
      if (ns != null) return ns;
    }
    if (parent instanceof XmlTag) return ((XmlTag)parent).getNamespaceByPrefix(prefix);
    //The prefix 'xml' is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared
    if (XML_NS_PREFIX.equals(prefix)) return XmlUtil.XML_NAMESPACE_URI;

    if (prefix.length() > 0 &&
        !hasNamespaceDeclarations() &&
        getNamespacePrefix().equals(prefix) &&
        ourGetNsByPrefixRecursionLock.get() == null) {
      // When there is no namespace declarations then qualified names should be just used in dtds
      // this implies that we may have "" namespace prefix ! (see last paragraph in Namespaces in Xml, Section 5)
      ourGetNsByPrefixRecursionLock.set(Boolean.TRUE);

      try {
        final String nsFromEmptyPrefix = getNamespaceByPrefix("");
        final XmlNSDescriptor nsDescriptor = getNSDescriptor(nsFromEmptyPrefix, false);
        final XmlElementDescriptor descriptor = nsDescriptor != null ? nsDescriptor.getElementDescriptor(this) : null;
        final String nameFromRealDescriptor =
          descriptor != null && descriptor.getDeclaration() != null && descriptor.getDeclaration().isPhysical() ? descriptor.getName() : "";
        if (nameFromRealDescriptor.equals(getName())) return nsFromEmptyPrefix;
      }
      finally {
        ourGetNsByPrefixRecursionLock.set(null);
      }
    }
    return XmlUtil.EMPTY_URI;
  }

  public String getPrefixByNamespace(String namespace) {
    final PsiElement parent = getParent();
    BidirectionalMap<String, String> map = initNamespaceMaps(parent);
    if (map != null) {
      List<String> keysByValue = map.getKeysByValue(namespace);
      final String ns = keysByValue == null || keysByValue.isEmpty() ? null : keysByValue.get(0);
      if (ns != null) return ns;
    }
    if (parent instanceof XmlTag) return ((XmlTag)parent).getPrefixByNamespace(namespace);
    //The prefix 'xml' is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared
    if (XmlUtil.XML_NAMESPACE_URI.equals(namespace)) return XML_NS_PREFIX;
    return null;
  }

  public String[] knownNamespaces() {
    final PsiElement parentElement = getParent();
    BidirectionalMap<String, String> map = initNamespaceMaps(parentElement);
    Set<String> known = Collections.emptySet();
    if (map != null) {
      known = new HashSet<String>(map.values());
    }
    if (parentElement instanceof XmlTag) {
      if (known.isEmpty()) return ((XmlTag)parentElement).knownNamespaces();
      ContainerUtil.addAll(known, ((XmlTag)parentElement).knownNamespaces());
    }
    else {
      final XmlFile xmlFile = XmlExtension.getExtensionByElement(this).getContainingFile(this);
      final XmlTag rootTag = xmlFile.getDocument().getRootTag();
      if (rootTag != null && rootTag != this) {
        if (known.isEmpty()) return rootTag.knownNamespaces();
        ContainerUtil.addAll(known, rootTag.knownNamespaces());
      }
    }
    return ArrayUtil.toStringArray(known);
  }

  @Nullable
  private BidirectionalMap<String, String> initNamespaceMaps(PsiElement parent) {
    BidirectionalMap<String, String> map = myNamespaceMap;
    if (map == null && hasNamespaceDeclarations()) {
      final BidirectionalMap<String, String> namespaceMap = new BidirectionalMap<String, String>();
      final XmlAttribute[] attributes = getAttributes();

      for (final XmlAttribute attribute : attributes) {
        if (attribute.isNamespaceDeclaration()) {
          final String name = attribute.getName();
          int splitIndex = name.indexOf(':');
          final String value = getRealNs(attribute.getValue());

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

      myNamespaceMap = map =
        namespaceMap; // assign to field should be as last statement, to prevent incomplete initialization due to ProcessCancelledException
    }

    if (parent instanceof XmlDocument) {
      final XmlExtension extension = XmlExtension.getExtensionByElement(parent);
      if (extension != null) {
        final String[][] defaultNamespace = extension.getNamespacesFromDocument((XmlDocument)parent, map != null);
        if (defaultNamespace != null) {
          final BidirectionalMap<String, String> namespaceMap = new BidirectionalMap<String, String>();
          if (map != null) {
            namespaceMap.putAll(map);
          }
          for (final String[] prefix2ns : defaultNamespace) {
            namespaceMap.put(prefix2ns[0], getRealNs(prefix2ns[1]));
          }
          myNamespaceMap = map =
            namespaceMap; // assign to field should be as last statement, to prevent incomplete initialization due to ProcessCancelledException
        }
      }
    }
    return map;
  }

  protected String getRealNs(final String value) {
    return value;
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
    Map<String, String> namespaces = new THashMap<String, String>();
    for (final XmlAttribute attribute : getAttributes()) {
      if (!attribute.isNamespaceDeclaration() || attribute.getValue() == null) continue;
      // xmlns -> "", xmlns:a -> a
      final String localName = attribute.getLocalName();
      namespaces.put(localName.equals(attribute.getName()) ? "" : localName, attribute.getValue());
    }
    return namespaces;
  }

  public XmlAttribute setAttribute(String qname, String value) throws IncorrectOperationException {
    final XmlAttribute attribute = getAttribute(qname);

    if (attribute != null) {
      if (value == null) {
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
      PsiElement xmlAttribute = add(XmlElementFactory.getInstance(getProject()).createXmlAttribute(qname, value));
      while (!(xmlAttribute instanceof XmlAttribute)) xmlAttribute = xmlAttribute.getNextSibling();
      return (XmlAttribute)xmlAttribute;
    }
  }

  public XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException {
    if (!Comparing.equal(namespace, "")) {
      final String prefix = getPrefixByNamespace(namespace);
      if (prefix != null && prefix.length() > 0) name = prefix + ":" + name;
    }
    return setAttribute(name, value);
  }

  public XmlTag createChildTag(String localName, String namespace, String bodyText, boolean enforceNamespacesDeep) {
    return XmlUtil.createChildTag(this, localName, namespace, bodyText, enforceNamespacesDeep);
  }

  @Override
  public XmlTag addSubTag(XmlTag subTag, boolean first) {
    XmlTagChild[] children = getSubTags();
    if (children.length == 0) {
      children = getValue().getChildren();
    }
    if (children.length == 0) {
      return (XmlTag)add(subTag);
    }
    else if (first) {
      return (XmlTag)addBefore(subTag, children[0]);
    }
    else {
      return (XmlTag)addAfter(subTag, ArrayUtil.getLastElement(children));
    }
  }

  @NotNull
  public XmlTagValue getValue() {
    XmlTagValue tagValue = myValue;
    if (tagValue == null) {
      final PsiElement[] elements = getElements();
      final List<XmlTagChild> bodyElements = new ArrayList<XmlTagChild>(elements.length);

      boolean insideBody = false;
      for (final PsiElement element : elements) {
        final ASTNode treeElement = element.getNode();
        if (insideBody) {
          if (treeElement.getElementType() == XmlTokenType.XML_END_TAG_START) break;
          if (!(element instanceof XmlTagChild)) continue;
          bodyElements.add((XmlTagChild)element);
        }
        else if (treeElement.getElementType() == XmlTokenType.XML_TAG_END) insideBody = true;
      }

      XmlTagChild[] tagChildren = ContainerUtil.toArray(bodyElements, new XmlTagChild[bodyElements.size()]);
      myValue = tagValue = new XmlTagValueImpl(tagChildren, this);
    }
    return tagValue;
  }

  private PsiElement[] getElements() {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    processElements(new PsiElementProcessor() {
      public boolean execute(PsiElement psiElement) {
        elements.add(psiElement);
        return true;
      }
    }, this);
    return ContainerUtil.toArray(elements, new PsiElement[elements.size()]);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "XmlTag:" + getName();
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean beforeB) {
    //ChameleonTransforming.transformChildren(this);
    TreeElement firstAppended = null;
    boolean before = beforeB == null || beforeB.booleanValue();
    try {
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
    catch (IncorrectOperationException ignored) {
    }
    finally {
      clearCaches();
    }
    return firstAppended;
  }

  private TreeElement addInternal(TreeElement child, ASTNode anchor, boolean before) throws IncorrectOperationException {
    final PomModel model = PomManager.getModel(getProject());
    if (anchor != null && child.getElementType() == XmlElementType.XML_TEXT) {
      XmlText psi = null;
      if (anchor.getPsi() instanceof XmlText) {
        psi = (XmlText)anchor.getPsi();
      }
      else {
        final ASTNode other = before ? anchor.getTreePrev() : anchor.getTreeNext();
        if (other != null && other.getPsi() instanceof XmlText) {
          before = !before;
          psi = (XmlText)other.getPsi();
        }
      }

      if (psi != null) {
        if (before) {
          psi.insertText(((XmlText)child.getPsi()).getValue(), 0);
        }
        else {
          psi.insertText(((XmlText)child.getPsi()).getValue(), psi.getValue().length());
        }
        return (TreeElement)psi.getNode();
      }
    }
    LOG.assertTrue(child.getPsi() instanceof XmlAttribute || child.getPsi() instanceof XmlTagChild);
    final InsertTransaction transaction;
    if (child.getElementType() == XmlElementType.XML_ATTRIBUTE) {
      transaction = new InsertAttributeTransaction(child, anchor, before, model);
    }
    else if (anchor == null) {
      transaction = getBodyInsertTransaction(child);
    }
    else {
      transaction = new GenericInsertTransaction(child, anchor, before);
    }
    model.runTransaction(transaction);
    return transaction.getFirstInserted();
  }

  protected InsertTransaction getBodyInsertTransaction(final TreeElement child) {
    return new BodyInsertTransaction(child);
  }

  public void deleteChildInternal(@NotNull final ASTNode child) {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);

    if (child.getElementType() == XmlElementType.XML_ATTRIBUTE) {
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
    else {
      final ASTNode treePrev = child.getTreePrev();
      final ASTNode treeNext = child.getTreeNext();
      XmlTagImpl.super.deleteChildInternal(child);
      if (treePrev != null &&
          treeNext != null &&
          treePrev.getElementType() == XmlElementType.XML_TEXT &&
          treeNext.getElementType() == XmlElementType.XML_TEXT) {
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

  private ASTNode expandTag() throws IncorrectOperationException {
    ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(this);
    if (endTagStart == null) {
      final XmlTagImpl tagFromText =
        (XmlTagImpl)XmlElementFactory.getInstance(getProject()).createTagFromText("<" + getName() + "></" + getName() + ">");
      final ASTNode startTagStart = XmlChildRole.START_TAG_END_FINDER.findChild(tagFromText);
      endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tagFromText);
      final LeafElement emptyTagEnd = (LeafElement)XmlChildRole.EMPTY_TAG_END_FINDER.findChild(this);
      if (emptyTagEnd != null) removeChild(emptyTagEnd);
      addChildren(startTagStart, null, null);
    }
    return endTagStart;
  }

  public XmlTag getParentTag() {
    final PsiElement parent = getParent();
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    final PsiElement nextSibling = getNextSibling();
    if (nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if (prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  public Icon getElementIcon(int flags) {
    return Icons.XML_TAG_ICON;
  }

  protected class BodyInsertTransaction extends InsertTransaction {
    private final TreeElement myChild;
    private ASTNode myNewElement;

    public BodyInsertTransaction(TreeElement child) {
      super(XmlTagImpl.this);
      myChild = child;
    }

    public PomModelEvent runInner() throws IncorrectOperationException {
      final ASTNode anchor = expandTag();
      if (myChild.getElementType() == XmlElementType.XML_TAG) {
        // compute where to insert tag according to DTD or XSD
        final XmlElementDescriptor parentDescriptor = getDescriptor();
        final XmlTag[] subTags = getSubTags();
        final PsiElement declaration = parentDescriptor != null ? parentDescriptor.getDeclaration() : null;
        // filtring out generated dtds
        if (declaration != null &&
            declaration.getContainingFile() != null &&
            declaration.getContainingFile().isPhysical() &&
            subTags.length > 0) {
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

    public TreeElement getFirstInserted() {
      return (TreeElement)myNewElement;
    }
  }

  protected class InsertAttributeTransaction extends InsertTransaction {
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

    public PomModelEvent runInner() {
      final String value = ((XmlAttribute)myChild).getValue();
      final String name = ((XmlAttribute)myChild).getName();
      if (myAnchor == null) {
        ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(XmlTagImpl.this);
        if (startTagEnd == null) startTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(XmlTagImpl.this);

        if (startTagEnd == null) {
          ASTNode anchor = getLastChildNode();

          while (anchor instanceof PsiWhiteSpace) {
            anchor = anchor.getTreePrev();
          }

          if (anchor instanceof PsiErrorElement) {
            final LeafElement token = Factory
              .createSingleLeafElement(XmlTokenType.XML_EMPTY_ELEMENT_END, "/>", 0, 2, SharedImplUtil.findCharTableByTree(anchor),
                                       getManager());
            replaceChild(anchor, token);
            startTagEnd = token;
          }
        }

        if (startTagEnd == null) {
          ASTNode anchor = XmlChildRole.START_TAG_NAME_FINDER.findChild(XmlTagImpl.this);
          myFirstInserted = XmlTagImpl.super.addInternal(myChild, myChild, anchor, Boolean.FALSE);
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

    public TreeElement getFirstInserted() {
      return myFirstInserted;
    }
  }

  protected class GenericInsertTransaction extends InsertTransaction {
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

  protected abstract class InsertTransaction extends PomTransactionBase {
    public InsertTransaction(final PsiElement scope) {
      super(scope, PomManager.getModel(getProject()).getModelAspect(XmlAspect.class));
    }

    public abstract TreeElement getFirstInserted();
  }
}
