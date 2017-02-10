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

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptorEx;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.intellij.plugins.relaxNG.ApplicationLoader;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;
import org.intellij.plugins.relaxNG.validation.RngParser;
import org.intellij.plugins.relaxNG.validation.XmlInstanceValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.DElementPattern;
import org.kohsuke.rngom.digested.DPattern;
import org.kohsuke.rngom.nc.NameClass;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.07.2007
 */
public class RngNsDescriptor implements XmlNSDescriptorEx, Validator {
  private final Map<QName, CachedValue<XmlElementDescriptor>> myDescriptorsMap =
    Collections.synchronizedMap(new HashMap<QName, CachedValue<XmlElementDescriptor>>());

  private static final Key<ParameterizedCachedValue<XmlElementDescriptor, RngNsDescriptor>> ROOT_KEY = Key.create("ROOT_DESCRIPTOR");

  private XmlFile myFile;
  private PsiElement myElement;
  private String myUrl;

  private DPattern myPattern;
  private PsiManager myManager;

  @Override
  @Nullable
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    if (myPattern == null) {
      return null;
    }

    XmlTag _tag = tag;
    final LinkedList<XmlTag> chain = new LinkedList<>();
    while (_tag != null) {
      chain.addFirst(_tag);
      _tag = _tag.getParentTag();
    }

    XmlElementDescriptor desc;
    do {
      desc = findRootDescriptor(chain.removeFirst());
    } while (desc == null && chain.size() > 0);

    if (desc != null) {
      for (XmlTag xmlTag : chain) {
        desc = desc.getElementDescriptor(xmlTag, xmlTag.getParentTag());
        if (desc == null) {
          break;
        }
      }
    }

    if (desc == null || desc instanceof AnyXmlElementDescriptor) {
      return findRootDescriptor(tag);
    }

