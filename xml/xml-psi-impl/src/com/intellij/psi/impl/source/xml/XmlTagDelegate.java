// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ImplicitNamespaceDescriptorProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.*;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.xml.*;
import com.intellij.util.*;
import com.intellij.util.IdempotenceChecker.ResultWithLog;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.MultiFileNsDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.util.XmlPsiUtil;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

@ApiStatus.Experimental
public abstract class XmlTagDelegate {
  private static final Logger LOG = Logger.getInstance(XmlTagDelegate.class);
  private static final @NonNls String XML_NS_PREFIX = "xml";
  private static final Key<CachedValue<ResultWithLog<XmlTag[]>>> SUBTAGS_WITH_INCLUDES_KEY = Key.create("subtags with includes");
  private static final Key<CachedValue<ResultWithLog<XmlTag[]>>> SUBTAGS_WITHOUT_INCLUDES_KEY = Key.create("subtags without includes");
  private static final Comparator<TextRange> RANGE_COMPARATOR = Comparator.comparingInt(TextRange::getStartOffset);

  protected final @NotNull XmlTag myTag;

  private volatile String myName;
  private volatile String myLocalName;
  private volatile TextRange[] myTextElements;
  private volatile Map<String, String> myAttributeValueMap;
  private volatile Boolean myHasNamespaceDeclarations;

  public XmlTagDelegate(@NotNull XmlTag tag) {
    myTag = tag;
  }

  protected abstract void deleteChildInternalSuper(final @NotNull ASTNode child);

  protected abstract TreeElement addInternalSuper(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before);

  protected XmlTag createTag(String name, String tagValue) {
    return XmlElementFactory.getInstance(myTag.getProject()).createTagFromText(XmlTagUtil.composeTagText(name, tagValue));
  }

  protected XmlAttribute createAttribute(@NotNull String qname, @NotNull String value) {
    return XmlElementFactory.getInstance(myTag.getProject()).createAttribute(qname, value, myTag);
  }

  protected XmlTag createTagFromText(String text) {
    return XmlElementFactory.getInstance(myTag.getProject()).createTagFromText(text);
  }

  protected void cacheOneAttributeValue(String name, String value, final Map<String, String> attributesValueMap) {
    attributesValueMap.put(name, value);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof XmlTagDelegate
           && ((XmlTagDelegate)obj).myTag == myTag;
  }

  @Override
  public int hashCode() {
    return myTag.hashCode();
  }

