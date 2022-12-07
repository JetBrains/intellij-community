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

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.DElementPattern;
import org.kohsuke.rngom.digested.DPattern;

import javax.xml.namespace.QName;
import java.util.*;

public class CompositeDescriptor extends RngElementDescriptor {
  private final DElementPattern[] myPatterns;

  CompositeDescriptor(RngNsDescriptor nsDescriptor, DElementPattern pattern, List<DElementPattern> patterns) {
    super(nsDescriptor, pattern);
    myPatterns = ContainerUtil.reverse(patterns).toArray(new DElementPattern[patterns.size()]);
  }

  @Override
  protected XmlElementDescriptor findElementDescriptor(XmlTag childTag) {
    final List<DElementPattern> patterns = new ArrayList<>();
    for (DElementPattern pattern : myPatterns) {
      patterns.addAll(ChildElementFinder.find(2, pattern));
    }
    // TODO: filter out impossible variants:
    /*
      while this needs both variants of <choice>-children
      <element><choice><caret>

      this does not, because <choice> inside <choice> is unambiguous:
      <element><choice><data type="string" /><choice><caret>
     */
    final XmlElementDescriptor d = myNsDescriptor.findDescriptor(childTag, patterns);
    if (d != null) {
      return d;
    }
    return NULL;
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    final Set<XmlElementDescriptor> descriptors = new LinkedHashSet<>(Arrays.asList(super.getElementsDescriptors(context)));
    for (DElementPattern pattern : myPatterns) {
      final List<DElementPattern> list = ChildElementFinder.find(2, pattern);
      descriptors.addAll(Arrays.asList(myNsDescriptor.convertElementDescriptors(list)));
    }
    return descriptors.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  @Override
  protected XmlAttributeDescriptor getAttributeDescriptor(String namespace, String localName) {
    final QName qname = new QName(namespace, localName);

    return computeAttributeDescriptor(AttributeFinder.find(qname, myPatterns));
  }

  @Override
  protected XmlAttributeDescriptor[] collectAttributeDescriptors(@Nullable XmlTag context) {
    return computeAttributeDescriptors(AttributeFinder.find(myPatterns));
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final CompositeDescriptor that = (CompositeDescriptor)o;

    if (!Arrays.equals(myPatterns, that.myPatterns)) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Arrays.hashCode(myPatterns);
    return result;
  }

  public DElementPattern[] getElementPatterns() {
    return myPatterns;
  }
}
