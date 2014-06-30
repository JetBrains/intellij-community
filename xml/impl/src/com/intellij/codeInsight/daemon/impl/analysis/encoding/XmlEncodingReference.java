/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class XmlEncodingReference extends EncodingReference implements EmptyResolveMessageProvider, Comparable<XmlEncodingReference> {
  private final int myPriority;

  public XmlEncodingReference(XmlAttributeValue value, final String charsetName, final TextRange rangeInElement, int priority) {
    super(value, charsetName, rangeInElement);
    myPriority = priority;
  }

  @Override
  @NotNull
  public String getUnresolvedMessagePattern() {
    //noinspection UnresolvedPropertyKey
    return XmlErrorMessages.message("unknown.encoding.0");
  }

  @Override
  public int compareTo(@NotNull XmlEncodingReference ref) {
    return myPriority - ref.myPriority;
  }
}