  private static @Nullable XmlNSDescriptor getDtdDescriptor(@NotNull XmlFile containingFile) {
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

  private static @Nullable String getNSVersion(final @Nullable String ns, final @NotNull XmlTag xmlTag) {
    String versionValue = xmlTag.getAttributeValue("version");
    if (versionValue != null && xmlTag.getNamespace().equals(ns)) {
      return versionValue;
    }
    return null;
  }

  PsiReference @NotNull [] getDefaultReferences(@NotNull PsiReferenceService.Hints hints) {
    ProgressManager.checkCanceled();
    if (hints == PsiReferenceService.Hints.NO_HINTS) {
      return CachedValuesManager
        .getCachedValue(myTag, () -> Result.create(getReferencesImpl(PsiReferenceService.Hints.NO_HINTS),
                                                   PsiModificationTracker.MODIFICATION_COUNT,
                                                   externalResourceModificationTracker(myTag))).clone();
    }

    return getReferencesImpl(hints);
  }

  private PsiReference @NotNull [] getReferencesImpl(@NotNull PsiReferenceService.Hints hints) {
    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(myTag.getNode());
    if (startTagName == null) return PsiReference.EMPTY_ARRAY;
    final ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(myTag.getNode());
    List<PsiReference> refs = new SmartList<>();
    String prefix = myTag.getNamespacePrefix();

    boolean inStartTag = hints.offsetInElement == null || childContainsOffset(startTagName.getPsi(), hints.offsetInElement);
    if (inStartTag) {
      TagNameReference startTagRef = TagNameReference.createTagNameReference(myTag, startTagName, true);
      if (startTagRef != null) {
        refs.add(startTagRef);
      }
      if (!prefix.isEmpty()) {
        refs.addAll(createPrefixReferences(startTagName, prefix, startTagRef));
      }
    }
    boolean inEndTag =
      endTagName != null && (hints.offsetInElement == null || childContainsOffset(endTagName.getPsi(), hints.offsetInElement));
    if (inEndTag) {
      TagNameReference endTagRef = TagNameReference.createTagNameReference(myTag, endTagName, false);
      if (endTagRef != null) {
        refs.add(endTagRef);
      }
      prefix = getNamespacePrefix(endTagName.getText());
      if (StringUtil.isNotEmpty(prefix)) {
        refs.addAll(createPrefixReferences(endTagName, prefix, endTagRef));
      }
    }

    if (hints.offsetInElement == null || inStartTag || inEndTag || isInsideXmlText(hints.offsetInElement)) {
      Collections.addAll(refs, ReferenceProvidersRegistry.getReferencesFromProviders(myTag, hints));
    }

    Integer offset = hints.offsetInElement;
    if (offset != null) {
      return PsiDynaReference.filterByOffset(refs.toArray(PsiReference.EMPTY_ARRAY), offset);
    }

    return refs.toArray(PsiReference.EMPTY_ARRAY);
  }

  private static boolean childContainsOffset(@NotNull PsiElement child, int offsetInTag) {
    return child.getStartOffsetInParent() <= offsetInTag && offsetInTag <= child.getStartOffsetInParent() + child.getTextLength();
  }

  private boolean isInsideXmlText(int offsetInTag) {
    TextRange[] ranges = getValueTextRanges();
    if (ranges.length == 0) return false;
    if (offsetInTag < ranges[0].getStartOffset() || offsetInTag > ranges[ranges.length - 1].getEndOffset()) return false;

    int i = Arrays.binarySearch(ranges, TextRange.from(offsetInTag, 0), RANGE_COMPARATOR);
    return i >= 0 || ranges[-i - 2].containsOffset(offsetInTag);
  }

  private TextRange @NotNull [] getValueTextRanges() {
    TextRange[] elements = myTextElements;
    if (elements == null) {
      List<TextRange> list = new SmartList<>();
      // don't use getValue().getXmlElements() because it processes includes & entities, and we only need textual AST here
      for (ASTNode child = myTag.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
        PsiElement psi = child.getPsi();
        if (psi instanceof XmlText) {
          list.add(TextRange.from(psi.getStartOffsetInParent(), psi.getTextLength()));
        }
      }
      myTextElements = elements = list.toArray(TextRange.EMPTY_ARRAY);
    }
    return elements;
  }


  protected Collection<PsiReference> createPrefixReferences(@NotNull ASTNode startTagName,
                                                            @NotNull String prefix,
                                                            @Nullable TagNameReference tagRef) {
    return Collections.singleton(new SchemaPrefixReference(
      myTag, TextRange.from(startTagName.getStartOffset() - myTag.getNode().getStartOffset(), prefix.length()), prefix, tagRef));
  }

  XmlNSDescriptor getNSDescriptor(final String namespace, boolean strict) {
    final XmlTag parentTag = myTag.getParentTag();

    if (parentTag == null && namespace.equals(XmlUtil.XHTML_URI)) {
      final XmlNSDescriptor descriptor = getDtdDescriptor(XmlUtil.getContainingFile(myTag));
      if (descriptor != null) {
        return descriptor;
      }
    }

    NullableLazyValue<XmlNSDescriptor> descriptor = getNSDescriptorMap().get(namespace);
    if (descriptor != null) {
      final XmlNSDescriptor value = descriptor.getValue();
      if (value != null) {
        return value;
      }
    }

    if (parentTag == null) {
      final XmlDocument parentOfType = PsiTreeUtil.getParentOfType(myTag, XmlDocument.class);
      if (parentOfType == null) {
        return null;
      }
      return parentOfType.getDefaultNSDescriptor(namespace, strict);
    }

    return parentTag.getNSDescriptor(namespace, strict);
  }

  void collapseIfEmpty() {
    final XmlTag[] tags = myTag.getSubTags();
    if (tags.length > 0) {
      return;
    }
    final ASTNode closingName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(myTag.getNode());
    final ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(myTag.getNode());
    if (closingName == null || startTagEnd == null) {
      return;
    }

    final ASTNode closingBracket = closingName.getTreeNext();
    final ASTNode tag = myTag.getNode();
    tag.removeRange(startTagEnd, closingBracket);
    final LeafElement emptyTagEnd =
      Factory.createSingleLeafElement(XmlTokenType.XML_EMPTY_ELEMENT_END, "/>", 0, 2, null, myTag.getManager());
    tag.replaceChild(closingBracket, emptyTagEnd);
  }

  private @NotNull Map<String, NullableLazyValue<XmlNSDescriptor>> getNSDescriptorMap() {
    XmlTag tag = myTag;
    return CachedValuesManager.getCachedValue(tag, () ->
      Result.create(computeNsDescriptorMap(tag),
                    PsiModificationTracker.MODIFICATION_COUNT, externalResourceModificationTracker(tag)));
  }

  protected @NotNull String getNamespacePrefix(@NotNull String name) {
    return XmlUtil.findPrefixByQualifiedName(name);
  }

  private static @NotNull Map<String, NullableLazyValue<XmlNSDescriptor>> computeNsDescriptorMap(@NotNull XmlTag tag) {
    Map<String, NullableLazyValue<XmlNSDescriptor>> map = null;
    // XSD aware attributes processing

    final String noNamespaceDeclaration = tag.getAttributeValue("noNamespaceSchemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
    final String schemaLocationDeclaration = tag.getAttributeValue("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
    final boolean hasNamespaceDeclarations = tag.hasNamespaceDeclarations();

    if (noNamespaceDeclaration != null) {
      map = initializeSchema(tag, XmlUtil.EMPTY_URI, null, Collections.singleton(noNamespaceDeclaration),
                             null, hasNamespaceDeclarations);
    }
    if (schemaLocationDeclaration != null) {
      final StringTokenizer tokenizer = new StringTokenizer(schemaLocationDeclaration);
      while (tokenizer.hasMoreTokens()) {
        final String uri = tokenizer.nextToken();
        if (tokenizer.hasMoreTokens()) {
          map = initializeSchema(tag, uri, getNSVersion(uri, tag), Collections.singleton(tokenizer.nextToken()),
                                 map, hasNamespaceDeclarations);
        }
      }
    }

    // namespace attributes processing (XSD declaration via ExternalResourceManager)
    if (hasNamespaceDeclarations) {
      for (final XmlAttribute attribute : tag.getAttributes()) {
        if (attribute.isNamespaceDeclaration()) {
          String ns = attribute.getValue();
          if (ns == null) ns = XmlUtil.EMPTY_URI;
          ns = tag.getRealNs(ns);

          if (map == null || !map.containsKey(ns)) {
            Set<String> locations = getNsLocations(tag, ns);
            map = initializeSchema(tag, ns, getNSVersion(ns, tag), locations, map, true);
          }
        }
      }
    }
    return map == null ? Collections.emptyMap() : map;
  }

  private static @NotNull Map<String, NullableLazyValue<XmlNSDescriptor>> initializeSchema(@NotNull XmlTag tag,
                                                                                           @Nullable String namespace,
                                                                                           @Nullable String version,
                                                                                           @NotNull Set<String> fileLocations,
                                                                                           @Nullable Map<String, NullableLazyValue<XmlNSDescriptor>> map,
                                                                                           boolean nsDecl) {
    if (map == null) {
      map = new HashMap<>();
    }

    // we put cached value in any case to cause its value update on e.g., mapping change
    map.put(namespace, lazyNullable(() -> {
      List<XmlNSDescriptor> descriptors =
        ContainerUtil.mapNotNull(fileLocations, s -> getDescriptor(tag, retrieveFile(tag, s, version, namespace, nsDecl), s, namespace));

      XmlNSDescriptor descriptor = null;
      if (descriptors.size() == 1) {
        descriptor = descriptors.get(0);
      }
      else if (descriptors.size() > 1) {
        descriptor = new MultiFileNsDescriptor(ContainerUtil.map(descriptors, descriptor1 -> (XmlNSDescriptorImpl)descriptor1));
      }
      if (descriptor == null) {
        return null;
      }
      XmlExtension extension = XmlExtension.getExtensionByElement(tag);
      if (extension != null) {
        String prefix = tag.getPrefixByNamespace(namespace);
        descriptor = extension.wrapNSDescriptor(tag, ObjectUtils.notNull(prefix, ""), descriptor);
      }
      return descriptor;
    }));

    return map;
  }

  private static @Nullable XmlNSDescriptor getDescriptor(@NotNull XmlTag tag,
                                                         @Nullable XmlFile currentFile,
                                                         @NotNull String fileLocation,
                                                         @Nullable String namespace) {
    XmlNSDescriptor descriptor = getImplicitNamespaceDescriptor(tag, fileLocation);
    if (descriptor != null) {
      return descriptor;
    }

    if (currentFile == null) {
      final XmlDocument document = XmlUtil.getContainingFile(tag).getDocument();
      if (document != null) {
        String uri = XmlUtil.getDtdUri(document);
        if (uri != null) {
          XmlFile containingFile = XmlUtil.getContainingFile(document);
          XmlFile xmlFile = XmlUtil.findNamespace(containingFile, uri);
          XmlDocument xmlDocument = xmlFile == null ? null : xmlFile.getDocument();
          descriptor = (XmlNSDescriptor)(xmlDocument == null ? null : xmlDocument.getMetaData());
        }

        // We want to get fixed xmlns attr from dtd and check its default with requested namespace
        if (descriptor instanceof com.intellij.xml.impl.dtd.XmlNSDescriptorImpl) {
          final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(tag);
          if (elementDescriptor != null) {
            final XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("xmlns", tag);
            if (attributeDescriptor != null && attributeDescriptor.isFixed()) {
              final String defaultValue = attributeDescriptor.getDefaultValue();
              if (defaultValue != null && defaultValue.equals(namespace)) {
                return descriptor;
              }
            }
          }
        }
      }
    }
    PsiMetaOwner currentOwner = retrieveOwner(tag, currentFile, namespace);
    if (currentOwner != null) {
      return (XmlNSDescriptor)currentOwner.getMetaData();
    }
    return null;
  }

  private static @Nullable XmlNSDescriptor getImplicitNamespaceDescriptor(@NotNull XmlTag tag, @NotNull String ns) {
    PsiFile file = tag.getContainingFile();
    if (file == null) return null;
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      for (ImplicitNamespaceDescriptorProvider provider : ImplicitNamespaceDescriptorProvider.EP_NAME.getExtensionList()) {
        XmlNSDescriptor nsDescriptor = provider.getNamespaceDescriptor(module, ns, file);
        if (nsDescriptor != null) return nsDescriptor;
      }
    }
    return null;
  }

  private static @Nullable XmlFile retrieveFile(final @Nullable XmlTag tag,
                                                final @NotNull String fileLocation,
                                                final @Nullable String version,
                                                String namespace,
                                                boolean nsDecl) {
    final String targetNs = XmlUtil.getTargetSchemaNsFromTag(tag);
    if (fileLocation.equals(targetNs)) {
      return null;
    }

    final XmlFile file = XmlUtil.getContainingFile(tag);
    if (file == null) return null;
    final PsiFile psiFile = ExternalResourceManager.getInstance().getResourceLocation(fileLocation, file, version);
    if (psiFile instanceof XmlFile) {
      return (XmlFile)psiFile;
    }

    return XmlNamespaceIndex.guessSchema(namespace, nsDecl ? null : tag.getLocalName(), version, fileLocation, file);
  }

  private static @Nullable PsiMetaOwner retrieveOwner(final @Nullable XmlTag tag,
                                                      final @Nullable XmlFile file,
                                                      final @Nullable String namespace) {
    if (file == null) {
      return namespace != null && namespace.equals(XmlUtil.getTargetSchemaNsFromTag(tag)) ? tag : null;
    }
    return AstLoadingFilter.forceAllowTreeLoading(file, file::getDocument);
  }

  @Nullable
  XmlElementDescriptor getDescriptor() {
    return CachedValuesManager.getCachedValue(myTag, new CachedValueProvider<>() {
      @Override
      public Result<XmlElementDescriptor> compute() {
        XmlElementDescriptor descriptor =
          RecursionManager.doPreventingRecursion(myTag, true, XmlTagDelegate.this::computeElementDescriptor);
        return Result.create(descriptor, PsiModificationTracker.MODIFICATION_COUNT, externalResourceModificationTracker(myTag));
      }

      @Override
      public String toString() {
        return "XmlTag.getDescriptor(" + myTag.getText() + ")";
      }
    });
  }

  private static @NotNull ModificationTracker externalResourceModificationTracker(@NotNull XmlTag tag) {
    Project project = tag.getProject();
    ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    return () -> manager.getModificationCount(project);
  }

  protected @Nullable XmlElementDescriptor computeElementDescriptor() {
    for (XmlElementDescriptorProvider provider : XmlElementDescriptorProvider.EP_NAME.getExtensionList()) {
      XmlElementDescriptor elementDescriptor = provider.getDescriptor(myTag);
      if (elementDescriptor != null) {
        return elementDescriptor;
      }
    }

    final String namespace = myTag.getNamespace();
    if (XmlUtil.EMPTY_URI.equals(namespace)) { //nonqualified items
      final XmlTag parent = myTag.getParentTag();
      if (parent != null) {
        final XmlElementDescriptor descriptor = parent.getDescriptor();
        if (descriptor != null) {
          XmlElementDescriptor fromParent = descriptor.getElementDescriptor(myTag, parent);
          if (fromParent != null && !(fromParent instanceof AnyXmlElementDescriptor)) {
            return fromParent;
          }
        }
      }
    }

    XmlElementDescriptor elementDescriptor = null;
    final XmlNSDescriptor nsDescriptor = myTag.getNSDescriptor(namespace, false);

    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "Descriptor for namespace " + namespace + " is " + (nsDescriptor != null ? nsDescriptor.getClass().getCanonicalName() : "NULL"));
    }

    if (nsDescriptor != null) {
      if (DumbService.getInstance(myTag.getProject()).isUsableInCurrentContext(nsDescriptor)) {
        elementDescriptor = nsDescriptor.getElementDescriptor(myTag);
      }
    }
    if (elementDescriptor == null) {
      return XmlUtil.findXmlDescriptorByType(myTag);
    }

    return elementDescriptor;
  }

