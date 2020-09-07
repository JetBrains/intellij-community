/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.ui;

import com.intellij.openapi.util.NlsContexts;
import org.intellij.plugins.xpathView.XPathBundle;

public enum Mode {
    SIMPLE, ADVANCED;

    public @NlsContexts.Button String getName() {
        return this == SIMPLE ? XPathBundle.message("button.simple") : XPathBundle.message("button.advanced");
    }

    public Mode other() {
        return this == ADVANCED ? SIMPLE : ADVANCED;
    }
}
