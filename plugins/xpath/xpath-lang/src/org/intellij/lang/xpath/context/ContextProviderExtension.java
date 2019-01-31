// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.context;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.intellij.lang.xpath.XPathFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ContextProviderExtension {
    public static final ExtensionPointName<ContextProviderExtension> EXTENSION_POINT_NAME =
            ExtensionPointName.create("XPathView.xpath.contextProviderExtension");

    protected abstract boolean accepts(XPathFile file);

    @NotNull
    protected abstract ContextProvider getContextProvider(XPathFile file);

    @Nullable
    public static ContextProvider getInstance(XPathFile file) {
      for (ContextProviderExtension extension : EXTENSION_POINT_NAME.getExtensionList()) {
            if (extension.accepts(file)) {
                return extension.getContextProvider(file);
            }
        }
        return null;
    }
}