  @NotNull
  String getName() {
    String name = myName;
    if (name == null) {
      final ASTNode nameElement = XmlChildRole.START_TAG_NAME_FINDER.findChild(myTag.getNode());
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

  @NotNull
  PsiElement setName(final @NotNull String name) throws IncorrectOperationException {
    final XmlTag dummyTag = createTag(name, "aa");
    final ASTNode tag = myTag.getNode();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(tag);
    ASTNode child = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag);
    LOG.assertTrue(child != null, "It seems '" + name + "' is not a valid tag name");
    TreeElement tagElement = (TreeElement)XmlChildRole.START_TAG_NAME_FINDER.findChild(dummyTag.getNode());
    LOG.assertTrue(tagElement != null, "What's wrong with it? '" + name + "'");
    tag.replaceChild(child, ChangeUtil.copyElement(tagElement, charTableByTree));
    final ASTNode childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag);
    if (childByRole != null) {
      final TreeElement treeElement = (TreeElement)XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(dummyTag.getNode());
      if (treeElement != null) {
        tag.replaceChild(childByRole, ChangeUtil.copyElement(treeElement, charTableByTree));
      }
    }
    return myTag;
  }

  private void processChildren(@NotNull PsiElementProcessor<? super PsiElement> processor) {
    XmlPsiUtil.processXmlElementChildren(myTag, processor, false);
  }

