/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.intellij.html;

import com.intellij.html.impl.DelegatingRelaxedHtmlElementDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.model.descriptors.CompositeDescriptor;
import org.intellij.plugins.relaxNG.model.descriptors.RngElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.rngom.digested.DElementPattern;

import javax.xml.namespace.QName;

/**
 * @author Eugene.Kudelevsky
 */
public class RelaxedHtmlFromRngElementDescriptor extends DelegatingRelaxedHtmlElementDescriptor implements Comparable {
  private final boolean isHtml;

  public RelaxedHtmlFromRngElementDescriptor(XmlElementDescriptor delegate) {
    super(delegate);
    isHtml = isHtml(delegate);
  }

  @Override
  public int compareTo(@NotNull Object o) {
    if (!(o instanceof RelaxedHtmlFromRngElementDescriptor)) return 1;
    final RelaxedHtmlFromRngElementDescriptor other = (RelaxedHtmlFromRngElementDescriptor)o;
    if (other.isHtml && !isHtml) return -1;
    if (!other.isHtml && isHtml) return 1;
    return 0;
  }

  private static boolean isHtml(XmlElementDescriptor o) {
    if (o instanceof CompositeDescriptor) {
      for (DElementPattern pattern : ((CompositeDescriptor)o).getElementPatterns()) {
        if (isHtml(pattern)) return true;
      }
    } else if (o instanceof RngElementDescriptor) {
      return isHtml(((RngElementDescriptor)o).getElementPattern());
    }
    return false;
  }

  private static boolean isHtml(DElementPattern pattern) {
    for (QName name : pattern.getName().listNames()) {
      if (XmlUtil.XHTML_URI.equals(name.getNamespaceURI())) {
        return true;
      }
    }
    return false;
  }
}
