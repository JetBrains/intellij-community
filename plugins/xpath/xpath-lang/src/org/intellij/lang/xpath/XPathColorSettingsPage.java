// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.lang.xpath;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class XPathColorSettingsPage implements ColorSettingsPage {
    @Override
    public @NotNull String getDisplayName() {
        return XPathFileType.XPATH.getName();
    }

    @Override
    public @Nullable Icon getIcon() {
        return XPathFileType.XPATH.getIcon();
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return new AttributesDescriptor[]{
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.keyword"), XPathHighlighter.XPATH_KEYWORD),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.name"), XPathHighlighter.XPATH_NAME),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.number"), XPathHighlighter.XPATH_NUMBER),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.string"), XPathHighlighter.XPATH_STRING),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.operator"), XPathHighlighter.XPATH_OPERATION_SIGN),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.parentheses"), XPathHighlighter.XPATH_PARENTH),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.brackets"), XPathHighlighter.XPATH_BRACKET),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.function"), XPathHighlighter.XPATH_FUNCTION),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.variable"), XPathHighlighter.XPATH_VARIABLE),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.extension.prefix"), XPathHighlighter.XPATH_PREFIX),
                new AttributesDescriptor(XPathBundle.message("attribute.descriptor.other"), XPathHighlighter.XPATH_TEXT),
        };
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(XPathFileType.XPATH.getLanguage(), null, null);
    }

    @Override
    public @NonNls @NotNull String getDemoText() {
        return "//prefix:*[ext:name() = 'changes']/element[(position() mod 2) = $pos + 1]/parent::*";
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }
}