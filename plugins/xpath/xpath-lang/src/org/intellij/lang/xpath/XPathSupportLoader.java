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
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.psi.filters.TrueFilter;
import org.intellij.lang.xpath.completion.XPathCompletionData;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.validation.inspections.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XPathSupportLoader extends FileTypeFactory implements ApplicationComponent, InspectionToolProvider {
    private static final boolean DBG_MODE = Boolean.getBoolean("xpath-lang.register-file-type");

    @NotNull
    @NonNls
    public String getComponentName() {
        return "XPath Support Loader";
    }
    
    public void initComponent() {
        CompletionUtil.registerCompletionData(XPathFileType.XPATH, new XPathCompletionData(TrueFilter.INSTANCE, XPathElement.class));
        TypedHandler.registerQuoteHandler(XPathFileType.XPATH, new SimpleTokenSetQuoteHandler(XPathTokenTypes.STRING_LITERAL));
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

    public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
        if (DBG_MODE) consumer.consume(XPathFileType.XPATH, XPathFileType.XPATH.getDefaultExtension());
    }
}
