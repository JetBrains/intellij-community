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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @by Maxim.Mossienko
 * @author Konstantin Bulenkov
 */
public class SchemaReferencesProvider extends PsiReferenceProvider {
  @NonNls private static final String VALUE_ATTR_NAME = "value";
  @NonNls private static final String PATTERN_TAG_NAME = "pattern";
  @NonNls private static final String NAME_ATTR_NAME = "name";

  @NonNls private static final String MEMBER_TYPES_ATTR_NAME = "memberTypes";
  @NonNls private static final String ITEM_TYPE_ATTR_NAME = "itemType";
  @NonNls private static final String BASE_ATTR_NAME = "base";
  @NonNls private static final String GROUP_TAG_NAME = "group";

  @NonNls private static final String ATTRIBUTE_GROUP_TAG_NAME = "attributeGroup";
  @NonNls private static final String ATTRIBUTE_TAG_NAME = "attribute";
  @NonNls private static final String ELEMENT_TAG_NAME = "element";
  @NonNls private static final String SIMPLE_TYPE_TAG_NAME = "simpleType";

  @NonNls private static final String COMPLEX_TYPE_TAG_NAME = "complexType";
  @NonNls private static final String REF_ATTR_NAME = "ref";
  @NonNls private static final String TARGET_NAMESPACE = "targetNamespace";
  @NonNls private static final String TYPE_ATTR_NAME = "type";
  @NonNls private static final String SUBSTITUTION_GROUP_ATTR_NAME = "substitutionGroup";

  public String[] getCandidateAttributeNamesForSchemaReferences() {
    return new String[] {REF_ATTR_NAME,TYPE_ATTR_NAME, BASE_ATTR_NAME,NAME_ATTR_NAME, SUBSTITUTION_GROUP_ATTR_NAME,MEMBER_TYPES_ATTR_NAME,
      VALUE_ATTR_NAME, ITEM_TYPE_ATTR_NAME};
  }

  static class RegExpReference extends BasicAttributeValueReference implements EmptyResolveMessageProvider {
    private String message;

    public RegExpReference(final PsiElement element) {
      super(element);
    }

    private static final Pattern pattern = Pattern.compile("^(?:\\\\i|\\\\l)");
    private static final Pattern pattern2 = Pattern.compile("([^\\\\])(?:\\\\i|\\\\l)");

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

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return false;
    }