  XmlAttribute @NotNull [] calculateAttributes() {
    final List<XmlAttribute> result = new ArrayList<>(10);
    processChildren(element -> {
      if (element instanceof XmlAttribute) {
        result.add((XmlAttribute)element);
      }
      return !(element instanceof XmlToken)
             || ((XmlToken)element).getTokenType() != XmlTokenType.XML_TAG_END;
    });
    return result.toArray(XmlAttribute.EMPTY_ARRAY);
  }

  @Contract("null->null")
  protected String getAttributeValue(String qname) {
    Map<String, String> map = myAttributeValueMap;
    if (map == null) {
      map = new HashMap<>();
      for (XmlAttribute attribute : myTag.getAttributes()) {
        cacheOneAttributeValue(attribute.getName(), attribute.getValue(), map);
      }
      myAttributeValueMap = map;
    }
    return map.get(qname);
  }

  protected @Nullable String getAttributeValue(@Nullable String _name, @Nullable String namespace) {
    if (namespace == null) {
      return myTag.getAttributeValue(_name);
    }

    XmlTag current = myTag;
    while (true) {
      BidirectionalMap<String, String> map = getNamespaceMap(current);
      if (map != null) {
        List<String> keysByValue = map.getKeysByValue(namespace);
        if (keysByValue != null && !keysByValue.isEmpty()) {

          for (String prefix : keysByValue) {
            if (prefix != null && !prefix.isEmpty()) {
              final String value = myTag.getAttributeValue(prefix + ":" + _name);
              if (value != null) return value;
            }
          }
        }
      }

      PsiElement parent = current.getParent();
      if (!(parent instanceof XmlTag)) {
        break;
      }
      current = (XmlTag)parent;
    }

    if (namespace.isEmpty() || myTag.getNamespace().equals(namespace)) {
      return myTag.getAttributeValue(_name);
    }
    return null;
  }

