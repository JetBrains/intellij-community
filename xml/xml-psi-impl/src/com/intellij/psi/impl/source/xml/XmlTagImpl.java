/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.XmlAttributeSetImpl;
import com.intellij.pom.xml.impl.events.XmlTagNameChangedImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.index.XmlNamespaceIndex;
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

public class XmlTagImpl extends XmlElementImpl implements XmlTag, HintedReferenceHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTagImpl");
  @NonNls private static final String XML_NS_PREFIX = "xml";
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("xmlTag");
  private static final Key<ParameterizedCachedValue<XmlTag[], XmlTagImpl>> SUBTAGS_KEY = Key.create("subtags");
  private static final ParameterizedCachedValueProvider<XmlTag[],XmlTagImpl> CACHED_VALUE_PROVIDER =
    new ParameterizedCachedValueProvider<XmlTag[], XmlTagImpl>() {
      @Override
      public CachedValueProvider.Result<XmlTag[]> compute(XmlTagImpl tag) {
        final List<XmlTag> result = new ArrayList<>();

        tag.fillSubTags(result);

        final int s = result.size();
        XmlTag[] tags = s > 0 ? ContainerUtil.toArray(result, new XmlTag[s]) : EMPTY;
        return CachedValueProvider.Result
          .create(tags, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, tag.getContainingFile());
      }
    };
  private static final Comparator<TextRange> RANGE_COMPARATOR = (range1, range2) -> range1.getStartOffset() - range2.getStartOffset();
  private final int myHC = ourHC++;
  private volatile String myName;
  private volatile String myLocalName;
  private volatile XmlAttribute[] myAttributes;
  private volatile TextRange[] myTextElements;
  private volatile Map<String, String> myAttributeValueMap;
  private volatile XmlTagValue myValue;
  private volatile boolean myHasNamespaceDeclarations;

  public XmlTagImpl() {
    this(XmlElementType.XML_TAG);
  }

  protected XmlTagImpl(IElementType type) {
    super(type);
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

  @Nullable
  private static String getNSVersion(String ns, final XmlTagImpl xmlTag) {
    String versionValue = xmlTag.getAttributeValue("version");
    if (versionValue != null && xmlTag.getNamespace().equals(ns)) {
      return versionValue;
    }
    return null;
  }

  @Override
  public final int hashCode() {
    return myHC;
  }

  @Override
  public void clearCaches() {
    myName = null;
    myLocalName = null;
    myAttributes = null;
    myTextElements = null;
    myAttributeValueMap = null;
    myHasNamespaceDeclarations = false;
    myValue = null;
    super.clearCaches();
  }

  /**
   * Use {@link #getReferences(PsiReferenceService.Hints)} instead of calling or overriding this method.
   */
  @Deprecated
  @NotNull
  @Override
  public final PsiReference[] getReferences() {
    return getReferences(PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public boolean shouldAskParentForReferences(@NotNull PsiReferenceService.Hints hints) {
    return false;
  }

  @NotNull
  @Override
  public PsiReference[] getReferences(@NotNull PsiReferenceService.Hints hints) {
    ProgressManager.checkCanceled();
    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(this);
    if (startTagName == null) return PsiReference.EMPTY_ARRAY;
    final ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(this);
    List<PsiReference> refs = new ArrayList<>();
    String prefix = getNamespacePrefix();

    boolean inStartTag = hints.offsetInElement == null || childContainsOffset(startTagName.getPsi(), hints.offsetInElement);
    if (inStartTag) {
      TagNameReference startTagRef = TagNameReference.createTagNameReference(this, startTagName, true);
      if (startTagRef != null) {
        refs.add(startTagRef);
      }
      if (!prefix.isEmpty()) {
        refs.add(createPrefixReference(startTagName, prefix, startTagRef));
      }
    }
    boolean inEndTag = endTagName != null && (hints.offsetInElement == null || childContainsOffset(endTagName.getPsi(), hints.offsetInElement));
    if (inEndTag) {
      TagNameReference endTagRef = TagNameReference.createTagNameReference(this, endTagName, false);
      if (endTagRef != null) {
        refs.add(endTagRef);
      }
      prefix = XmlUtil.findPrefixByQualifiedName(endTagName.getText());
      if (StringUtil.isNotEmpty(prefix)) {
        refs.add(createPrefixReference(endTagName, prefix, endTagRef));
      }
    }

    if (hints.offsetInElement == null || inStartTag || inEndTag || isInsideXmlText(hints.offsetInElement)) {
      Collections.addAll(refs, ReferenceProvidersRegistry.getReferencesFromProviders(this, hints));
    }

    return ContainerUtil.toArray(refs, new PsiReference[refs.size()]);
  }

  private static boolean childContainsOffset(PsiElement child, int offsetInTag) {
    return child.getStartOffsetInParent() <= offsetInTag && offsetInTag <= child.getStartOffsetInParent() + child.getTextLength();
  }

  private boolean isInsideXmlText(int offsetInTag) {
    TextRange[] ranges = getValueTextRanges();
    if (ranges.length == 0) return false;
    if (offsetInTag < ranges[0].getStartOffset() || offsetInTag > ranges[ranges.length - 1].getEndOffset()) return false;

    int i = Arrays.binarySearch(ranges, TextRange.from(offsetInTag, 0), RANGE_COMPARATOR);
    return i >= 0 || ranges[-i - 2].containsOffset(offsetInTag);
  }

  @NotNull
  private TextRange[] getValueTextRanges() {
    TextRange[] elements = myTextElements;
    if (elements == null) {
      List<TextRange> list = ContainerUtil.newSmartList();
      // don't use getValue().getXmlElements() because it processes includes & entities, and we only need textual AST here
      for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
        PsiElement psi = child.getPsi();
        if (psi instanceof XmlText) {
          list.add(TextRange.from(psi.getStartOffsetInParent(), psi.getTextLength()));
        }
      }
      myTextElements = elements = list.toArray(new TextRange[list.size()]);
    }
    return elements;
  }

  private SchemaPrefixReference createPrefixReference(ASTNode startTagName, String prefix, TagNameReference tagRef) {
    return new SchemaPrefixReference(this, TextRange.from(startTagName.getStartOffset() - getStartOffset(), prefix.length()), prefix,
                                     tagRef);
  }

  @Override
  public XmlNSDescriptor getNSDescriptor(final String namespace, boolean strict) {
    final XmlTag parentTag = getParentTag();

    if (parentTag == null && namespace.equals(XmlUtil.XHTML_URI)) {
      final XmlNSDescriptor descriptor = getDtdDescriptor(XmlUtil.getContainingFile(this));
      if (descriptor != null) {
        return descriptor;
      }
    }

    Map<String, CachedValue<XmlNSDescriptor>> map = getNSDescriptorsMap();
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

  @Override
  public boolean isEmpty() {
    return XmlChildRole.CLOSING_TAG_START_FINDER.findChild(this) == null;
  }

  @Override
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

      @Override
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

  @Override
  @Nullable
  @NonNls
  public String getSubTagText(@NonNls String qname) {
    final XmlTag tag = findFirstSubTag(qname);
    if (tag == null) return null;
    return tag.getValue().getText();
  }

  protected final Map<String, CachedValue<XmlNSDescriptor>> getNSDescriptorsMap() {
    return CachedValuesManager.getCachedValue(this, () ->
      CachedValueProvider.Result.create(computeNsDescriptorMap(),
                                        PsiModificationTracker.MODIFICATION_COUNT, externalResourceModificationTracker()));
  }

  @NotNull
  private Map<String, CachedValue<XmlNSDescriptor>> computeNsDescriptorMap() {
    Map<String, CachedValue<XmlNSDescriptor>> map = null;
    // XSD aware attributes processing

    final String noNamespaceDeclaration = getAttributeValue("noNamespaceSchemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
    final String schemaLocationDeclaration = getAttributeValue("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);

    if (noNamespaceDeclaration != null) {
      map = initializeSchema(XmlUtil.EMPTY_URI, null, noNamespaceDeclaration, null, myHasNamespaceDeclarations);
    }
    if (schemaLocationDeclaration != null) {
      final StringTokenizer tokenizer = new StringTokenizer(schemaLocationDeclaration);
      while (tokenizer.hasMoreTokens()) {
        final String uri = tokenizer.nextToken();
        if (tokenizer.hasMoreTokens()) {
          map = initializeSchema(uri, getNSVersion(uri, this), tokenizer.nextToken(), map, myHasNamespaceDeclarations);
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
            map = initializeSchema(ns, getNSVersion(ns, this), getNsLocation(ns), map, true);
          }
        }
      }
    }
    return map == null ? Collections.<String, CachedValue<XmlNSDescriptor>>emptyMap() : map;
  }

  private Map<String, CachedValue<XmlNSDescriptor>> initializeSchema(@NotNull final String namespace,
                                                                     @Nullable final String version,
                                                                     final String fileLocation,
                                                                     Map<String, CachedValue<XmlNSDescriptor>> map,
                                                                     final boolean nsDecl) {
    if (map == null) map = new THashMap<>();

    // We put cached value in any case to cause its value update on e.g. mapping change
    map.put(namespace, CachedValuesManager.getManager(getManager().getProject()).createCachedValue(() -> {
      XmlNSDescriptor descriptor = getImplicitNamespaceDescriptor(fileLocation);
      if (descriptor != null) {
        return new CachedValueProvider.Result<>(descriptor, ArrayUtil.append(descriptor.getDependences(), this));
      }

      XmlFile currentFile = retrieveFile(fileLocation, version, namespace, nsDecl);
      if (currentFile == null) {
        final XmlDocument document = XmlUtil.getContainingFile(this).getDocument();
        if (document != null) {
          final String uri = XmlUtil.getDtdUri(document);
          if (uri != null) {
            final XmlFile containingFile = XmlUtil.getContainingFile(document);
            final XmlFile xmlFile = XmlUtil.findNamespace(containingFile, uri);
            descriptor = xmlFile == null ? null : (XmlNSDescriptor)xmlFile.getDocument().getMetaData();
          }

          // We want to get fixed xmlns attr from dtd and check its default with requested namespace
          if (descriptor instanceof com.intellij.xml.impl.dtd.XmlNSDescriptorImpl) {
            final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(this);
            if (elementDescriptor != null) {
              final XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("xmlns", this);
              if (attributeDescriptor != null && attributeDescriptor.isFixed()) {
                final String defaultValue = attributeDescriptor.getDefaultValue();
                if (defaultValue != null && defaultValue.equals(namespace)) {
                  return new CachedValueProvider.Result<>(descriptor, descriptor.getDependences(), this,
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
          return new CachedValueProvider.Result<>(descriptor, descriptor.getDependences(), this,
                                                  ExternalResourceManager.getInstance());
        }
      }
      return new CachedValueProvider.Result<>(null, this, currentFile == null ? this : currentFile,
                                              ExternalResourceManager.getInstance());
    }, false));

    return map;
  }

  @Nullable
  private XmlNSDescriptor getImplicitNamespaceDescriptor(String ns) {
    PsiFile file = getContainingFile();
    if (file == null) return null;
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      for (ImplicitNamespaceDescriptorProvider provider : Extensions.getExtensions(ImplicitNamespaceDescriptorProvider.EP_NAME)) {
        XmlNSDescriptor nsDescriptor = provider.getNamespaceDescriptor(module, ns, file);
        if (nsDescriptor != null) return nsDescriptor;
      }
    }
    return null;
  }

  @Nullable
  private XmlFile retrieveFile(final String fileLocation, final String version, String namespace, boolean nsDecl) {
    final String targetNs = XmlUtil.getTargetSchemaNsFromTag(this);
    if (fileLocation.equals(targetNs)) {
      return null;
    }

    final XmlFile file = XmlUtil.getContainingFile(this);
    final PsiFile psiFile = ExternalResourceManager.getInstance().getResourceLocation(fileLocation, file, version);
    if (psiFile instanceof XmlFile) {
      return (XmlFile)psiFile;
    }

    return XmlNamespaceIndex.guessSchema(namespace, nsDecl ? null : myLocalName, version, fileLocation, file);
  }

  @Nullable
  private PsiMetaOwner retrieveOwner(final XmlFile file, @NotNull final String namespace) {
    if (file == null) {
      return namespace.equals(XmlUtil.getTargetSchemaNsFromTag(this)) ? this : null;
    }
    return file.getDocument();
  }

  @Override
  public PsiReference getReference() {
    return ArrayUtil.getFirstElement(getReferences(PsiReferenceService.Hints.NO_HINTS));
  }

  @Override
  public XmlElementDescriptor getDescriptor() {
    return CachedValuesManager.getCachedValue(this, () ->
      CachedValueProvider.Result.create(computeElementDescriptor(),
                                        PsiModificationTracker.MODIFICATION_COUNT, externalResourceModificationTracker()));
  }

  private ModificationTracker externalResourceModificationTracker() {
    Project project = getProject();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    return () -> manager.getModificationCount(project);
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

    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "Descriptor for namespace " + namespace + " is " + (nsDescriptor != null ? nsDescriptor.getClass().getCanonicalName() : "NULL"));
    }

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

  @Override
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

  @Override
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

  @Override
  public PsiElement setName(@NotNull final String name) throws IncorrectOperationException {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      @Override
      public PomModelEvent runInner() throws IncorrectOperationException {
        final String oldName = getName();
        final XmlTagImpl dummyTag =
          (XmlTagImpl)XmlElementFactory.getInstance(getProject()).createTagFromText(XmlTagUtil.composeTagText(name, "aa"));
        final XmlTagImpl tag = XmlTagImpl.this;
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(tag);
        ASTNode child = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag);
        LOG.assertTrue(child != null, "It seems '" + name + "' is not a valid tag name");
        TreeElement tagElement = (TreeElement)XmlChildRole.START_TAG_NAME_FINDER.findChild(dummyTag);
        LOG.assertTrue(tagElement != null, "What's wrong with it? '" + name + "'");
        tag.replaceChild(child, ChangeUtil.copyElement(tagElement, charTableByTree));
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

  @Override
  @NotNull
  public XmlAttribute[] getAttributes() {
    XmlAttribute[] attributes = myAttributes;
    if (attributes == null) {
      myAttributes = attributes = calculateAttributes();
    }
    return attributes;
  }

  @NotNull
  private XmlAttribute[] calculateAttributes() {
    final List<XmlAttribute> result = new ArrayList<>(10);
    processChildren(new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (element instanceof XmlAttribute) {
          XmlAttribute attribute = (XmlAttribute)element;
          result.add(attribute);
          myHasNamespaceDeclarations = myHasNamespaceDeclarations || attribute.isNamespaceDeclaration();
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

  @Override
  public String getAttributeValue(String qname) {
    Map<String, String> map = myAttributeValueMap;
    if (map == null) {
      map = new THashMap<>();
      for (XmlAttribute attribute : getAttributes()) {
        cacheOneAttributeValue(attribute.getName(), attribute.getValue(), map);
      }
      myAttributeValueMap = map;
    }
    return map.get(qname);
  }

  @Override
  public String getAttributeValue(String _name, String namespace) {
    if (namespace == null) {
      return getAttributeValue(_name);
    }

    XmlTagImpl current = this;
    while (true) {
      BidirectionalMap<String, String> map = current.getNamespaceMap();
      if (map != null) {
        List<String> keysByValue = map.getKeysByValue(namespace);
        if (keysByValue != null && !keysByValue.isEmpty()) {

          for (String prefix : keysByValue) {
            if (prefix != null && !prefix.isEmpty()) {
              final String value = getAttributeValue(prefix + ":" + _name);
              if (value != null) return value;
            }
          }
        }
      }

      PsiElement parent = current.getParent();
      if (!(parent instanceof XmlTag)) {
        break;
      }
      current = (XmlTagImpl)parent;
    }

    if (namespace.isEmpty() || getNamespace().equals(namespace)) {
      return getAttributeValue(_name);
    }
    return null;
  }

  @Override
  @NotNull
  public XmlTag[] getSubTags() {
    return CachedValuesManager.getManager(getProject()).getParameterizedCachedValue(this, SUBTAGS_KEY, CACHED_VALUE_PROVIDER, false, this);
  }

  protected void fillSubTags(final List<XmlTag> result) {
    processElements(new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (element instanceof XmlTag) {
          assert element.isValid();
          result.add((XmlTag)element);
        }
        return true;
      }
    }, this);
  }

  @Override
  @NotNull
  public XmlTag[] findSubTags(String name) {
    return findSubTags(name, null);
  }

  @Override
  @NotNull
  public XmlTag[] findSubTags(final String name, @Nullable final String namespace) {
    final XmlTag[] subTags = getSubTags();
    final List<XmlTag> result = new ArrayList<>();
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

  @Override
  public XmlTag findFirstSubTag(String name) {
    final XmlTag[] subTags = findSubTags(name);
    if (subTags.length > 0) return subTags[0];
    return null;
  }

  @Override
  public XmlAttribute getAttribute(String name, String namespace) {
    if (name != null && name.indexOf(':') != -1 ||
        namespace == null ||
        XmlUtil.EMPTY_URI.equals(namespace)) {
      return getAttribute(name);
    }

    final String prefix = getPrefixByNamespace(namespace);
    if (prefix == null || prefix.isEmpty()) return null;
    return getAttribute(prefix + ":" + name);
  }

  @Override
  @Nullable
  public XmlAttribute getAttribute(String qname) {
    if (qname == null) return null;
    final XmlAttribute[] attributes = getAttributes();

    final boolean caseSensitive = isCaseSensitive();

    for (final XmlAttribute attribute : attributes) {
      final ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(attribute.getNode());
      if (child instanceof LeafElement) {
        final LeafElement attrNameElement = (LeafElement)child;
        if ((caseSensitive && Comparing.equal(attrNameElement.getChars(), qname) ||
             !caseSensitive && Comparing.equal(attrNameElement.getChars(), qname, false))) {
          return attribute;
        }
      }
    }
    return null;
  }

  protected boolean isCaseSensitive() {
    return true;
  }

  @Override
  @NotNull
  public String getNamespace() {
    return CachedValuesManager.getCachedValue(this, () ->
      CachedValueProvider.Result.create(getNamespaceByPrefix(getNamespacePrefix()), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  @NotNull
  public String getNamespacePrefix() {
    return XmlUtil.findPrefixByQualifiedName(getName());
  }

  @Override
  @NotNull
  public String getNamespaceByPrefix(String prefix) {
    BidirectionalMap<String, String> map = getNamespaceMap();
    if (map != null) {
      final String ns = map.get(prefix);
      if (ns != null) return ns;
    }
    XmlTag parentTag = getParentTag();
    if (parentTag != null) return parentTag.getNamespaceByPrefix(prefix);
    //The prefix 'xml' is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared
    if (XML_NS_PREFIX.equals(prefix)) return XmlUtil.XML_NAMESPACE_URI;

    if (!prefix.isEmpty() &&
        !hasNamespaceDeclarations() &&
        getNamespacePrefix().equals(prefix)) {
      // When there is no namespace declarations then qualified names should be just used in dtds
      // this implies that we may have "" namespace prefix ! (see last paragraph in Namespaces in Xml, Section 5)

      String result = ourGuard.doPreventingRecursion("getNsByPrefix", true, () -> {
        final String nsFromEmptyPrefix = getNamespaceByPrefix("");
        final XmlNSDescriptor nsDescriptor = getNSDescriptor(nsFromEmptyPrefix, false);
        final XmlElementDescriptor descriptor = nsDescriptor != null ? nsDescriptor.getElementDescriptor(this) : null;
        final String nameFromRealDescriptor =
          descriptor != null && descriptor.getDeclaration() != null && descriptor.getDeclaration().isPhysical()
          ? descriptor.getName()
          : "";
        if (nameFromRealDescriptor.equals(getName())) return nsFromEmptyPrefix;
        return XmlUtil.EMPTY_URI;
      });
      if (result != null) {
        return result;
      }
    }
    return XmlUtil.EMPTY_URI;
  }

  @Override
  public String getPrefixByNamespace(String namespace) {
    BidirectionalMap<String, String> map = getNamespaceMap();
    if (map != null) {
      List<String> keysByValue = map.getKeysByValue(namespace);
      final String ns = keysByValue == null || keysByValue.isEmpty() ? null : keysByValue.get(0);
      if (ns != null) return ns;
    }
    XmlTag parentTag = getParentTag();
    if (parentTag != null) return parentTag.getPrefixByNamespace(namespace);
    //The prefix 'xml' is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared
    if (XmlUtil.XML_NAMESPACE_URI.equals(namespace)) return XML_NS_PREFIX;
    return null;
  }

  @Override
  public String[] knownNamespaces() {
    final PsiElement parentElement = getParent();
    BidirectionalMap<String, String> map = getNamespaceMap();
    Set<String> known = Collections.emptySet();
    if (map != null) {
      known = new HashSet<>(map.values());
    }
    if (parentElement instanceof XmlTag) {
      if (known.isEmpty()) return ((XmlTag)parentElement).knownNamespaces();
      ContainerUtil.addAll(known, ((XmlTag)parentElement).knownNamespaces());
    }
    else {
      XmlExtension xmlExtension = XmlExtension.getExtensionByElement(this);
      if (xmlExtension != null) {
        final XmlFile xmlFile = xmlExtension.getContainingFile(this);
        if (xmlFile != null) {
          final XmlTag rootTag = xmlFile.getRootTag();
          if (rootTag != null && rootTag != this) {
            if (known.isEmpty()) return rootTag.knownNamespaces();
            ContainerUtil.addAll(known, rootTag.knownNamespaces());
          }
        }
      }
    }
    return ArrayUtil.toStringArray(known);
  }

  @Nullable
  private BidirectionalMap<String, String> getNamespaceMap() {
    return CachedValuesManager.getCachedValue(this, () ->
      CachedValueProvider.Result.create(computeNamespaceMap(getParent()), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private BidirectionalMap<String, String> computeNamespaceMap(PsiElement parent) {
    BidirectionalMap<String, String> map = null;
    boolean hasNamespaceDeclarations = hasNamespaceDeclarations();
    if (hasNamespaceDeclarations) {
      map = new BidirectionalMap<>();
      final XmlAttribute[] attributes = getAttributes();

      for (final XmlAttribute attribute : attributes) {
        if (attribute.isNamespaceDeclaration()) {
          final String name = attribute.getName();
          int splitIndex = name.indexOf(':');
          final String value = getRealNs(attribute.getValue());

          if (value != null) {
            if (splitIndex < 0) {
              map.put("", value);
            }
            else {
              map.put(XmlUtil.findLocalNameByQualifiedName(name), value);
            }
          }
        }
      }
    }

    if (parent instanceof XmlDocument) {
      final XmlExtension extension = XmlExtension.getExtensionByElement(parent);
      if (extension != null) {
        final String[][] namespacesFromDocument = extension.getNamespacesFromDocument((XmlDocument)parent, hasNamespaceDeclarations);
        if (namespacesFromDocument != null) {
          if (map == null) {
            map = new BidirectionalMap<>();
          }
          for (final String[] prefix2ns : namespacesFromDocument) {
            map.put(prefix2ns[0], getRealNs(prefix2ns[1]));
          }
        }
      }
    }
    return map;
  }

  private String getNsLocation(String ns) {
    if (XmlUtil.XHTML_URI.equals(ns)) {
      return XmlUtil.getDefaultXhtmlNamespace(getProject());
    }
    if (XmlNSDescriptorImpl.equalsToSchemaName(this, XmlNSDescriptorImpl.SCHEMA_TAG_NAME)) {
      for (XmlTag subTag : getSubTags()) {
        if (XmlNSDescriptorImpl.equalsToSchemaName(subTag, XmlNSDescriptorImpl.IMPORT_TAG_NAME) &&
            ns.equals(subTag.getAttributeValue("namespace"))) {
          String location = subTag.getAttributeValue("schemaLocation");
          if (location != null) {
            return location;
          }
        }
      }
    }
    return XmlUtil.getSchemaLocation(this, ns);
  }

  protected String getRealNs(final String value) {
    return value;
  }

  @Override
  @NotNull
  public String getLocalName() {
    String localName = myLocalName;
    if (localName == null) {
      final String name = getName();
      myLocalName = localName = name.substring(name.indexOf(':') + 1);
    }
    return localName;
  }

  @Override
  public boolean hasNamespaceDeclarations() {
    getAttributes();
    return myHasNamespaceDeclarations;
  }

  @Override
  @NotNull
  public Map<String, String> getLocalNamespaceDeclarations() {
    Map<String, String> namespaces = new THashMap<>();
    for (final XmlAttribute attribute : getAttributes()) {
      if (!attribute.isNamespaceDeclaration() || attribute.getValue() == null) continue;
      // xmlns -> "", xmlns:a -> a
      final String localName = attribute.getLocalName();
      namespaces.put(localName.equals(attribute.getName()) ? "" : localName, attribute.getValue());
    }
    return namespaces;
  }

  @Override
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
      PsiElement xmlAttribute = add(XmlElementFactory.getInstance(getProject()).createAttribute(qname, value, this));
      while (!(xmlAttribute instanceof XmlAttribute)) xmlAttribute = xmlAttribute.getNextSibling();
      return (XmlAttribute)xmlAttribute;
    }
  }

  @Override
  public XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException {
    if (!Comparing.equal(namespace, "")) {
      final String prefix = getPrefixByNamespace(namespace);
      if (prefix != null && !prefix.isEmpty()) name = prefix + ":" + name;
    }
    return setAttribute(name, value);
  }

  @Override
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

  @Override
  @NotNull
  public XmlTagValue getValue() {
    XmlTagValue tagValue = myValue;
    if (tagValue == null) {
      myValue = tagValue = XmlTagValueImpl.createXmlTagValue(this);
    }
    return tagValue;
  }

  @Override
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

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean beforeB) {
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

  @Override
  public void deleteChildInternal(@NotNull final ASTNode child) {
    final PomModel model = PomManager.getModel(getProject());
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);

    if (child.getElementType() == XmlElementType.XML_ATTRIBUTE) {
      try {
        model.runTransaction(new PomTransactionBase(this, aspect) {
          @Override
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
      super.deleteChildInternal(child);
      if (treePrev != null &&
          treeNext != null &&
          treePrev.getElementType() == XmlElementType.XML_TEXT &&
          treeNext.getElementType() == XmlElementType.XML_TEXT) {
        final XmlText prevText = (XmlText)treePrev.getPsi();
        final XmlText nextText = (XmlText)treeNext.getPsi();

        final String newValue = prevText.getValue() + nextText.getValue();

        // merging two XmlText-s should be done in one transaction to preserve smart pointers
        ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction() {
          @Override
          public void makeChange(TreeChangeEvent destinationTreeChange) {
            PsiElement anchor = prevText.getPrevSibling();
            prevText.delete();
            nextText.delete();
            XmlText text = (XmlText)addAfter(XmlElementFactory.getInstance(getProject()).createDisplayText("x"), anchor);
            text.setValue(newValue);
          }
        }, this);

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

  @Override
  public XmlTag getParentTag() {
    final PsiElement parent = getParent();
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    final PsiElement nextSibling = getNextSibling();
    if (nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if (prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  @Override
  public Icon getElementIcon(int flags) {
    return PlatformIcons.XML_TAG_ICON;
  }

  protected class BodyInsertTransaction extends InsertTransaction {
    private final TreeElement myChild;
    private ASTNode myNewElement;

    public BodyInsertTransaction(TreeElement child) {
      super(XmlTagImpl.this);
      myChild = child;
    }

    @Override
    public PomModelEvent runInner() throws IncorrectOperationException {
      final ASTNode anchor = expandTag();
      if (myChild.getElementType() == XmlElementType.XML_TAG) {
        // compute where to insert tag according to DTD or XSD
        final XmlElementDescriptor parentDescriptor = getDescriptor();
        final XmlTag[] subTags = getSubTags();
        final PsiElement declaration = parentDescriptor != null ? parentDescriptor.getDeclaration() : null;
        // filtering out generated dtds
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
                    "Can't insert subtag to the entity. Entity reference text: " + (entityRef == null ? "" : entityRef.getText()));
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

    @Override
    public TreeElement getFirstInserted() {
      return (TreeElement)myNewElement;
    }
  }

  protected class InsertAttributeTransaction extends InsertTransaction {
    private final TreeElement myChild;
    private final ASTNode myAnchor;
    private final boolean myBefore;
    private final PomModel myModel;
    private TreeElement myFirstInserted;

    public InsertAttributeTransaction(final TreeElement child, final ASTNode anchor, final boolean before, final PomModel model) {
      super(XmlTagImpl.this);
      myChild = child;
      myAnchor = anchor;
      myBefore = before;
      myModel = model;
    }

    @Override
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

    @Override
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

    @Override
    public PomModelEvent runInner() {
      myRetHolder = XmlTagImpl.super.addInternal(myChild, myChild, myAnchor, Boolean.valueOf(myBefore));
      return null;
    }

    @Override
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
