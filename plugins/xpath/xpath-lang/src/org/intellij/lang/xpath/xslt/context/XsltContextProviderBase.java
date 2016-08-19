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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.psi.XsltWithParam;
import org.intellij.lang.xpath.xslt.util.NSDeclTracker;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.01.11
*/
public abstract class XsltContextProviderBase extends ContextProvider {
  protected static final Set<String> IGNORED_URIS = new THashSet<>();

  static {
    IGNORED_URIS.add(XsltSupport.XSLT_NS);
    IGNORED_URIS.addAll(XmlUtil.ourSchemaUrisList);
  }

  private static final SimpleFieldCache<CachedValue<ElementNames>, XsltContextProviderBase> myNamesCache = new SimpleFieldCache<CachedValue<ElementNames>, XsltContextProviderBase>() {
    protected CachedValue<ElementNames> compute(final XsltContextProviderBase xsltContextProvider) {
      return xsltContextProvider.createCachedValue(xsltContextProvider.getFile());
    }

    protected CachedValue<ElementNames> getValue(final XsltContextProviderBase xsltContextProvider) {
      return xsltContextProvider.myNames;
    }

    protected void putValue(final CachedValue<ElementNames> elementNamesCachedValue, final XsltContextProviderBase xsltContextProvider) {
      xsltContextProvider.myNames = elementNamesCachedValue;
    }
  };

  private CachedValue<ElementNames> myNames;
  protected final SmartPsiElementPointer<XmlElement> myContextElement;
  protected final FileAssociationsManager myFileAssociationsManager;

