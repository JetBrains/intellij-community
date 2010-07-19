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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.intellij.plugins.relaxNG.ProjectLoader;
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
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.07.2007
 */
public class RngNsDescriptor implements XmlNSDescriptor, Validator {
  private static final Key<ParameterizedCachedValue<XmlElementDescriptor, RngNsDescriptor>> ROOT_KEY = Key.create("ROOT_DESCRIPTOR");

  private XmlFile myFile;
  private PsiElement myElement;
  private String myUrl;

  private DPattern myPattern;
  private PsiManager myManager;

  @Nullable
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    if (myPattern == null) {
      return null;
    }

    XmlTag _tag = tag;
    final LinkedList<XmlTag> chain = new LinkedList<XmlTag>();
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

    if (desc == null) {
      return findRootDescriptor(tag);
    }

    return desc;
  }

  private XmlElementDescriptor findRootDescriptor(final XmlTag tag) {
    return tag.getManager().getCachedValuesManager().getParameterizedCachedValue(tag, ROOT_KEY, new ParameterizedCachedValueProvider<XmlElementDescriptor, RngNsDescriptor>() {
      public CachedValueProvider.Result<XmlElementDescriptor> compute(RngNsDescriptor o) {
        final XmlElementDescriptor descr = o.findRootDescriptorInner(tag);
        if (descr != null) {
          return CachedValueProvider.Result.create(descr, tag, descr.getDependences(), o.getDependences());
        } else {
          return CachedValueProvider.Result.create(null, tag, o.getDependences());
        }
      }
    }, false, this);
  }

  private XmlElementDescriptor findRootDescriptorInner(XmlTag tag) {
    return findDescriptor(tag, ChildElementFinder.find(myPattern));
  }

  public XmlElementDescriptor findDescriptor(XmlTag tag, List<DElementPattern> list) {
    final QName qName = new QName(tag.getNamespace(), tag.getLocalName());

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
    final List<DElementPattern> patterns = ContainerUtil.findAll(list, new Condition<DElementPattern>() {
      public boolean value(DElementPattern pattern) {
        final NameClass nameClass = pattern.getName();
        return nameClass.contains(qName);
      }
    });

    if (maxPattern != null) {
      if (patterns.size() > 1) {
        return new CompositeDescriptor(this, maxPattern, patterns);
      } else {
        return new RngElementDescriptor(this, maxPattern);
      }
    } else {
      return null;
    }
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable XmlDocument document) {
    if (myPattern == null) {
      return XmlElementDescriptor.EMPTY_ARRAY;
    }

    final List<DElementPattern> list = ContainerUtil.findAll(ChildElementFinder.find(myPattern), NamedPatternFilter.INSTANCE);
    return convertElementDescriptors(list, this);
  }

  static XmlElementDescriptor[] convertElementDescriptors(List<DElementPattern> finder, final RngNsDescriptor nsDescriptor) {
    return ContainerUtil.map2Array(finder, XmlElementDescriptor.class, new Function<DElementPattern, XmlElementDescriptor>() {
      public XmlElementDescriptor fun(final DElementPattern elementPattern) {
        return new RngElementDescriptor(nsDescriptor, elementPattern);
      }
    });
  }

  @NotNull
  public XmlFile getDescriptorFile() {
    return myFile;
  }

  public boolean isHierarhyEnabled() {
    return false;
  }

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

  @NonNls
  public String getName(PsiElement context) {
    return getName();
  }

  @NonNls
  public String getName() {
    return getDescriptorFile().getName();
  }

  public Object[] getDependences() {
    if (myPattern != null) {
      final Object[] a = { myElement, ExternalResourceManager.getInstance() };
      final PsiElementProcessor.CollectElements<XmlFile> processor = new PsiElementProcessor.CollectElements<XmlFile>();
      RelaxIncludeIndex.processForwardDependencies(myFile, processor);
      if (processor.getCollection().size() > 0) {
        return ArrayUtil.mergeArrays(a, processor.toArray(), Object.class);
      } else {
        return a;
      }
    } else {
      return new Object[]{ ModificationTracker.EVER_CHANGED };
    }
  }

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
    if (!ProjectLoader.RNG_NAMESPACE.equals(rootTag.getNamespace())) {
      XmlInstanceValidator.doValidation(doc, host, getDescriptorFile());
    }
  }
}