  XmlTag @NotNull [] getSubTags(boolean processIncludes) {
    Key<CachedValue<ResultWithLog<XmlTag[]>>> key = processIncludes ? SUBTAGS_WITH_INCLUDES_KEY : SUBTAGS_WITHOUT_INCLUDES_KEY;
    ResultWithLog<XmlTag[]> cached = CachedValuesManager.getCachedValue(myTag, key, () ->
      Result.create(IdempotenceChecker.computeWithLogging(() -> calcSubTags(processIncludes)), PsiModificationTracker.MODIFICATION_COUNT));
    return cached.getResult().clone();
  }

  protected XmlTag @NotNull [] calcSubTags(boolean processIncludes) {
    List<XmlTag> result = new ArrayList<>();
    XmlPsiUtil.processXmlElements(myTag, element -> {
      if (element instanceof XmlTag) {
        PsiUtilCore.ensureValid(element);
        result.add((XmlTag)element);
      }
      return true;
    }, false, false, myTag.getContainingFile(), processIncludes);
    return result.toArray(XmlTag.EMPTY);
  }

  protected XmlTag @NotNull [] findSubTags(final @NotNull String name, final @Nullable String namespace) {
    return findSubTags(name, namespace, myTag.getSubTags());
  }

  private @NotNull Boolean calculateHasNamespaceDeclarations() {
    Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    processChildren(element -> {
      if (element instanceof XmlAttribute
          && ((XmlAttribute)element).isNamespaceDeclaration()) {
        result.set(Boolean.TRUE);
        return false;
      }
      return !(element instanceof XmlToken) || ((XmlToken)element).getTokenType() != XmlTokenType.XML_TAG_END;
    });
    return result.get();
  }

  @Nullable
  XmlTag findFirstSubTag(@NotNull String name) {
    final XmlTag[] subTags = myTag.findSubTags(name);
    if (subTags.length > 0) return subTags[0];
    return null;
  }

  @Nullable
  XmlAttribute getAttribute(@Nullable String name, @Nullable String namespace) {
    if (name != null && name.indexOf(':') != -1 ||
        namespace == null ||
        XmlUtil.EMPTY_URI.equals(namespace)) {
      return myTag.getAttribute(name);
    }

    final String prefix = myTag.getPrefixByNamespace(namespace);
    if (prefix == null || prefix.isEmpty()) return null;
    return myTag.getAttribute(prefix + ":" + name);
  }

  @Nullable
  XmlAttribute getAttribute(@Nullable String qname) {
    if (qname == null) return null;
    final XmlAttribute[] attributes = myTag.getAttributes();

    final boolean caseSensitive = myTag.isCaseSensitive();

    for (final XmlAttribute attribute : attributes) {
      final ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(attribute.getNode());
      if (child instanceof LeafElement attrNameElement) {
        if ((caseSensitive && Comparing.equal(attrNameElement.getChars(), qname) ||
             !caseSensitive && Comparing.equal(attrNameElement.getChars(), qname, false))) {
          return attribute;
        }
      }
    }
    return null;
  }

