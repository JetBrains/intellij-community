// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class PropertiesHighlighter extends SyntaxHighlighterBase {

  public enum PropertiesComponent {
    PROPERTY_KEY(
      TextAttributesKey.createTextAttributesKey("PROPERTIES.KEY", DefaultLanguageHighlighterColors.KEYWORD),
      PropertiesBundle.messagePointer("options.properties.attribute.descriptor.property.key"),
      PropertiesTokenTypes.KEY_CHARACTERS
    ),
    PROPERTY_VALUE(
      TextAttributesKey.createTextAttributesKey("PROPERTIES.VALUE", DefaultLanguageHighlighterColors.STRING),
      PropertiesBundle.messagePointer("options.properties.attribute.descriptor.property.value"),
      PropertiesTokenTypes.VALUE_CHARACTERS
    ),
    PROPERTY_COMMENT(
      TextAttributesKey.createTextAttributesKey("PROPERTIES.LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT),
      PropertiesBundle.messagePointer("options.properties.attribute.descriptor.comment"),
      PropertiesTokenTypes.END_OF_LINE_COMMENT
    ),
    PROPERTY_KEY_VALUE_SEPARATOR(
      TextAttributesKey.createTextAttributesKey("PROPERTIES.KEY_VALUE_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN),
      PropertiesBundle.messagePointer("options.properties.attribute.descriptor.key.value.separator"),
      PropertiesTokenTypes.KEY_VALUE_SEPARATOR
    ),
    PROPERTIES_VALID_STRING_ESCAPE(
      TextAttributesKey.createTextAttributesKey("PROPERTIES.VALID_STRING_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE),
      PropertiesBundle.messagePointer("options.properties.attribute.descriptor.valid.string.escape"),
      StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
    ),
    PROPERTIES_INVALID_STRING_ESCAPE(
      TextAttributesKey.createTextAttributesKey("PROPERTIES.INVALID_STRING_ESCAPE", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE),
      PropertiesBundle.messagePointer("options.properties.attribute.descriptor.invalid.string.escape"),
      StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
    );

    private static final Map<IElementType, PropertiesComponent> elementTypeToComponent;
    private static final Map<TextAttributesKey, PropertiesComponent> textAttributeKeyToComponent;

    static {
      elementTypeToComponent = Arrays.stream(values())
        .collect(Collectors.toMap(PropertiesComponent::getTokenType, Function.identity()));

      textAttributeKeyToComponent = Arrays.stream(values())
        .collect(Collectors.toMap(PropertiesComponent::getTextAttributesKey, Function.identity()));
    }

    private final TextAttributesKey myTextAttributesKey;
    private final Supplier<@Nls String> myMessagePointer;
    private final IElementType myTokenType;

    PropertiesComponent(TextAttributesKey textAttributesKey, Supplier<@Nls String> messagePointer, IElementType tokenType) {
      myTextAttributesKey = textAttributesKey;
      myMessagePointer = messagePointer;
      myTokenType = tokenType;
    }

    public TextAttributesKey getTextAttributesKey() {
      return myTextAttributesKey;
    }

    public Supplier<@Nls String> getMessagePointer() {
      return myMessagePointer;
    }

    IElementType getTokenType() {
      return myTokenType;
    }

    @ApiStatus.Internal
    public static PropertiesComponent getByTokenType(IElementType tokenType) {
      return elementTypeToComponent.get(tokenType);
    }

    static PropertiesComponent getByTextAttribute(TextAttributesKey textAttributesKey) {
      return textAttributeKeyToComponent.get(textAttributesKey);
    }

    public static @Nls String getDisplayName(TextAttributesKey key) {
      final PropertiesComponent component = getByTextAttribute(key);
      if (component == null) return null;
      return component.getMessagePointer().get();
    }

    public static @Nls HighlightSeverity getSeverity(TextAttributesKey key) {
      final PropertiesComponent component = getByTextAttribute(key);
      return component == PROPERTIES_INVALID_STRING_ESCAPE
             ? HighlightSeverity.WARNING
             : null;
    }
  }

}