/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 18:55:15
 * To change this template use Options | File Templates.
 */
public class XmlCompletionData extends CompletionData {
  public XmlCompletionData() {
    declareFinalScope(XmlTag.class);
    declareFinalScope(XmlAttribute.class);
    declareFinalScope(XmlAttributeValue.class);

    {
      final CompletionVariant variant = new CompletionVariant(createTagCompletionFilter());
      variant.includeScopeClass(XmlTag.class);
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(createAttributeCompletionFilter());
      variant.includeScopeClass(XmlAttribute.class);
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      registerVariant(variant);
    }

    {
      XmlAttributeValueGetter getter = getAttributeValueGetter();
      if (getter != null) {
        final CompletionVariant variant = new CompletionVariant(createAttributeValueCompletionFilter());
        variant.includeScopeClass(XmlAttributeValue.class);
        variant.addCompletion(getter, TailType.NONE);
        variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
        registerVariant(variant);
      }
    }

    final ElementFilter entityCompletionFilter = createXmlEntityCompletionFilter();

    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS), new NotFilter(entityCompletionFilter), new ElementFilter() {
          @Override
          public boolean isAcceptable(Object element, PsiElement context) {
            XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
            if (tag != null) {
              return XmlUtil.getSchemaSimpleContent(tag) != null;
            }
            return false;
          }

          @Override
          public boolean isClassAcceptable(Class hintClass) {
            return true;
          }
        }));
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new SimpleTagContentEnumerationValuesGetter(), TailType.NONE);

      registerVariant(variant);
    }
    
    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS), new NotFilter(entityCompletionFilter)));
      variant.includeScopeClass(XmlToken.class, true);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(entityCompletionFilter);
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new EntityRefGetter());
      variant.setInsertHandler(new EntityRefInsertHandler());
      registerVariant(variant);
    }
  }

  protected ElementFilter createXmlEntityCompletionFilter() {
    return new AndFilter(new LeftNeighbour(new XmlTextFilter("&")), new OrFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS),
                                                                                 new XmlTokenTypeFilter(
                                                                                     XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)));
  }

  protected XmlAttributeValueGetter getAttributeValueGetter() {
    return new XmlAttributeValueGetter();
  }

  protected ElementFilter createAttributeCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createAttributeValueCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createTagCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  private static class SimpleTagContentEnumerationValuesGetter implements ContextGetter {
    @Override
    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
      if (tag != null) {
        final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent(tag);
        if (simpleContent != null) {
          final HashSet<String> variants = new HashSet<>();
          XmlUtil.collectEnumerationValues(simpleContent, variants);
          return ArrayUtil.toObjectArray(variants);
        }
      }

      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  protected static class EntityRefGetter implements ContextGetter {

    @Nullable
    private static Object getLookupItem(@Nullable final XmlEntityDecl decl) {
      if (decl == null) {
        return null;
      }

      final String name = decl.getName();
      if (name == null) {
        return null;
      }

      final XmlAttributeValue value = decl.getValueElement();
      final ASTNode node = value.getNode();
      if (node != null) {
        final ASTNode[] nodes = node.getChildren(TokenSet.create(XmlTokenType.XML_CHAR_ENTITY_REF));
        if (nodes.length == 1) {
          final String valueText = nodes[0].getText();
          final int i = valueText.indexOf('#');
          if (i > 0) {
            String s = valueText.substring(i + 1);
            s = StringUtil.trimEnd(s, ";");

            try {
              final int unicodeChar = Integer.valueOf(s).intValue();
              return LookupValueFactory.createLookupValueWithHint(name, null, String.valueOf((char)unicodeChar));
            }
            catch (NumberFormatException e) {
              return null;
            }
          }
        }
      }

      return null;
    }

    @Override
    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      XmlFile containingFile = null;
      XmlFile descriptorFile = null;
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class);

      if (tag != null) {
        containingFile = (XmlFile)tag.getContainingFile();
        descriptorFile = findDescriptorFile(tag, containingFile);
      }

      if (HtmlUtil.isHtml5Context(tag)) {
        descriptorFile = XmlUtil.findXmlFile(containingFile, Html5SchemaProvider.getCharsDtdLocation());
      } else if (tag == null) {
        final XmlDocument document = PsiTreeUtil.getParentOfType(context, XmlDocument.class);

        if (document != null) {
          containingFile = (XmlFile)document.getContainingFile();

          final FileType ft = containingFile.getFileType();
          if (HtmlUtil.isHtml5Document(document)) {
            descriptorFile = XmlUtil.findXmlFile(containingFile, Html5SchemaProvider.getCharsDtdLocation());
          } else if(ft != StdFileTypes.XML) {
            final String namespace = ft == StdFileTypes.XHTML || ft == StdFileTypes.JSPX ? XmlUtil.XHTML_URI : XmlUtil.HTML_URI;
            final XmlNSDescriptor nsDescriptor = document.getDefaultNSDescriptor(namespace, true);

            if (nsDescriptor != null) {
              descriptorFile = nsDescriptor.getDescriptorFile();
            }
          }
        }
      }

      if (descriptorFile != null) {
        final List<Object> results = new ArrayList<>();
        final boolean acceptSystemEntities = containingFile.getFileType() == StdFileTypes.XML;

        final PsiElementProcessor processor = new PsiElementProcessor() {
          @Override
          public boolean execute(@NotNull final PsiElement element) {
            if (element instanceof XmlEntityDecl) {
              final XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)element;
              if (xmlEntityDecl.isInternalReference() || acceptSystemEntities) {
                final String name = xmlEntityDecl.getName();
                final Object _item = getLookupItem(xmlEntityDecl);
                results.add(_item == null ? name : _item);
              }
            }
            return true;
          }
        };

        XmlUtil.processXmlElements(descriptorFile, processor, true);
        if (descriptorFile != containingFile && containingFile.getFileType() == StdFileTypes.XML) {
          final XmlProlog element = containingFile.getDocument().getProlog();
          if (element != null) XmlUtil.processXmlElements(element, processor, true);
        }

        return ArrayUtil.toObjectArray(results);
      }

      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  public static XmlFile findDescriptorFile(@NotNull XmlTag tag, @NotNull XmlFile containingFile) {
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    final XmlNSDescriptor nsDescriptor = descriptor != null ? descriptor.getNSDescriptor() : null;
    XmlFile descriptorFile = nsDescriptor != null
                             ? nsDescriptor.getDescriptorFile()
                             : containingFile.getDocument().getProlog().getDoctype() != null ? containingFile : null;
    if (nsDescriptor != null && (descriptorFile == null || descriptorFile.getName().equals(containingFile.getName() + ".dtd"))) {
      descriptorFile = containingFile;
    }
    return descriptorFile;
  }

  protected static class EntityRefInsertHandler extends BasicInsertHandler {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      super.handleInsert(context, item);
      context.setAddCompletionChar(false);
      final CaretModel caretModel = context.getEditor().getCaretModel();
      context.getEditor().getDocument().insertString(caretModel.getOffset(), ";");
      caretModel.moveToOffset(caretModel.getOffset() + 1);
    }
  }
}