  protected XsltContextProviderBase(XmlElement element) {
    final Project project = element.getProject();
    myFileAssociationsManager = FileAssociationsManager.getInstance(project);
    myContextElement = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element);
    attachTo(element);
  }

  @Override
  protected boolean isValid() {
    return super.isValid() && matchContextType();
  }

  private boolean matchContextType() {
    final PsiFile file = myContextElement.getContainingFile();
    return file != null && XsltSupport.getXsltLanguageLevel(file).getXPathVersion() == getContextType().getVersion();
  }

  private static void fillFromSchema(PsiFile file, ElementNames names) {
    if (!(file instanceof XmlFile)) return;
    final XmlFile f = (XmlFile)file;
    final XmlDocument d = f.getDocument();
    if (d == null) return;
    final XmlTag rootTag = d.getRootTag();
    if (rootTag == null) return;

    //noinspection unchecked
    names.dependencies.add(new NSDeclTracker(rootTag));

    try {
      final Map<String, String> namespaceDeclarations = rootTag.getLocalNamespaceDeclarations();
      final Collection<String> prefixes = namespaceDeclarations.keySet();

      final XmlElementFactory ef = XmlElementFactory.getInstance(file.getProject());
      int noSchemaNamespaces = 0;
      for (String prefix : prefixes) {
        final String namespace = namespaceDeclarations.get(prefix);
        if (isIgnoredNamespace(prefix, namespace)) continue;

        final XmlTag tag = ef.createTagFromText("<dummy-tag xmlns='" + namespace + "' />", XMLLanguage.INSTANCE);
        final XmlDocument document = PsiTreeUtil.getParentOfType(tag, XmlDocument.class);
        final XmlNSDescriptor rootDescriptor = tag.getNSDescriptor(tag.getNamespace(), true);
        if (rootDescriptor == null ||
                (rootDescriptor instanceof XmlNSDescriptorImpl && ((XmlNSDescriptorImpl)rootDescriptor).getTag() == null) ||
                !rootDescriptor.getDeclaration().isPhysical()) {
          final QName any = QNameUtil.createAnyLocalName(namespace);
          names.elementNames.add(any);
          names.attributeNames.add(any);
          noSchemaNamespaces++;
          continue;
        }

        //noinspection unchecked
        names.dependencies.add(rootDescriptor.getDescriptorFile());

        //noinspection unchecked
        final Set<XmlElementDescriptor> history = new THashSet<>(150);

        final XmlElementDescriptor[] e = rootDescriptor.getRootElementsDescriptors(document);
        try {
          for (XmlElementDescriptor descriptor : e) {
            processElementDescriptors(descriptor, tag, names, history, 0);
          }
        } catch (StopProcessingException e1) {
          Logger.getInstance(XsltContextProviderBase.class).error("Maximum recursion depth reached. Missing equals()/hashCode() implementation?", StringUtil
            .join(history, descriptor -> descriptor.getClass().getName() + "[" + descriptor.getQualifiedName() + "]", ", "));
        }
      }
      names.validateNames = names.elementNames.size() > noSchemaNamespaces;

//            final QName any = QNameUtil.createAnyLocalName("");
//            names.elementNames.add(any);
//            names.attributeNames.add(any);
    } catch (IncorrectOperationException e) {
      Logger.getInstance(XsltContextProvider.class.getName()).error(e);
    }
  }

  private static boolean isIgnoredNamespace(String prefix, String namespace) {
    return IGNORED_URIS.contains(namespace) || prefix.length() == 0 || "xmlns".equals(prefix);
  }

  private static class StopProcessingException extends Exception {
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  private static void processElementDescriptors(XmlElementDescriptor descriptor, XmlTag tag, ElementNames names, Set<XmlElementDescriptor> history, int depth)
    throws StopProcessingException {
    if (!history.add(descriptor) || ++depth == 200) {
      if (depth == 200) {
        throw new StopProcessingException();
      }
      return;
    }
    final String namespace = descriptor instanceof XmlElementDescriptorImpl
            ? ((XmlElementDescriptorImpl)descriptor).getNamespace()
            : tag.getNamespace();
    names.elementNames.add(new QName(namespace, descriptor.getName()));

    final XmlAttributeDescriptor[] attributesDescriptors = descriptor.getAttributesDescriptors(null);
    for (XmlAttributeDescriptor attributesDescriptor : attributesDescriptors) {
      final String localPart = attributesDescriptor.getName();
      if (!"xmlns".equals(localPart)) names.attributeNames.add(new QName(localPart));
    }

    final XmlElementDescriptor[] descriptors = descriptor.getElementsDescriptors(tag);
    for (XmlElementDescriptor elem : descriptors) {
      processElementDescriptors(elem, tag, names, history, depth);
    }
  }

  public PsiFile[] getRelatedFiles(final XPathFile file) {

    final XmlAttribute attribute = PsiTreeUtil.getContextOfType(file, XmlAttribute.class, false);
    assert attribute != null;

    final PsiFile psiFile = attribute.getContainingFile();
    assert psiFile != null;

    final List<PsiFile> files = new ArrayList<>();

    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        final PsiFile[] _files = XsltSupport.getFiles(attribute);
        for (PsiFile _file : _files) {
          if (_file != file) files.add(_file);
        }
      }
    });

    return PsiUtilCore.toPsiFileArray(files);
  }

  @Nullable
  public XmlElement getContextElement() {
    return myContextElement.getElement();
  }

  @NotNull
  public XPathType getExpectedType(XPathExpression expr) {
    final XmlTag tag = PsiTreeUtil.getContextOfType(expr, XmlTag.class, true);
    if (tag != null && XsltSupport.isXsltTag(tag)) {
      final XsltElement element = XsltElementFactory.getInstance().wrapElement(tag, XsltElement.class);
      if (element instanceof XsltVariable) {
        return ((XsltVariable)element).getType();
      } else {
        final XmlAttribute attr = PsiTreeUtil.getContextOfType(expr, XmlAttribute.class, true);
        if (attr != null) {
          if (element instanceof XsltWithParam) {
            final XmlAttribute nameAttr = tag.getAttribute("name", null);
            if (nameAttr != null) {
              final XmlAttributeValue valueElement = nameAttr.getValueElement();
              if (valueElement != null) {
                final PsiReference[] references = valueElement.getReferences();
                for (PsiReference reference : references) {
                  final PsiElement psiElement = reference.resolve();
                  if (psiElement instanceof XsltVariable) {
                    return ((XsltVariable)psiElement).getType();
                  }
                }
              }
            }
          } else {
            final String name = attr.getName();
            return getTypeForTag(tag, name);
          }
        }
      }
    }
    return XPathType.UNKNOWN;
  }

  protected XPathType getTypeForTag(XmlTag tag, String attribute) {
    String tagName = tag.getLocalName();
    if ("select".equals(attribute)) {
      if ("copy-of".equals(tagName) || "for-each".equals(tagName) || "apply-templates".equals(tagName)) {
        return XPathType.NODESET;
      } else if ("value-of".equals(tagName) || "sort".equals(tagName)) {
        return XPathType.STRING;
      }
      return XPathType.ANY;
    } else if ("test".equals(attribute)) {
      if ("if".equals(tagName) || "when".equals(tagName)) {
        return XPathType.BOOLEAN;
      }
    } else if ("number".equals(attribute)) {
      if ("value".equals(tagName)) {
        return XPathType.NUMBER;
      }
    }
    return XPathType.UNKNOWN;
  }

  @NotNull
  public NamespaceContext getNamespaceContext() {
    return XsltNamespaceContext.NAMESPACE_CONTEXT;
  }

  @NotNull
  public VariableContext getVariableContext() {
    return XsltVariableContext.INSTANCE;
  }

  @Nullable
  public Set<QName> getAttributes(boolean forValidation) {
    final ElementNames names = getNames(getFile());
    if (names != null) {
      return !forValidation || names.validateNames ? names.attributeNames : null;
    }
    return null;
  }

  @Nullable
  public Set<QName> getElements(boolean forValidation) {
    final ElementNames names = getNames(getFile());
    if (names != null) {
      return !forValidation || names.validateNames ? names.elementNames : null;
    }
    return null;
  }

  @Nullable
  private ElementNames getNames(@Nullable PsiFile file) {
    if (file == null) return null;

    return myNamesCache.get(this).getValue();
  }

  private CachedValue<ElementNames> createCachedValue(final PsiFile file) {
    return CachedValuesManager.getManager(file.getProject()).createCachedValue(new CachedValueProvider<ElementNames>() {
      public Result<ElementNames> compute() {
        final ElementNames names = new ElementNames();
        final PsiFile[] associations = myFileAssociationsManager.getAssociationsFor(file, FileAssociationsManager.Holder.XML_FILES);

        if (associations.length == 0) {
          fillFromSchema(file, names);
        } else {
          names.validateNames = true;
          //noinspection unchecked
          ContainerUtil.addAll(names.dependencies, associations);
        }
        //noinspection unchecked
        names.dependencies.add(myFileAssociationsManager);

        for (PsiFile file : associations) {
          if (!(file instanceof XmlFile)) continue;
          file.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              names.elementNames.add(QNameUtil.createQName(tag));
              super.visitXmlTag(tag);
            }

            @Override
            public void visitXmlAttribute(XmlAttribute attribute) {
              if (!attribute.isNamespaceDeclaration()) {
                names.attributeNames.add(QNameUtil.createQName(attribute));
              }
              super.visitXmlAttribute(attribute);
            }
          });
        }

        //noinspection unchecked
        return new Result<>(names, ArrayUtil.toObjectArray(names.dependencies));
      }
    }, false);
  }

  @Nullable
  private PsiFile getFile() {
    final XmlElement element = getContextElement();
    if (element == null) {
      return null;
    }
    return element.getContainingFile().getOriginalFile();
  }

  @NotNull
  public XPathQuickFixFactory getQuickFixFactory() {
    return XsltQuickFixFactory.INSTANCE;
  }

  static class ElementNames {
    boolean validateNames;

    final Set<QName> elementNames = new HashSet<>();
    final Set<QName> attributeNames = new HashSet<>();

    @SuppressWarnings({"RawUseOfParameterizedType"})
    final Set dependencies = new HashSet();
  }
}