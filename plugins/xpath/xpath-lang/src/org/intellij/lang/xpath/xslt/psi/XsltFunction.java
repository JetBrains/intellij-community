/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt.psi;

import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.psi.XPathFunction;

import javax.xml.namespace.QName;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 11.01.11
*/
public interface XsltFunction extends XsltElement, XPathFunction, Function {

  QName getQName();
}