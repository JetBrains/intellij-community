/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.descriptors;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.validation.RngSchemaValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.*;
import org.kohsuke.rngom.nc.NameClass;
import org.kohsuke.rngom.nc.NameClassVisitor;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.*;

public class RngElementDescriptor implements XmlElementDescriptor {
  private static final Key<ParameterizedCachedValue<XmlElementDescriptor, RngElementDescriptor>> DESCR_KEY = Key.create("DESCR");
  private static final Key<ParameterizedCachedValue<XmlAttributeDescriptor[], RngElementDescriptor>> ATTRS_KEY = Key.create("ATTRS");

  protected static final XmlElementDescriptor NULL = null;

  private final DElementPattern myElementPattern;
  protected final RngNsDescriptor myNsDescriptor;

  private volatile SmartPsiElementPointer<? extends PsiElement> myDeclaration;

  RngElementDescriptor(RngNsDescriptor nsDescriptor, DElementPattern pattern) {
    myNsDescriptor = nsDescriptor;
    myElementPattern = pattern;
  }

  @Override
  public String getQualifiedName() {
    final QName qName = getQName();
    return qName != null ? format(qName, "") : "#unknown";
  }

  @Override
  public String getDefaultName() {
    return getName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    if (context == null) {
      return EMPTY_ARRAY;
    }

    // TODO: Not sure why this is needed. IDEA sometimes asks us for descriptors with a context that doesn't match our
    // element pattern. At least at namespace boundaries...
    final DElementPattern pattern;
    final XmlElementDescriptor descriptor = myNsDescriptor.getElementDescriptor(context);
    if (descriptor instanceof RngElementDescriptor) {
      final DElementPattern p = ((RngElementDescriptor)descriptor).myElementPattern;
      pattern = p != null ? p : myElementPattern;
    } else {
      pattern = myElementPattern;
    }

    final List<DElementPattern> patterns = ChildElementFinder.find(2, pattern);
    return myNsDescriptor.convertElementDescriptors(patterns);
  }

  protected XmlElementDescriptor findElementDescriptor(XmlTag childTag) {
    final List<DElementPattern> patterns = ChildElementFinder.find(2, myElementPattern);
    final XmlElementDescriptor d = myNsDescriptor.findDescriptor(childTag, patterns);
    return d == null ? NULL : d;
  }

  public final XmlElementDescriptor getElementDescriptor(final XmlTag childTag) {
    return getElementDescriptor(childTag, null);
  }

  @Override
  public final XmlElementDescriptor getElementDescriptor(final XmlTag childTag, XmlTag contextTag) {
    final XmlElementDescriptor value = getCachedValue(childTag, this, DESCR_KEY, new ParameterizedCachedValueProvider<XmlElementDescriptor, RngElementDescriptor>() {
      @Override
      public CachedValueProvider.Result<XmlElementDescriptor> compute(RngElementDescriptor p) {
        final XmlElementDescriptor descriptor = p.findElementDescriptor(childTag);
        return CachedValueProvider.Result.create(descriptor, p.getDependences(), childTag);
      }
    });
    return value == NULL ? null : value;
  }

