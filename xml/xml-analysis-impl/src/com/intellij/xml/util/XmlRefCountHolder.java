// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.IdReferenceProvider;
import com.intellij.psi.impl.source.xml.PossiblePrefixReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XmlRefCountHolder {
  private static final Key<CachedValue<XmlRefCountHolder>> xmlRefCountHolderKey = Key.create("xml ref count holder");

  private static final UserDataCache<CachedValue<XmlRefCountHolder>, XmlFile, Object> CACHE =
    new UserDataCache<>() {
      @Override
      protected CachedValue<XmlRefCountHolder> compute(final XmlFile file, final Object p) {
        return CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
          final XmlRefCountHolder holder = new XmlRefCountHolder();
          final Language language = file.getViewProvider().getBaseLanguage();
          final PsiFile psiFile = file.getViewProvider().getPsi(language);
          assert psiFile != null;
          psiFile.accept(new IdGatheringRecursiveVisitor(holder));
          return new CachedValueProvider.Result<>(holder, file);
        }, false);
      }
    };

  private final Map<String, List<Pair<XmlAttributeValue, Boolean>>> myId2AttributeListMap = new HashMap<>();
  private final Set<XmlAttributeValue> myPossiblyDuplicateIds = new HashSet<>();
  private final List<XmlAttributeValue> myIdReferences = new ArrayList<>();
  private final Set<String> myAdditionallyDeclaredIds = new HashSet<>();
  private final Set<PsiElement> myDoNotValidateParentsList = new HashSet<>();
  private final Set<String> myUsedPrefixes = new HashSet<>();
  private final Set<String> myUsedNamespaces = new HashSet<>();

  private static final Pattern PREFIX_PATTERN = Pattern.compile("[\\w_][\\w_.]*:");

  @Nullable
  public static XmlRefCountHolder getRefCountHolder(@NotNull XmlFile file) {
    return CACHE.get(xmlRefCountHolderKey, file, null).getValue();
  }

  private XmlRefCountHolder() {
  }


  public boolean isDuplicateIdAttributeValue(@NotNull final XmlAttributeValue value) {
    return myPossiblyDuplicateIds.contains(value);
  }

  public boolean isValidatable(@Nullable final PsiElement element) {
    return !myDoNotValidateParentsList.contains(element);
  }

  public boolean hasIdDeclaration(@NotNull final String idRef) {
    return myId2AttributeListMap.get(idRef) != null || myAdditionallyDeclaredIds.contains(idRef);
  }

  public boolean isIdReferenceValue(@NotNull final XmlAttributeValue value) {
    return myIdReferences.contains(value);
  }

  private void registerId(@NotNull final String id, @NotNull final XmlAttributeValue attributeValue, final boolean soft) {
    List<Pair<XmlAttributeValue, Boolean>> list = myId2AttributeListMap.get(id);
    if (list == null) {
      list = new ArrayList<>();
      myId2AttributeListMap.put(id, list);
    }
    else if (!soft) {
      final boolean html = HtmlUtil.isHtmlFile(attributeValue);
      final boolean html5 = HtmlUtil.isHtml5Context(attributeValue);

      // mark as duplicate
      List<XmlAttributeValue> notSoft = ContainerUtil.mapNotNull(list,
                                                                 (NullableFunction<Pair<XmlAttributeValue, Boolean>, XmlAttributeValue>)pair -> {
                                                                   if (html5 && !"id".equalsIgnoreCase(((XmlAttribute)pair.first.getParent()).getName())) {
                                                                     // according to HTML 5 (http://www.w3.org/TR/html5/dom.html#the-id-attribute) spec
                                                                     // only id attribute is unique identifier
                                                                     return null;
                                                                   }
                                                                   if (html && pair.first.getParent().getParent() == attributeValue.getParent().getParent()) {
                                                                     // according to HTML 4 (http://www.w3.org/TR/html401/struct/global.html#adef-id,
                                                                     // http://www.w3.org/TR/html401/struct/links.html#h-12.2.3) spec id and name occupy
                                                                     // same namespace, but having same values on one tag is ok
                                                                     return null;
                                                                   }
                                                                   return pair.second ? null : pair.first;
                                                                 });
      if (!notSoft.isEmpty()) {
        myPossiblyDuplicateIds.addAll(notSoft);
        myPossiblyDuplicateIds.add(attributeValue);
      }
    }

    list.add(new Pair<>(attributeValue, soft));
  }

  private void registerAdditionalId(@NotNull final String id) {
    myAdditionallyDeclaredIds.add(id);
  }

  private void registerIdReference(@NotNull final XmlAttributeValue value) {
    myIdReferences.add(value);
  }

  private void registerOuterLanguageElement(@NotNull final PsiElement element) {
    PsiElement parent = element.getParent();

    if (parent instanceof XmlText) {
      parent = parent.getParent();
    }

    myDoNotValidateParentsList.add(parent);
  }

  public boolean isInUse(String prefix) {
    return myUsedPrefixes.contains(prefix);
  }

  public boolean isUsedNamespace(String ns) {
    return myUsedNamespaces.contains(ns);
  }

  private static final class IdGatheringRecursiveVisitor extends XmlRecursiveElementWalkingVisitor {
    private final XmlRefCountHolder myHolder;

    private IdGatheringRecursiveVisitor(@NotNull XmlRefCountHolder holder) {
      super(true);
      myHolder = holder;
    }

    @Override
    public void visitElement(@NotNull final PsiElement element) {
      if (element instanceof OuterLanguageElement) {
        visitOuterLanguageElement(element);
      }

      super.visitElement(element);
    }

    private void visitOuterLanguageElement(@NotNull final PsiElement element) {
      myHolder.registerOuterLanguageElement(element);
      PsiReference[] references = element.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PossiblePrefixReference && ((PossiblePrefixReference)reference).isPrefixReference()) {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof SchemaPrefix) {
            myHolder.addUsedPrefix(((SchemaPrefix)resolve).getName());
          }
        }
      }
    }

    @Override
    public void visitComment(@NotNull final PsiComment comment) {
      doVisitAnyComment(comment);
      super.visitComment(comment);
    }

    @Override
    public void visitXmlComment(final @NotNull XmlComment comment) {
      doVisitAnyComment(comment);
      super.visitXmlComment(comment);
    }

    private void doVisitAnyComment(final PsiComment comment) {
      final String id = XmlDeclareIdInCommentAction.getImplicitlyDeclaredId(comment);
      if (id != null) {
        myHolder.registerAdditionalId(id);
      }
    }

    @Override
    public void visitXmlTag(@NotNull XmlTag tag) {
      myHolder.addUsedPrefix(tag.getNamespacePrefix());
      myHolder.addUsedNamespace(tag.getNamespace());
      String text = tag.getValue().getTrimmedText();
      detectPrefix(text, tag);
      super.visitXmlTag(tag);
    }

    @Override
    public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
      if (!attribute.isNamespaceDeclaration()) {
        myHolder.addUsedPrefix(attribute.getNamespacePrefix());
      }
      myHolder.addUsedNamespace(attribute.getNamespace());
      super.visitXmlAttribute(attribute);
    }

    @Override
    public void visitXmlAttributeValue(final @NotNull XmlAttributeValue value) {
      final PsiElement element = value.getParent();
      if (!(element instanceof XmlAttribute attribute)) return;

      final XmlTag tag = attribute.getParent();
      if (tag == null) return;

      final XmlElementDescriptor descriptor = tag.getDescriptor();
      if (descriptor == null) return;

      final XmlAttributeDescriptor attributeDescriptor = attribute.getDescriptor();
      if (attributeDescriptor != null) {
        if (attributeDescriptor.hasIdType()) {
          updateMap(attribute, value, false);
        }
        else {
          final PsiReference[] references = value.getReferences();
          for (PsiReference r : references) {
            if (r instanceof IdReferenceProvider.GlobalAttributeValueSelfReference /*&& !r.isSoft()*/) {
              updateMap(attribute, value, r.isSoft());
            }
            else if (r instanceof SchemaPrefixReference) {
              SchemaPrefix prefix = ((SchemaPrefixReference)r).resolve();
              if (prefix != null) {
                myHolder.addUsedPrefix(prefix.getName());
              }
            }
          }
        }

        if (attributeDescriptor.hasIdRefType() && PsiTreeUtil.getChildOfType(value, OuterLanguageElement.class) == null) {
          myHolder.registerIdReference(value);
        }
      }

      String s = value.getValue();
      detectPrefix(s, tag);
      super.visitXmlAttributeValue(value);
    }

    private void detectPrefix(String s, XmlTag tag) {
      if (s != null && !(tag instanceof HtmlTag) && s.length() < 1000) {
        Matcher matcher = PREFIX_PATTERN.matcher(s);
        while (matcher.find()) {
          String group = matcher.group();
          myHolder.addUsedPrefix(group.substring(0, group.length() - 1));
        }
      }
    }

    private void updateMap(@NotNull final XmlAttribute attribute, @NotNull final XmlAttributeValue value, final boolean soft) {
      final String id = XmlHighlightVisitor.getUnquotedValue(value, attribute.getParent());
      if (XmlUtil.isSimpleValue(id, value) &&
          PsiTreeUtil.getChildOfType(value, OuterLanguageElement.class) == null) {
        myHolder.registerId(id, value, soft);
      }
    }
  }

  private void addUsedPrefix(String prefix) {
    myUsedPrefixes.add(prefix);
  }

  private void addUsedNamespace(String ns) {
    myUsedNamespaces.add(ns);
  }
}
