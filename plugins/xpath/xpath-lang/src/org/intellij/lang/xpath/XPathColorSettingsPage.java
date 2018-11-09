// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.lang.xpath;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class XPathColorSettingsPage implements ColorSettingsPage {
    @Override
    @NotNull
    public String getDisplayName() {
        return "XPath";
    }

    @Override
    @Nullable
    public Icon getIcon() {
        return XPathFileType.XPATH.getIcon();
    }

    @Override
    @NotNull
    public AttributesDescriptor[] getAttributeDescriptors() {
        return new AttributesDescriptor[]{
                new AttributesDescriptor("Keyword", XPathHighlighter.XPATH_KEYWORD),
                new AttributesDescriptor("Name", XPathHighlighter.XPATH_NAME),
                new AttributesDescriptor("Number", XPathHighlighter.XPATH_NUMBER),
                new AttributesDescriptor("String", XPathHighlighter.XPATH_STRING),
                new AttributesDescriptor("Operator", XPathHighlighter.XPATH_OPERATION_SIGN),
                new AttributesDescriptor("Parentheses", XPathHighlighter.XPATH_PARENTH),
                new AttributesDescriptor("Brackets", XPathHighlighter.XPATH_BRACKET),
                new AttributesDescriptor("Function", XPathHighlighter.XPATH_FUNCTION),
                new AttributesDescriptor("Variable", XPathHighlighter.XPATH_VARIABLE),
                new AttributesDescriptor("Extension Prefix", XPathHighlighter.XPATH_PREFIX),
                new AttributesDescriptor("Other", XPathHighlighter.XPATH_TEXT),
        };
    }

    @Override
    @NotNull
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    @NotNull
    public SyntaxHighlighter getHighlighter() {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(XPathFileType.XPATH.getLanguage(), null, null);
    }

    @Override
    @NonNls
    @NotNull
    public String getDemoText() {
        return "//prefix:*[ext:name() = 'changes']/element[(position() mod 2) = $pos + 1]/parent::*";
    }

    @Override
    @Nullable
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }
}