  @Override
  public final XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable final XmlTag context) {
    if (context != null) {
      return getCachedValue(context, this, ATTRS_KEY, new ParameterizedCachedValueProvider<XmlAttributeDescriptor[], RngElementDescriptor>() {
        @Override
        public CachedValueProvider.Result<XmlAttributeDescriptor[]> compute(RngElementDescriptor p) {
          final XmlAttributeDescriptor[] value = p.collectAttributeDescriptors(context);
          return CachedValueProvider.Result.create(value, p.getDependences(), context);
        }
      });
    } else {
      return collectAttributeDescriptors(null);
    }
  }

  private static <D extends PsiElement, T, P> T getCachedValue(D context, P p, Key<ParameterizedCachedValue<T, P>> key, ParameterizedCachedValueProvider<T, P> provider) {
    final CachedValuesManager mgr = CachedValuesManager.getManager(context.getProject());
    return mgr.getParameterizedCachedValue(context, key, provider, false, p);
  }

  protected XmlAttributeDescriptor[] collectAttributeDescriptors(@Nullable XmlTag context) {
    return computeAttributeDescriptors(AttributeFinder.find((QName)null, myElementPattern));
  }

  protected XmlAttributeDescriptor[] computeAttributeDescriptors(final Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> map) {
    final Map<QName, RngXmlAttributeDescriptor> name2descriptor = new HashMap<>();

    for (DAttributePattern pattern : map.keySet()) {
      final Pair<? extends Map<String, String>, Boolean> value = map.get(pattern);
      for (QName name : pattern.getName().listNames()) {
        RngXmlAttributeDescriptor descriptor = name2descriptor.get(name);
        final RngXmlAttributeDescriptor newDescriptor = new RngXmlAttributeDescriptor(this, pattern, value.first, value.second);
        if (descriptor == null) {
          descriptor = newDescriptor;
        }
        else {
          descriptor = descriptor.mergeWith(newDescriptor);
        }
        name2descriptor.put(name, descriptor);
      }
    }

    final Collection<RngXmlAttributeDescriptor> result = name2descriptor.values();
    return result.toArray(new RngXmlAttributeDescriptor[result.size()]);
  }

  @Override
  public final XmlAttributeDescriptor getAttributeDescriptor(String attributeName, @Nullable XmlTag context) {
    return getAttributeDescriptor("", attributeName);
  }

  @Override
  public final XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getNamespace(), attribute.getLocalName());
  }

  protected XmlAttributeDescriptor getAttributeDescriptor(String namespace, String localName) {
    final QName qname = new QName(namespace, localName);

    return computeAttributeDescriptor(AttributeFinder.find(qname, myElementPattern));
  }

  protected XmlAttributeDescriptor computeAttributeDescriptor(final Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> attributes) {
    if (attributes.size() > 0) {
      RngXmlAttributeDescriptor d = null;
      final Set<DAttributePattern> patterns = attributes.keySet();
      for (DAttributePattern pattern : patterns) {
        final Pair<? extends Map<String, String>, Boolean> pair = attributes.get(pattern);
        final RngXmlAttributeDescriptor a =
                new RngXmlAttributeDescriptor(this, pattern, pair.first, pair.second);
        if (d == null) {
          d = a;
        } else {
          d = d.mergeWith(a);
        }
      }
      return d;
    } else {
      return null;
    }
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myNsDescriptor;
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  // is this actually used anywhere?
  @Override
  public int getContentType() {
    final DPattern child = myElementPattern.getChild();
    if (child instanceof DEmptyPattern) {
      return CONTENT_TYPE_EMPTY;
    } else if (child instanceof DTextPattern) {
      return CONTENT_TYPE_MIXED;
    } else if (child instanceof DElementPattern) {
      return ((DElementPattern)child).getName().accept(MyNameClassVisitor.INSTANCE);
    } else {
      return CONTENT_TYPE_CHILDREN;
    }
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    final SmartPsiElementPointer<? extends PsiElement> declaration = myDeclaration;
    if (declaration != null) {
      final PsiElement element = declaration.getElement();
      if (element != null && element.isValid()) {
        return element;
      }
    }

    final PsiElement decl = myNsDescriptor.getDeclaration();
    if (decl == null/* || !decl.isValid()*/) {
      myDeclaration = null;
      return null;
    }

    final PsiElement element = getDeclarationImpl(decl, myElementPattern.getLocation());
    if (element != null && element != decl) {
      myDeclaration = SmartPointerManager.getInstance(decl.getProject()).createSmartPsiElementPointer(element);
    }
    return element;
  }

  public PsiElement getDeclaration(Locator location) {
    final PsiElement element = myNsDescriptor.getDeclaration();
    if (element == null) {
      return null;
    }
    return getDeclarationImpl(element, location);
  }

  private static PsiElement getDeclarationImpl(PsiElement decl, Locator location) {
    final VirtualFile virtualFile = RngSchemaValidator.findVirtualFile(location.getSystemId());
    if (virtualFile == null) {
      return decl;
    }

    final Project project = decl.getProject();
    final PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
    if (file == null) {
      return decl;
    }

    final int column = location.getColumnNumber();
    final int line = location.getLineNumber();

    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    assert document != null;

    if (line <= 0 || document.getLineCount() < line - 1) {
      return decl;
    }
    final int startOffset = document.getLineStartOffset(line - 1);

    final PsiElement at;
    if (column > 0) {
      if (decl.getContainingFile().getFileType() == RncFileType.getInstance()) {
        final PsiElement rncElement = file.findElementAt(startOffset + column);
        final ASTNode pattern = rncElement != null ? TreeUtil.findParent(rncElement.getNode(), RncElementTypes.PATTERN) : null;
        final ASTNode nameClass = pattern != null ? pattern.findChildByType(RncElementTypes.NAME_CLASS) : null;
        return nameClass != null ? nameClass.getPsi() : rncElement;
      }
      at = file.findElementAt(startOffset + column - 2);
    } else {
      PsiElement element = file.findElementAt(startOffset);
      at = element != null ? PsiTreeUtil.nextLeaf(element) : null;
    }

    return PsiTreeUtil.getParentOfType(at, XmlTag.class);
  }

  @Override
  @NonNls
  public String getName(PsiElement context) {
    final QName qName = getQName();
    if (qName == null) {
      return "#unknown";
    }
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
    final String prefix = xmlTag != null ? xmlTag.getPrefixByNamespace(qName.getNamespaceURI()) : null;
    return format(qName, prefix != null ? prefix : qName.getPrefix());
  }

  @Override
  @NonNls
  public String getName() {
    final QName qName = getQName();
    if (qName == null) {
      return "#unknown";
    }
    return qName.getLocalPart();
  }

  private static String format(QName qName, String p) {
    final String localPart = qName.getLocalPart();
    return p.length() > 0 ? p + ":" + localPart : localPart;
  }

  @Nullable
  private QName getQName() {
    final Iterator<QName> iterator = myElementPattern.getName().listNames().iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    return iterator.next();
  }

  @Override
  public void init(PsiElement element) {

  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RngElementDescriptor that = (RngElementDescriptor)o;

    if (!myElementPattern.equals(that.myElementPattern)) return false;

    return true;
  }

  public int hashCode() {
    return myElementPattern.hashCode();
  }

  @Override
  public Object[] getDependences() {
    if (myDeclaration != null) {
      return ArrayUtil.append(myNsDescriptor.getDependences(), myDeclaration.getElement());
    } else {
      return myNsDescriptor.getDependences();
    }
  }

  private static class MyNameClassVisitor implements NameClassVisitor<Integer> {
    public static final MyNameClassVisitor INSTANCE = new MyNameClassVisitor();

    @Override
    public Integer visitAnyName() {
      return CONTENT_TYPE_ANY;
    }

    @Override
    public Integer visitAnyNameExcept(NameClass nc) {
      return CONTENT_TYPE_ANY;
    }

    @Override
    public Integer visitChoice(NameClass nc1, NameClass nc2) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitName(QName name) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitNsName(String ns) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitNsNameExcept(String ns, NameClass nc) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitNull() {
      return CONTENT_TYPE_EMPTY;
    }
  }

  public DElementPattern getElementPattern() {
    return myElementPattern;
  }
}