  protected @NotNull String getNamespaceByPrefix(String prefix) {
    BidirectionalMap<String, String> map = getNamespaceMap(myTag);
    if (map != null) {
      final String ns = map.get(prefix);
      if (ns != null) return ns;
    }
    XmlTag parentTag = myTag.getParentTag();
    if (parentTag != null) return parentTag.getNamespaceByPrefix(prefix);
    //The prefix 'xml' is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared
    if (XML_NS_PREFIX.equals(prefix)) return XmlUtil.XML_NAMESPACE_URI;

    if (!prefix.isEmpty() &&
        !myTag.hasNamespaceDeclarations() &&
        myTag.getNamespacePrefix().equals(prefix)) {
      // When there is no namespace declarations then qualified names should be just used in dtds
      // myTag implies that we may have "" namespace prefix ! (see last paragraph in Namespaces in Xml, Section 5)

      String result = RecursionManager.doPreventingRecursion(Trinity.create("getNsByPrefix", myTag, prefix), true, () -> {
        final String nsFromEmptyPrefix = myTag.getNamespaceByPrefix("");
        if (nsFromEmptyPrefix.isEmpty()) return nsFromEmptyPrefix;

        final XmlNSDescriptor nsDescriptor = myTag.getNSDescriptor(nsFromEmptyPrefix, false);
        final XmlElementDescriptor descriptor = nsDescriptor != null ? nsDescriptor.getElementDescriptor(myTag) : null;
        final String nameFromRealDescriptor =
          descriptor != null && descriptor.getDeclaration() != null && descriptor.getDeclaration().isPhysical()
          ? descriptor.getName()
          : "";
        if (nameFromRealDescriptor.equals(myTag.getName())) return nsFromEmptyPrefix;
        return XmlUtil.EMPTY_URI;
      });
      if (result != null) {
        return result;
      }
    }
    return XmlUtil.EMPTY_URI;
  }

  protected @Nullable String getPrefixByNamespace(@Nullable String namespace) {
    BidirectionalMap<String, String> map = getNamespaceMap(myTag);
    if (map != null) {
      List<String> keysByValue = map.getKeysByValue(namespace);
      final String ns = keysByValue == null || keysByValue.isEmpty() ? null : keysByValue.get(0);
      if (ns != null) return ns;
    }
    XmlTag parentTag = myTag.getParentTag();
    if (parentTag != null) return parentTag.getPrefixByNamespace(namespace);
    //The prefix 'xml' is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared
    if (XmlUtil.XML_NAMESPACE_URI.equals(namespace)) return XML_NS_PREFIX;
    return null;
  }

  String @NotNull [] knownNamespaces() {
    final XmlTag parentTag = myTag.getParentTag();
    BidirectionalMap<String, String> map = getNamespaceMap(myTag);
    Set<String> known = Collections.emptySet();
    if (map != null) {
      known = new HashSet<>(map.values());
    }
    if (parentTag != null) {
      if (known.isEmpty()) return parentTag.knownNamespaces();
      ContainerUtil.addAll(known, parentTag.knownNamespaces());
    }
    else {
      XmlExtension xmlExtension = XmlExtension.getExtensionByElement(myTag);
      if (xmlExtension != null) {
        final XmlFile xmlFile = xmlExtension.getContainingFile(myTag);
        if (xmlFile != null) {
          final XmlTag rootTag = xmlFile.getRootTag();
          if (rootTag != null && rootTag != myTag) {
            if (known.isEmpty()) return rootTag.knownNamespaces();
            ContainerUtil.addAll(known, rootTag.knownNamespaces());
          }
        }
      }
    }
    return ArrayUtilRt.toStringArray(known);
  }