    return desc;
  }

  private XmlElementDescriptor findRootDescriptor(final XmlTag tag) {
    return CachedValuesManager.getManager(tag.getProject())
        .getParameterizedCachedValue(tag, ROOT_KEY, new ParameterizedCachedValueProvider<XmlElementDescriptor, RngNsDescriptor>() {
          @Override
          public CachedValueProvider.Result<XmlElementDescriptor> compute(RngNsDescriptor o) {
            final XmlElementDescriptor descr = o.findRootDescriptorInner(tag);
            if (descr != null) {
              return CachedValueProvider.Result.create(descr, tag, descr.getDependences(), o.getDependences());
            }
            else {
              return CachedValueProvider.Result.create(null, tag, o.getDependences());
            }
          }
        }, false, this);
  }

  private XmlElementDescriptor findRootDescriptorInner(XmlTag tag) {
    final List<DElementPattern> allNamedPatterns =
      ContainerUtil.findAll(ChildElementFinder.find(-1, myPattern), NamedPatternFilter.INSTANCE);
    XmlElementDescriptor descriptor = findDescriptor(tag, allNamedPatterns);
    return descriptor != null ? descriptor : findDescriptor(tag, ChildElementFinder.find(myPattern));
  }

  private XmlElementDescriptor findRootDescriptorInner(QName qName) {
    return findDescriptor(qName, ContainerUtil.findAll(
      ChildElementFinder.find(-1, myPattern), NamedPatternFilter.INSTANCE));
  }

  public XmlElementDescriptor findDescriptor(XmlTag tag, List<DElementPattern> list) {
    final QName qName = new QName(tag.getNamespace(), tag.getLocalName());

    return findDescriptor(qName, list);
  }

  private XmlElementDescriptor findDescriptor(final QName qName, List<DElementPattern> list) {
    int max = -1;
    DElementPattern maxPattern = null;
    for (DElementPattern pattern : list) {
      final NameClass nameClass = pattern.getName();
      if (nameClass.contains(qName)) {
        final int spec = nameClass.containsSpecificity(qName);
        if (spec > max) {
          maxPattern = pattern;
          max = spec;
        }
      }
    }
    final List<DElementPattern> patterns = ContainerUtil.findAll(list, pattern -> {
      final NameClass nameClass = pattern.getName();
      return nameClass.contains(qName);
    });

    if (maxPattern != null) {
      if (patterns.size() > 1) {
        return initDescriptor(new CompositeDescriptor(this, maxPattern, patterns));
      } else {
        return initDescriptor(new RngElementDescriptor(this, maxPattern));
      }
    } else {
      return null;
    }
  }

  @Override
  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable XmlDocument document) {
    if (myPattern == null) {
      return XmlElementDescriptor.EMPTY_ARRAY;
    }

    final List<DElementPattern> list = ChildElementFinder.find(-1, myPattern);
    return convertElementDescriptors(list);
  }

  XmlElementDescriptor[] convertElementDescriptors(List<DElementPattern> patterns) {
    patterns = ContainerUtil.findAll(patterns, NamedPatternFilter.INSTANCE);

    final Map<QName, List<DElementPattern>> name2patterns = new HashMap<>();
    for (DElementPattern pattern : patterns) {
      for (QName qName : pattern.getName().listNames()) {
        List<DElementPattern> dPatterns = name2patterns.get(qName);
        if (dPatterns == null) {
          dPatterns = new ArrayList<>();
          name2patterns.put(qName, dPatterns);
        }
        if (!dPatterns.contains(pattern)) dPatterns.add(pattern);
      }
    }

    final List<XmlElementDescriptor> result = new ArrayList<>();

    for (QName qName : name2patterns.keySet()) {
      final List<DElementPattern> patternList = name2patterns.get(qName);
      final XmlElementDescriptor descriptor = findDescriptor(qName, patternList);
      if (descriptor != null) {
        result.add(descriptor);
      }
    }

    return result.toArray(new XmlElementDescriptor[result.size()]);
  }

  protected XmlElementDescriptor initDescriptor(@NotNull XmlElementDescriptor descriptor) {
    return descriptor;
  }

  @Override
  @NotNull
  public XmlFile getDescriptorFile() {
    return myFile;
  }

  @Override
  public synchronized PsiElement getDeclaration() {
    if (!myElement.isValid() || !myFile.isValid()) {
      if (myUrl != null) {
        final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(myUrl);
        if (fileByUrl != null) {
          final PsiFile file = myManager.findFile(fileByUrl);
          if (file instanceof XmlFile) {
            init(((XmlFile)file).getDocument());
          }
        }
      }
    }
    return myFile.isValid() ? myFile.getDocument() : null;
  }

  @Override
  @NonNls
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  @NonNls
  public String getName() {
    return getDescriptorFile().getName();
  }

  @Override
  public Object[] getDependences() {
    if (myPattern != null) {
      if (DumbService.isDumb(myElement.getProject())) {
        return new Object[] { ModificationTracker.EVER_CHANGED, ExternalResourceManager.getInstance()};
      }
      final Object[] a = { myElement, ExternalResourceManager.getInstance() };
      final PsiElementProcessor.CollectElements<XmlFile> processor = new PsiElementProcessor.CollectElements<>();
      RelaxIncludeIndex.processForwardDependencies(myFile, processor);
      if (processor.getCollection().size() > 0) {
        return ArrayUtil.mergeArrays(a, processor.toArray());
      } else {
        return a;
      }
    }
    return new Object[]{ ModificationTracker.EVER_CHANGED };
  }

  @Override
  public synchronized void init(PsiElement element) {
    myElement = element;
    myFile = element instanceof XmlFile ? (XmlFile)element : (XmlFile)element.getContainingFile();
    myManager = myFile.getManager();

    final VirtualFile file = myFile.getVirtualFile();
    if (file != null) {
      myUrl = file.getUrl();
    }

    myPattern = RngParser.getCachedPattern(getDescriptorFile(), RngParser.DEFAULT_HANDLER);
  }

  @Override
  public void validate(@NotNull PsiElement context, @NotNull final ValidationHost host) {
    final XmlDocument doc = PsiTreeUtil.getContextOfType(context, XmlDocument.class, false);
    if (doc == null) {
      return;
    }
    final XmlTag rootTag = doc.getRootTag();
    if (rootTag == null) {
      return;
    }
    // RNG XML itself is validated by parsing it with Jing, so we don't want to schema-validate it
    if (!ApplicationLoader.RNG_NAMESPACE.equals(rootTag.getNamespace())) {
      XmlInstanceValidator.doValidation(doc, host, getDescriptorFile());
    }
  }

  //@Override
  @Override
  public XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    final QName qName = new QName(namespace, localName);
    CachedValue<XmlElementDescriptor> cachedValue = myDescriptorsMap.get(qName);
    if (cachedValue == null) {
      cachedValue =
        CachedValuesManager.getManager(myElement.getProject()).createCachedValue(() -> {
          final XmlElementDescriptor descriptor = findRootDescriptorInner(qName);
          return descriptor != null
                 ? new CachedValueProvider.Result<>(descriptor, descriptor.getDependences())
                 : new CachedValueProvider.Result<>(null, getDependences());
        }, false);
      myDescriptorsMap.put(qName, cachedValue);
    }
    return cachedValue.getValue();
  }
}
