// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.util.ui.PresentableEnum;
import com.jetbrains.python.PySyntaxCoreBundle;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.WRAP_AS_NEEDED;


public class PyCodeStyleSettings extends CustomCodeStyleSettings {

  public enum DictAlignment implements PresentableEnum {
    NONE("formatter.panel.dict.alignment.do.not.align"),
    ON_VALUE("formatter.panel.dict.alignment.align.on.value"),
    ON_COLON("formatter.panel.dict.alignment.align.on.colon");

    private final String key;

    DictAlignment(String key) {
      this.key = key;
    }

    public int asInt() {
      return ordinal();
    }

    @Override
    public @NotNull String getPresentableText() {
      return PySyntaxCoreBundle.message(key);
    }

    @Override
    public String toString() {
      return getPresentableText();
    }
  }

  // Unfortunately, the old serializer for code style settings can't handle enums
  public static final int DICT_ALIGNMENT_NONE = DictAlignment.NONE.asInt();
  public static final int DICT_ALIGNMENT_ON_VALUE = DictAlignment.ON_VALUE.asInt();
  public static final int DICT_ALIGNMENT_ON_COLON = DictAlignment.ON_COLON.asInt();

  /**
   * The "Chop down if long" wrapping option (collapse if it fits, otherwise one item per line). This is
   * the exact int the wrapping combo box persists for that choice; see the third entry of
   * {@link com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable#WRAP_VALUES}. Plain
   * {@code WRAP_ON_EVERY_ITEM} formats identically (both fall into the {@code CHOP_DOWN_IF_LONG} branch
   * of {@code WrappingUtil.getWrapType}) but is not a recognized combo value, so the Code Style UI would
   * fail to show the option as selected.
   */
  public static final int CHOP_DOWN_IF_LONG = WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

  public boolean SPACE_WITHIN_BRACES = false;
  public boolean SPACE_BEFORE_PY_COLON = false;
  public boolean SPACE_AFTER_PY_COLON = true;
  public boolean SPACE_BEFORE_LBRACKET = false;
  public boolean SPACE_AROUND_EQ_IN_NAMED_PARAMETER = false;
  public boolean SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = false;
  public boolean SPACE_AROUND_POWER_OPERATOR = true;
  public boolean SPACE_BEFORE_BACKSLASH = true;

  public int BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS = 2;
  public int BLANK_LINES_BEFORE_FIRST_METHOD = 0;
  public boolean BLANK_LINE_AT_FILE_END = true;

  public boolean ALIGN_COLLECTIONS_AND_COMPREHENSIONS = true;
  public boolean ALIGN_MULTILINE_IMPORTS = true;

  public boolean NEW_LINE_AFTER_COLON = false;
  public boolean NEW_LINE_AFTER_COLON_MULTI_CLAUSE = true;

  public boolean SPACE_AFTER_NUMBER_SIGN = true;
  public boolean SPACE_BEFORE_NUMBER_SIGN = true;

  public int DICT_ALIGNMENT = DICT_ALIGNMENT_NONE;

  @CommonCodeStyleSettings.WrapConstant
  public int DICT_WRAPPING = WRAP_AS_NEEDED;
  public boolean DICT_NEW_LINE_AFTER_LEFT_BRACE = false;
  public boolean DICT_NEW_LINE_BEFORE_RIGHT_BRACE = false;

  @CommonCodeStyleSettings.WrapConstant
  public int LIST_WRAPPING = WRAP_AS_NEEDED;
  public boolean LIST_NEW_LINE_AFTER_LEFT_BRACKET = false;
  public boolean LIST_NEW_LINE_BEFORE_RIGHT_BRACKET = false;

  @CommonCodeStyleSettings.WrapConstant
  public int SET_WRAPPING = WRAP_AS_NEEDED;
  public boolean SET_NEW_LINE_AFTER_LEFT_BRACE = false;
  public boolean SET_NEW_LINE_BEFORE_RIGHT_BRACE = false;


  @CommonCodeStyleSettings.WrapConstant
  public int TUPLE_WRAPPING = WRAP_AS_NEEDED;
  public boolean TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS = false;
  public boolean TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false;

  public int BLANK_LINES_AFTER_LOCAL_IMPORTS = 0;
  /**
   * Code style for most languages use continuation indent both for parameters in function definition and for arguments in function calls.
   * In Python continuation indent (assuming it's 8 spaces) for parameters is required by PEP 8, because otherwise they won't be visually 
   * distinctive from function body. However for arguments (except several special cases) both normal and continuation indents are acceptable 
   * (as long as they're multiple of 4), though examples in PEP 8 itself use mostly normal indent. Nonetheless, some users prefer to have 
   * the same indentation level for arguments as for parameters.
   */
  public boolean USE_CONTINUATION_INDENT_FOR_PARAMETERS = true;
  public boolean USE_CONTINUATION_INDENT_FOR_ARGUMENTS = false;
  public boolean USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = false;

  public boolean OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
  public boolean OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = false;
  public boolean OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST = true;
  public boolean OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = false;
  public boolean OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = false;
  public boolean OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER = false;