  private static @Nullable BidirectionalMap<String, String> getNamespaceMap(@NotNull XmlTag tag) {
    return CachedValuesManager.getCachedValue(tag, () ->
      Result.create(computeNamespaceMap(tag), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @Nullable BidirectionalMap<String, String> computeNamespaceMap(@NotNull XmlTag tag) {
    BidirectionalMap<String, String> map = null;
    boolean hasNamespaceDeclarations = tag.hasNamespaceDeclarations();
    if (hasNamespaceDeclarations) {
      map = new BidirectionalMap<>();
      final XmlAttribute[] attributes = tag.getAttributes();

      for (final XmlAttribute attribute : attributes) {
        if (attribute.isNamespaceDeclaration()) {
          final String name = attribute.getName();
          int splitIndex = name.indexOf(':');
          final String value = tag.getRealNs(attribute.getValue());

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

    PsiElement parent = (tag instanceof HtmlTag)
                        ? PsiTreeUtil.getParentOfType(tag, XmlTag.class, XmlDocument.class)
                        : tag.getParent();
    if (parent instanceof XmlDocument) {
      final XmlExtension extension = XmlExtension.getExtensionByElement(parent);
      if (extension != null) {
        final String[][] namespacesFromDocument = extension.getNamespacesFromDocument((XmlDocument)parent, hasNamespaceDeclarations);
        if (namespacesFromDocument != null) {
          if (map == null) {
            map = new BidirectionalMap<>();
          }
          for (final String[] prefix2ns : namespacesFromDocument) {
            if (map.containsKey(prefix2ns[0])) continue;
            map.put(prefix2ns[0], tag.getRealNs(prefix2ns[1]));
          }
        }
      }
    }
    return map;
  }

  private static @NotNull Set<String> getNsLocations(final @NotNull XmlTag tag, @Nullable String ns) {
    if (XmlUtil.XHTML_URI.equals(ns)) {
      return Collections.singleton(XmlUtil.getDefaultXhtmlNamespace(tag.getProject()));
    }
    Set<String> locations = new HashSet<>();
    if (XmlNSDescriptorImpl.equalsToSchemaName(tag, XmlNSDescriptorImpl.SCHEMA_TAG_NAME) && ns != null) {
      for (XmlTag subTag : tag.getSubTags()) {
        if (XmlNSDescriptorImpl.equalsToSchemaName(subTag, XmlNSDescriptorImpl.IMPORT_TAG_NAME) &&
            ns.equals(subTag.getAttributeValue("namespace"))) {
          String location = subTag.getAttributeValue("schemaLocation");
          ContainerUtil.addIfNotNull(locations, location);
        }
      }
    }
    if (locations.isEmpty()) {
      locations.add(XmlUtil.getSchemaLocation(tag, ns));
    }

    return locations;
  }

  @NotNull
  String getLocalName() {
    String localName = myLocalName;
    if (localName == null) {
      final String name = myTag.getName();
      myLocalName = localName = name.substring(name.indexOf(':') + 1);
    }
    return localName;
  }

  boolean hasNamespaceDeclarations() {
    Boolean result = myHasNamespaceDeclarations;
    if (result == null) {
      myHasNamespaceDeclarations = result = calculateHasNamespaceDeclarations();
    }
    return result;
  }

  @NotNull
  Map<String, String> getLocalNamespaceDeclarations() {
    Map<String, String> namespaces = new HashMap<>();
    for (XmlAttribute attribute : myTag.getAttributes()) {
      if (!attribute.isNamespaceDeclaration() || attribute.getValue() == null) {
        continue;
      }
      // xmlns -> "", xmlns:a -> a
      String localName = attribute.getLocalName();
      namespaces.put(localName.equals(attribute.getName()) ? "" : localName, attribute.getValue());
    }
    return namespaces;
  }

  public static @NotNull XmlTag @NotNull [] findSubTags(@NotNull String name, @Nullable String namespace, XmlTag[] subTags) {
    final List<XmlTag> result = new ArrayList<>();
    for (final XmlTag subTag : subTags) {
      if (namespace == null) {
        if (name.equals(subTag.getName())) result.add(subTag);
      }
      else if (name.equals(subTag.getLocalName()) && namespace.equals(subTag.getNamespace())) {
        result.add(subTag);
      }
    }
    return result.toArray(XmlTag.EMPTY);
  }

  @Nullable
  XmlAttribute setAttribute(String qname, @Nullable String value) throws IncorrectOperationException {
    final XmlAttribute attribute = myTag.getAttribute(qname);

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
      PsiElement xmlAttribute = myTag.add(createAttribute(qname, value));
      while (!(xmlAttribute instanceof XmlAttribute)) xmlAttribute = xmlAttribute.getNextSibling();
      return (XmlAttribute)xmlAttribute;
    }
  }

  XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException {
    if (!Objects.equals(namespace, "")) {
      final String prefix = myTag.getPrefixByNamespace(namespace);
      if (prefix != null && !prefix.isEmpty()) name = prefix + ":" + name;
    }
    return myTag.setAttribute(name, value);
  }

  @NotNull
  XmlTag addSubTag(@NotNull XmlTag subTag, boolean first) {
    XmlTagChild[] children = myTag.getSubTags();
    if (children.length == 0) {
      children = myTag.getValue().getChildren();
    }
    if (children.length == 0) {
      return (XmlTag)myTag.add(subTag);
    }
    else if (first) {
      return (XmlTag)myTag.addBefore(subTag, children[0]);
    }
    else {
      return (XmlTag)myTag.addAfter(subTag, ArrayUtil.getLastElement(children));
    }
  }

  void deleteChildInternal(final @NotNull ASTNode child) {
    if (child.getElementType() instanceof IXmlAttributeElementType) {
      try {
        deleteChildInternalSuper(child);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    else {
      final ASTNode treePrev = child.getTreePrev();
      final ASTNode treeNext = child.getTreeNext();
      deleteChildInternalSuper(child);
      if (treePrev != null &&
          treeNext != null &&
          treePrev.getElementType() == XmlElementType.XML_TEXT &&
          treeNext.getElementType() == XmlElementType.XML_TEXT &&
          !TreeUtil.containsOuterLanguageElements(treePrev) &&
          !TreeUtil.containsOuterLanguageElements(treeNext)) {
        final XmlText prevText = (XmlText)treePrev.getPsi();
        final XmlText nextText = (XmlText)treeNext.getPsi();

        final String newValue = prevText.getValue() + nextText.getValue();

        // merging two XmlText-s should be done in one transaction to preserve smart pointers
        ChangeUtil.prepareAndRunChangeAction(destinationTreeChange -> {
          PsiElement anchor = prevText.getPrevSibling();
          prevText.delete();
          nextText.delete();
          XmlText text = (XmlText)myTag.addAfter(XmlElementFactory.getInstance(myTag.getProject()).createDisplayText("x"), anchor);
          text.setValue(newValue);
        }, (TreeElement)myTag.getNode());
      }
    }
  }

  TreeElement addInternal(@NotNull TreeElement child, @Nullable ASTNode anchor, boolean before) throws IncorrectOperationException {
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
    return child.getElementType() instanceof IXmlAttributeElementType ? insertAttribute(anchor, child, before) :
           anchor == null ? bodyInsert(child) :
           genericInsert(child, anchor, before);
  }

  private @NotNull ASTNode expandTag() throws IncorrectOperationException {
    ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(myTag.getNode());
    if (endTagStart == null) {
      final XmlTag tagFromText = createTagFromText("<" + myTag.getName() + "></" + myTag.getName() + ">");
      final ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tagFromText.getNode());
      endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tagFromText.getNode());
      assert startTagEnd != null : tagFromText.getText();
      assert endTagStart != null : tagFromText.getText();
      final LeafElement emptyTagEnd = (LeafElement)XmlChildRole.EMPTY_TAG_END_FINDER.findChild(myTag.getNode());
      if (emptyTagEnd != null) myTag.getNode().removeChild(emptyTagEnd);
      myTag.getNode().addChildren(startTagEnd, null, null);
    }
    return endTagStart;
  }

  protected TreeElement bodyInsert(TreeElement child) {
    final ASTNode anchor = expandTag();
    if (child.getElementType() == XmlElementType.XML_TAG) {
      // compute where to insert tag according to DTD or XSD
      final XmlTag[] subTags = myTag.getSubTags();
      XmlElementDescriptor parentDescriptor = myTag.getDescriptor();
      final PsiElement declaration = parentDescriptor != null ? parentDescriptor.getDeclaration() : null;
      // filtering out generated dtds
      if (declaration != null &&
          declaration.getContainingFile() != null &&
          declaration.getContainingFile().isPhysical() &&
          subTags.length > 0) {
        final XmlElementDescriptor[] childElementDescriptors = parentDescriptor.getElementsDescriptors(myTag);
        int subTagNum = -1;
        for (final XmlElementDescriptor childElementDescriptor : childElementDescriptors) {
          final String childElementName = childElementDescriptor.getName();
          while (subTagNum < subTags.length - 1 && subTags[subTagNum + 1].getName().equals(childElementName)) {
            subTagNum++;
          }
          ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(child);
          if (startTagName != null && childElementName.equals(startTagName.getText())) {
            // insert child just after anchor
            // insert into the position specified by index
            if (subTagNum >= 0) {
              final ASTNode subTag = subTags[subTagNum].getNode();
              if (subTag.getTreeParent() != myTag.getNode()) {
                // in entity
                final XmlEntityRef entityRef = PsiTreeUtil.getParentOfType(subTags[subTagNum], XmlEntityRef.class);
                throw new IncorrectOperationException(
                  "Can't insert subtag to the entity. Entity reference text: " + (entityRef == null ? "" : entityRef.getText()));
              }
              return addInternalSuper(child, child, subTag, Boolean.FALSE);
            }
            else {
              ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(myTag.getNode());
              return addInternalSuper(child, child, startTagEnd, Boolean.FALSE);
            }
          }
        }
      }
      else {
        ASTNode closingTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(myTag.getNode());
        return addInternalSuper(child, child, closingTagStart, Boolean.TRUE);
      }
    }
    return addInternalSuper(child, child, anchor, Boolean.TRUE);
  }

  private TreeElement insertAttribute(ASTNode anchor, TreeElement child, boolean before) {
    if (anchor == null) {
      ASTNode tagNode = myTag.getNode();
      ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tagNode);
      if (startTagEnd == null) startTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tagNode);

      if (startTagEnd == null) {
        anchor = tagNode.getLastChildNode();

        while (anchor instanceof PsiWhiteSpace) {
          anchor = anchor.getTreePrev();
        }

        if (anchor instanceof PsiErrorElement) {
          final LeafElement token = Factory
            .createSingleLeafElement(XmlTokenType.XML_EMPTY_ELEMENT_END, "/>", 0, 2, SharedImplUtil.findCharTableByTree(anchor),
                                     myTag.getManager());
          tagNode.replaceChild(anchor, token);
          startTagEnd = token;
        }
      }

      if (startTagEnd == null) {
        anchor = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagNode);
        return addInternalSuper(child, child, anchor, Boolean.FALSE);
      }
      return addInternalSuper(child, child, startTagEnd, Boolean.TRUE);
    }
    return genericInsert(child, anchor, before);
  }

  protected TreeElement genericInsert(TreeElement child, ASTNode anchor, boolean before) {
    return addInternalSuper(child, child, anchor, before);
  }
}
