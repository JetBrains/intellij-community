/*
 * Copyright 2005 Sascha Weinreuter
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

import com.intellij.psi.xml.XmlElement;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.context.functions.FunctionContext;
import org.jetbrains.annotations.NotNull;

public class XsltContextProvider extends XsltContextProviderBase {
  public static final ContextType TYPE = ContextType.lookupOrCreate("XSLT", XPathVersion.V1);

  protected XsltContextProvider(@NotNull XmlElement contextElement) {
    super(contextElement);
  }

  @NotNull
  @Override
  public ContextType getContextType() {
    return TYPE;
  }

  @Override
  @NotNull
  public FunctionContext createFunctionContext() {
    return XsltFunctionContext.getInstance();
  }
}
