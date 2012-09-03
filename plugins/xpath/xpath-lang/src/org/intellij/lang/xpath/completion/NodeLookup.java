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
package org.intellij.lang.xpath.completion;

import com.intellij.openapi.util.Iconable;
import com.intellij.util.PlatformIcons;
import icons.XpathIcons;
import org.intellij.lang.xpath.psi.XPathNodeTest;

import javax.swing.*;

public class NodeLookup extends AbstractLookup implements Lookup, Iconable {
    private final XPathNodeTest.PrincipalType principalType;

    public NodeLookup(String name, XPathNodeTest.PrincipalType principalType) {
        super(name, name);
        this.principalType = principalType;
    }

    public Icon getIcon(int flags) {
        return principalType == XPathNodeTest.PrincipalType.ATTRIBUTE ? PlatformIcons.ANNOTATION_TYPE_ICON : XpathIcons.Tag;
    }
}