  /**
   * Affects wrapping of multiple imported names in a single "from" import.
   */
  @CommonCodeStyleSettings.WrapConstant
  public int FROM_IMPORT_WRAPPING = WRAP_AS_NEEDED;
  public boolean FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = false;
  public boolean FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false;
  
  @MagicConstant(intValues = {
    CommonCodeStyleSettings.DO_NOT_FORCE,
    CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
    CommonCodeStyleSettings.FORCE_BRACES_ALWAYS}
  )
  public boolean FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = false;
  public boolean FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = false;

  /**
   * Corresponds to the option of pycodestyle.py "--hang-closing". Basically, it means that the closing brace of a collection literal, 
   * a comprehension, an argument list, a parameter list or parentheses in "from" import statement should have the same indent as the items 
   * inside even if there is so called hanging indent (nothing follows the opening bracket on its line).
   */
  public boolean HANG_CLOSING_BRACKETS = false;

  public boolean FORMAT_INJECTED_FRAGMENTS = true;
  public boolean ADD_INDENT_INSIDE_INJECTIONS = false;

  public boolean USE_TRAILING_COMMA_IN_COLLECTIONS = false;
  public boolean USE_TRAILING_COMMA_IN_PARAMETER_LIST = false;
  public boolean USE_TRAILING_COMMA_IN_ARGUMENTS_LIST = false;

  /**
   * Identifies the active code style profile ({@link PyDefaultStyleGuide#CODE_STYLE_ID} or
   * {@link PyClassicStyleGuide#CODE_STYLE_ID}). {@code null} means a legacy scheme that predates the
   * PY-85946 migration and is therefore treated as "classic". Excluded from {@link #equals} so that
   * choosing a profile whose values match the defaults isn't reported as a modification.
   */
  @MagicConstant(stringValues = {PyDefaultStyleGuide.CODE_STYLE_ID, PyClassicStyleGuide.CODE_STYLE_ID})
  @PyCodeStyleReflectionUtil.SkipInEquals
  public @Nullable String CODE_STYLE_PROFILE = null;

  private final boolean isTempForDeserialize;

  public PyCodeStyleSettings(@NotNull CodeStyleSettings container) {
    this(container, false);
  }

  private PyCodeStyleSettings(@NotNull CodeStyleSettings container, boolean isTempForDeserialize) {
    super("Python", container);
    this.isTempForDeserialize = isTempForDeserialize;
    if (!isTempForDeserialize) {
      // The in-memory baseline follows the active default profile (PY-85946): modern when the opt-in
      // flag is on, so a fresh "Default" scheme simply IS modern and reads clean; classic otherwise.
      // The flag-off branch is byte-for-byte the historical behavior (classic values, no profile marker).
      if (PyCodeStyleDefaultsKt.isPyNewFormatterDefaultsActive()) {
        PyDefaultStyleGuide.applyToPyCustomSettings(this, true);
      }
      else {
        PyClassicStyleGuide.applyToPyCustomSettings(this, false);
      }
    }
  }

  private static PyCodeStyleSettings createForTempDeserialize(@NotNull CodeStyleSettings container) {
    return new PyCodeStyleSettings(container, true);
  }

  @Override
  public void readExternal(Element parentElement) throws InvalidDataException {
    if (isTempForDeserialize) {
      super.readExternal(parentElement);
      return;
    }

    // Discover the stored profile first, apply it as the baseline, then read the actual diffs on top.
    PyCodeStyleSettings tempSettings = createForTempDeserialize(getContainer());
    tempSettings.readExternal(parentElement);
    applyPyCodeStyle(tempSettings.CODE_STYLE_PROFILE, this, true);

    super.readExternal(parentElement);
  }

  @Override
  public void writeExternal(Element parentElement, @NotNull CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    if (CODE_STYLE_PROFILE != null) {
      // Apply the chosen profile to the baseline that diffs are computed against, so only real
      // deviations from the profile are serialized.
      PyCodeStyleSettings baseline = (PyCodeStyleSettings)parentSettings.clone();
      applyPyCodeStyle(CODE_STYLE_PROFILE, baseline, false);
      super.writeExternal(parentElement, baseline);
    }
    else {
      super.writeExternal(parentElement, parentSettings);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PyCodeStyleSettings)) return false;
    return PyCodeStyleReflectionUtil.comparePublicNonFinalFieldsWithSkip(this, obj);
  }

  private static void applyPyCodeStyle(@Nullable String codeStyleId, @NotNull PyCodeStyleSettings settings, boolean modifyCodeStyle) {
    if (codeStyleId == null) return;
    switch (codeStyleId) {
      case PyDefaultStyleGuide.CODE_STYLE_ID -> PyDefaultStyleGuide.applyToPyCustomSettings(settings, modifyCodeStyle);
      case PyClassicStyleGuide.CODE_STYLE_ID -> PyClassicStyleGuide.applyToPyCustomSettings(settings, modifyCodeStyle);
      default -> { }
    }
  }
}