    public String getUnresolvedMessagePattern() {
      return message;
    }
  }

  public static class NameReference implements PsiReference {
    private final PsiElement myElement;

    public NameReference(PsiElement element) {
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

    @NotNull
    public String getCanonicalText() {
      String text = myElement.getText();
      return text.substring(1,text.length()- 1);
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return ElementManipulators.getManipulator(myElement).handleContentChange(
        myElement,
        getRangeInElement(),
        newElementName.substring(newElementName.indexOf(':') + 1)
      );
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return true;
    }
  }

  public static class TypeOrElementOrAttributeReference implements PsiReference, QuickFixProvider<TypeOrElementOrAttributeReference> {
    private final PsiElement myElement;
    private TextRange myRange;
    private String nsPrefix;

    public void registerQuickfix(HighlightInfo info, TypeOrElementOrAttributeReference reference) {
      if (myType == ReferenceType.TypeReference) {
        QuickFixAction.registerQuickFixAction(
          info, new CreateXmlElementIntentionAction("xml.schema.create.complex.type.intention.name",COMPLEX_TYPE_TAG_NAME,reference)
        );
        QuickFixAction.registerQuickFixAction(
          info, new CreateXmlElementIntentionAction("xml.schema.create.simple.type.intention.name",SIMPLE_TYPE_TAG_NAME,reference)
        );
      } else if (myType != null) {
        @PropertyKey(resourceBundle = XmlBundle.PATH_TO_BUNDLE) String key = null;
        @NonNls String declarationTagName = null;

        if (myType == ReferenceType.ElementReference) {
          declarationTagName = ELEMENT_TAG_NAME;
          key = "xml.schema.create.element.intention.name";
        } else if (myType == ReferenceType.AttributeReference) {
          declarationTagName = ATTRIBUTE_TAG_NAME;
          key = "xml.schema.create.attribute.intention.name";
        } else if (myType == ReferenceType.AttributeGroupReference) {
          declarationTagName = ATTRIBUTE_GROUP_TAG_NAME;
          key = "xml.schema.create.attribute.group.intention.name";
        } else if (myType == ReferenceType.GroupReference) {
          declarationTagName = GROUP_TAG_NAME;
          key = "xml.schema.create.group.intention.name";
        }

        assert key != null && declarationTagName != null;
        QuickFixAction.registerQuickFixAction(
          info, new CreateXmlElementIntentionAction(key, declarationTagName, reference)
        );
      }
    }

    public void setNamespacePrefix(String prefix) {
      this.nsPrefix = prefix;
    }

    enum ReferenceType {
      ElementReference, AttributeReference, GroupReference, AttributeGroupReference, TypeReference
    }

    private ReferenceType myType;

    TypeOrElementOrAttributeReference(PsiElement element, TextRange range) {
      myElement = element;
      myRange   = range;
      
      assert myRange.getLength() >= 0;

      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
      final XmlTag tag = attribute.getParent();
      final String localName = tag.getLocalName();
      final String attributeLocalName = attribute.getLocalName();

      if (REF_ATTR_NAME.equals(attributeLocalName) || SUBSTITUTION_GROUP_ATTR_NAME.equals(attributeLocalName)) {
        if (localName.equals(GROUP_TAG_NAME)) {
          myType = ReferenceType.GroupReference;
        } else if (localName.equals(ATTRIBUTE_GROUP_TAG_NAME)) {
          myType = ReferenceType.AttributeGroupReference;
        } else if (ELEMENT_TAG_NAME.equals(localName)) {
          myType = ReferenceType.ElementReference;
        } else if (ATTRIBUTE_TAG_NAME.equals(localName)) {
          myType = ReferenceType.AttributeReference;
        }
      } else if (TYPE_ATTR_NAME.equals(attributeLocalName) ||
                 BASE_ATTR_NAME.equals(attributeLocalName) ||
                 MEMBER_TYPES_ATTR_NAME.equals(attributeLocalName) ||
                 ITEM_TYPE_ATTR_NAME.equals(attributeLocalName)
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
        psiElement = ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false);
      } else {
        psiElement = resolveInner();
      }

      return psiElement != PsiUtilBase.NULL_PSI_ELEMENT ? psiElement:null;
    }

    private PsiElement resolveInner() {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);
      if (tag == null) return PsiUtilBase.NULL_PSI_ELEMENT;

      String canonicalText = getCanonicalText();
      XmlNSDescriptorImpl nsDescriptor = getDescriptor(tag,canonicalText);

      if (myType != null && nsDescriptor != null && nsDescriptor.getTag() != null) {

        switch(myType) {
          case GroupReference: return nsDescriptor.findGroup(canonicalText);
          case AttributeGroupReference: return nsDescriptor.findAttributeGroup(canonicalText);
          case ElementReference: {
            XmlElementDescriptor descriptor = nsDescriptor.getElementDescriptor(
              XmlUtil.findLocalNameByQualifiedName(canonicalText), getNamespace(tag, canonicalText),
              new HashSet<XmlNSDescriptorImpl>(),
              true
            );

            return descriptor != null ? descriptor.getDeclaration(): PsiUtilBase.NULL_PSI_ELEMENT;
          }
          case AttributeReference: {
            //final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(canonicalText);
            final String localNameByQualifiedName = XmlUtil.findLocalNameByQualifiedName(canonicalText);
            XmlAttributeDescriptor descriptor = nsDescriptor.getAttribute(
              localNameByQualifiedName,
              getNamespace(tag, canonicalText),
              tag
            );

            if (descriptor != null) return descriptor.getDeclaration();

            return PsiUtilBase.NULL_PSI_ELEMENT;
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

      return PsiUtilBase.NULL_PSI_ELEMENT;
    }

    private XmlNSDescriptorImpl getDescriptor(final XmlTag tag, String text) {
      if (myType != ReferenceType.ElementReference &&
          myType != ReferenceType.AttributeReference) {
        final PsiElement parentElement = myElement.getParent();
        final PsiElement grandParentElement = parentElement != null ? parentElement.getParent() : null;
        boolean doRedefineCheck = false;

        if (parentElement instanceof XmlAttribute &&
            grandParentElement instanceof XmlTag
           ) {
          final String attrName = ((XmlAttribute)parentElement).getName();
          final String tagLocalName = ((XmlTag)grandParentElement).getLocalName();

          doRedefineCheck = (REF_ATTR_NAME.equals(attrName) &&
            ( GROUP_TAG_NAME.equals(tagLocalName) ||
              ATTRIBUTE_GROUP_TAG_NAME.equals(tagLocalName)
            )
           ) ||
           ( BASE_ATTR_NAME.equals(attrName) ||
             MEMBER_TYPES_ATTR_NAME.equals(attrName)
           );
        }

        if (doRedefineCheck) {
          XmlNSDescriptorImpl redefinedDescriptor = findRedefinedDescriptor(tag, text);
          if (redefinedDescriptor != null) return redefinedDescriptor;
        }
      }

      final String namespace = getNamespace(tag, text);
      XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace,true);

      final XmlDocument document = ((XmlFile)tag.getContainingFile()).getDocument();

      if (nsDescriptor == null) { // import
        nsDescriptor = (XmlNSDescriptor)document.getMetaData();
      }

      if (nsDescriptor == null) {
        final XmlNSDescriptor[] descrs = new XmlNSDescriptor[1];

        URLReference.processWsdlSchemas(
          document.getRootTag(),
          new Processor<XmlTag>() {
            public boolean process(final XmlTag xmlTag) {
              if (namespace.equals(xmlTag.getAttributeValue(TARGET_NAMESPACE))) {
                descrs[0] = (XmlNSDescriptor)xmlTag.getMetaData();
                return false;
              }
              return true;
            }
          }
        );

        if (descrs[0] instanceof XmlNSDescriptorImpl) return (XmlNSDescriptorImpl)descrs[0];
      }

      return nsDescriptor instanceof XmlNSDescriptorImpl ? (XmlNSDescriptorImpl)nsDescriptor:null;
    }

    private static String getNamespace(final XmlTag tag, final String text) {
      final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(text);
      final String namespaceByPrefix = tag.getNamespaceByPrefix(namespacePrefix);
      if (namespaceByPrefix.length() > 0) return namespaceByPrefix;
      final XmlTag rootTag = ((XmlFile)tag.getContainingFile()).getDocument().getRootTag();

      if (rootTag != null &&
          "schema".equals(rootTag.getLocalName()) &&
          XmlUtil.ourSchemaUrisList.indexOf(rootTag.getNamespace()) != -1 ) {
        final String targetNS = rootTag.getAttributeValue(TARGET_NAMESPACE);
        
        if (targetNS != null) {
          final String targetNsPrefix = rootTag.getPrefixByNamespace(targetNS);

          if (namespacePrefix.equals(targetNsPrefix) ||
              (namespaceByPrefix.length() == 0 && targetNsPrefix == null)) {
            return targetNS;
          }
        }
      }
      return namespaceByPrefix;
    }

    @NotNull
    public String getCanonicalText() {
      final String text = myElement.getText();
      String name = myRange.getEndOffset() < text.length() ? myRange.substring(text) : "";
      if (name.length() > 0 && nsPrefix != null && nsPrefix.length() > 0) {
        name = nsPrefix + ":" + name;
      }
      return name;
    }

    public PsiElement handleElementRename(String _newElementName) throws IncorrectOperationException {
      final String canonicalText = getCanonicalText();
      //final String newElementName = canonicalText.substring(0,canonicalText.indexOf(':') + 1) + _newElementName;
      final String newElementName = _newElementName;

      final PsiElement element = ElementManipulators.getManipulator(myElement)
        .handleContentChange(myElement, getRangeInElement(), newElementName);
      myRange = new TextRange(myRange.getStartOffset(),myRange.getEndOffset() - (canonicalText.length() - newElementName.length()));
      return element;
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException();
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    class CompletionProcessor implements PsiElementProcessor<XmlTag> {
      List<String> myElements = new ArrayList<String>(1);
      String namespace;
      XmlTag tag;

      public boolean execute(final XmlTag element) {
        String name = element.getAttributeValue(NAME_ATTR_NAME);
        final String prefixByNamespace = tag.getPrefixByNamespace(namespace);
        if (prefixByNamespace != null && prefixByNamespace.length() > 0 && nsPrefix == null) {
          name = prefixByNamespace + ":" + name;
        }
        myElements.add( name );
        return true;
      }
    }

    @NotNull
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

      XmlDocument document = ((XmlFile)myElement.getContainingFile()).getDocument();
      final XmlTag rootTag = document.getRootTag();
      String ourNamespace = rootTag != null ? rootTag.getAttributeValue(TARGET_NAMESPACE) : "";
      if (ourNamespace == null) ourNamespace = "";

      for(String namespace:tag.knownNamespaces()) {
        if (ourNamespace.equals(namespace)) continue;
        final XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace, true);

        if (nsDescriptor instanceof XmlNSDescriptorImpl) {
          processNamespace(namespace, processor, nsDescriptor, tagNames);
        }
      }


      XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
      if (nsDescriptor != null) {
        processNamespace(
          ourNamespace,
          processor,
          nsDescriptor,
          tagNames
        );
      }

      return ArrayUtil.toStringArray(processor.myElements);
    }

    private static void processNamespace(final String namespace,
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

    private static class MyResolver implements ResolveCache.Resolver {
      static MyResolver INSTANCE = new MyResolver(); 
      public PsiElement resolve(PsiReference ref, boolean incompleteCode) {
        return ((TypeOrElementOrAttributeReference)ref).resolveInner();
      }
    }
  }

  private static class CreateXmlElementIntentionAction implements IntentionAction {
    private final String myMessageKey;
    protected final TypeOrElementOrAttributeReference myRef;
    private boolean myIsAvailableEvaluated;
    private XmlFile myTargetFile;
    private final String myDeclarationTagName;

    CreateXmlElementIntentionAction(
      @PropertyKey(resourceBundle = XmlBundle.PATH_TO_BUNDLE) String messageKey,
      @NonNls @NotNull String declarationTagName,
      TypeOrElementOrAttributeReference ref) {

      myMessageKey = messageKey;
      myRef = ref;
      myDeclarationTagName = declarationTagName;
    }

    @NotNull
    public String getText() {
      return XmlBundle.message(myMessageKey,XmlUtil.findLocalNameByQualifiedName(myRef.getCanonicalText()));
    }

    @NotNull
    public String getFamilyName() {
      return XmlBundle.message("xml.create.xml.declaration.intention.type");
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
      if (!myIsAvailableEvaluated) {
        final XmlTag tag = PsiTreeUtil.getParentOfType(myRef.getElement(), XmlTag.class);
        if (tag != null) {
          final XmlNSDescriptorImpl descriptor = myRef.getDescriptor(tag, myRef.getCanonicalText());

          if (descriptor != null &&
              descriptor.getDescriptorFile() != null &&
              descriptor.getDescriptorFile().isWritable()
             ) {
            myTargetFile = descriptor.getDescriptorFile();
          }
        }
        myIsAvailableEvaluated = true;
      }
      return myTargetFile != null;
    }

    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
      if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

      final XmlTag rootTag = myTargetFile.getDocument().getRootTag();

      OpenFileDescriptor descriptor = new OpenFileDescriptor(
        project,
        myTargetFile.getVirtualFile(),
        rootTag.getValue().getTextRange().getEndOffset()
      );
      Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      TemplateManager manager = TemplateManager.getInstance(project);
      final Template template = manager.createTemplate("", "");

      addTextTo(template, rootTag);

      manager.startTemplate(targetEditor, template);
    }

    protected void addTextTo(Template template, XmlTag rootTag) {
      String schemaPrefix = rootTag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_URI);
      if (schemaPrefix.length() > 0) schemaPrefix += ":";

      template.addTextSegment(
        "<" + schemaPrefix + myDeclarationTagName + " name=\"" + XmlUtil.findLocalNameByQualifiedName(myRef.getCanonicalText()) + "\">"
      );
      template.addEndVariable();
      template.addTextSegment(
        "</" + schemaPrefix + myDeclarationTagName + ">\n"
      );
      template.setToReformat(true);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
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
      final int testLength = text.length();

      for(int i = 1; i < testLength; ++i) {
        if (Character.isWhitespace(text.charAt(i))) {
          if (lastIndex != i) result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, i) ) );
          lastIndex = i + 1;
        }
      }

      if (lastIndex != testLength - 1) result.add( new TypeOrElementOrAttributeReference(element, new TextRange(lastIndex, testLength - 1) ) );
      return result.toArray(new PsiReference[result.size()]);
    } else {
      final PsiReference prefix = createSchemaPrefixReference(element);
      final PsiReference ref = createTypeOrElementOrAttributeReference(element, prefix == null ? null : prefix.getCanonicalText());
      return prefix == null ? new PsiReference[] {ref} : new PsiReference[] {ref, prefix};
    }
  }

  public static PsiReference createTypeOrElementOrAttributeReference(final PsiElement element) {
    return createTypeOrElementOrAttributeReference(element, null);
  }

  public static PsiReference createTypeOrElementOrAttributeReference(final PsiElement element, String ns) {
    final int length = element.getTextLength();
    int offset = (element instanceof XmlAttributeValue) ?
      XmlUtil.findPrefixByQualifiedName(((XmlAttributeValue)element).getValue()).length() : 0;
    if (offset > 0) offset++;
    final TypeOrElementOrAttributeReference ref = new TypeOrElementOrAttributeReference(element, length >= 2 ? new TextRange(1 + offset, length - 1) : new TextRange(0,0));
    ref.setNamespacePrefix(ns);
    return ref;
  }

  @Nullable
  private static PsiReference createSchemaPrefixReference(final PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue attributeValue = (XmlAttributeValue)element;
      final String prefix = XmlUtil.findPrefixByQualifiedName(attributeValue.getValue());
      if (prefix.length() > 0) {
        return new SchemaPrefixReference(attributeValue, TextRange.from(1, prefix.length()), prefix, null);
      }
    }
    return null;
  }

  public static @Nullable XmlNSDescriptorImpl findRedefinedDescriptor(XmlTag tag, String text) {
    for(XmlTag parentTag = tag.getParentTag(); parentTag != null; parentTag = parentTag.getParentTag()) {

      if (text.equals(parentTag.getAttributeValue("name"))) {
        final XmlTag grandParent = parentTag.getParentTag();

        if (grandParent != null && "redefine".equals(grandParent.getLocalName())) {
          return XmlNSDescriptorImpl.getRedefinedElementDescriptor(grandParent);
        }
      }
    }

    return null;
  }
}
