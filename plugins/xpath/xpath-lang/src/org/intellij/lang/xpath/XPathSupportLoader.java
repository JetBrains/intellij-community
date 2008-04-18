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

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.filters.TrueFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.completion.XPathCompletionData;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.validation.inspections.CheckNodeTest;
import org.intellij.lang.xpath.validation.inspections.HardwiredNamespacePrefix;
import org.intellij.lang.xpath.validation.inspections.ImplicitTypeConversion;
import org.intellij.lang.xpath.validation.inspections.IndexZeroPredicate;
import org.intellij.lang.xpath.validation.inspections.RedundantTypeConversion;
import org.intellij.lang.xpath.validation.inspections.XPathInspection;

public class XPathSupportLoader implements ApplicationComponent, InspectionToolProvider {
    private static final boolean DBG_MODE = Boolean.getBoolean("xpath-lang.register-file-type");

    @NotNull
    @NonNls
    public String getComponentName() {
        return "XPath Support Loader";
    }
    
    public void initComponent() {
        if (DBG_MODE) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                    final String[] extensions = new String[]{ XPathFileType.XPATH.getDefaultExtension() };
                    FileTypeManager.getInstance().registerFileType(XPathFileType.XPATH, extensions);
                }
            });
        }

        CompletionUtil.registerCompletionData(XPathFileType.XPATH, new XPathCompletionData(TrueFilter.INSTANCE, XPathElement.class));
        TypedHandler.registerQuoteHandler(XPathFileType.XPATH, new SimpleTokenSetQuoteHandler(XPathTokenTypes.STRING_LITERAL));
//        ColorSettingsPages.getInstance().registerPage(new XPathColorSettingsPage());
    }

    public void disposeComponent() {
    }

    public Class<? extends XPathInspection>[] getInspectionClasses() {
        //noinspection unchecked
        return new Class[]{
                CheckNodeTest.class,
                ImplicitTypeConversion.class,
                RedundantTypeConversion.class,
                IndexZeroPredicate.class,
                HardwiredNamespacePrefix.class,
        };
    }
}
