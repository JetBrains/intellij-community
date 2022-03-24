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
package org.intellij.lang.xpath;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import icons.XpathIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Locale;

public final class XPathFileType extends LanguageFileType {
    public static final XPathFileType XPATH = new XPathFileType(new XPathLanguage());
    public static final XPathFileType XPATH2 = new XPathFileType(new XPath2Language());

    @SuppressWarnings("NonDefaultConstructor")
    private XPathFileType(Language language) {
        super(language);
    }

    @Override
    public @NotNull @NlsSafe String getName() {
        return getLanguage().getID();
    }

    @Override
    public @NotNull String getDescription() {
        return getName();
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return getLanguage().getID().toLowerCase(Locale.ENGLISH);
    }

    @Override
    public Icon getIcon() {
        return XpathIcons.Xpath;
    }
}
