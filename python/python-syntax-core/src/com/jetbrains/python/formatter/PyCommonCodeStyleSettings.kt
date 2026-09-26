// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleProvider
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.formatter.PyCodeStyleReflectionUtil.comparePublicNonFinalFieldsWithSkip
import org.jdom.Element
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Python-specific [CommonCodeStyleSettings] that carries the active code style profile id
 * ([.CODE_STYLE_PROFILE]) for the common (language-shared) formatter fields, mirroring the
 * Kotlin `KotlinCommonCodeStyleSettings`.
 * 
 * 
 * Without this subclass there would be no place to store a per-profile baseline for common fields
 * such as `METHOD_PARAMETERS_WRAP` / `CALL_PARAMETERS_WRAP` / the `RPAREN_ON_NEXT_LINE`
 * flags / `ALIGN_MULTILINE_PARAMETERS*` / `RIGHT_MARGIN`, so they would be serialized as
 * explicit diffs for every "default"-profile user. See PY-85946.
 */
class PyCommonCodeStyleSettings private constructor(private val isTempForDeserialize: Boolean) :
  CommonCodeStyleSettings(PythonLanguage.INSTANCE) {
  // `@JvmField` is required so this is a real public Java field, exactly like the Java mirror's
  // `public String CODE_STYLE_PROFILE`. The profile baseline machinery reaches it purely through
  // public-field reflection — `copyPublicFields` in [clone] (ReflectionUtil.copyFields over
  // getFields()), `DefaultJDOMExternalizer.write` in [writeExternalBase], and
  // [comparePublicNonFinalFieldsWithSkip] in [equals]. A plain Kotlin `var` compiles to a private
  // backing field that none of them would see, silently dropping the profile id on clone and
  // failing to serialize it whenever soft margins are empty (the classic profile). The
  // `@field:` target keeps `SkipInEquals` on the field that reflection inspects.
  @JvmField
  @field:PyCodeStyleReflectionUtil.SkipInEquals
  var CODE_STYLE_PROFILE: String? = null

  constructor() : this(false)

  init {
    if (!isTempForDeserialize) {
      // The in-memory baseline follows the active default profile (PY-85946): modern when the opt-in
      // flag is on so a fresh "Default" scheme is modern and reads clean; classic (unmarked, exactly
      // as historically) otherwise.
      if (isPyNewFormatterDefaultsActive()) {
        applyPyCodeStyle(PyDefaultStyleGuide.CODE_STYLE_ID, this, true)
      }
      else {
        applyPyCodeStyle(PyClassicStyleGuide.CODE_STYLE_ID, this, false)
      }
    }
  }

  override fun readExternal(element: Element?) {
    if (isTempForDeserialize) {
      super.readExternal(element)
      return
    }

    val tempDeserialize: PyCommonCodeStyleSettings = createForTempDeserialize()
    tempDeserialize.readExternal(element)

    applyPyCodeStyle(tempDeserialize.CODE_STYLE_PROFILE, this, true)

    super.readExternal(element)
  }

  override fun writeExternal(element: Element, provider: LanguageCodeStyleProvider) {
    val defaultSettings = provider.getDefaultCommonSettings()

    // Apply the chosen profile to the comparison baseline so only real deviations are serialized.
    applyPyCodeStyle(CODE_STYLE_PROFILE, defaultSettings, false)

    writeExternalBase(element, defaultSettings, provider)
  }

  //<editor-fold desc="Copied and adapted from CommonCodeStyleSettings">
  private fun writeExternalBase(
    element: Element,
    defaultSettings: CommonCodeStyleSettings,
    provider: LanguageCodeStyleProvider,
  ) {
    val supportedFields = provider.getSupportedFields()
    if (supportedFields != null) {
      supportedFields.add("FORCE_REARRANGE_MODE")
      supportedFields.add("CODE_STYLE_PROFILE")
    }
    else {
      return
    }

    DefaultJDOMExternalizer.write(this, element, SupportedFieldsDiffFilter(this, supportedFields, defaultSettings))
    val softMargins = getSoftMargins()
    serializeInto(softMargins, element)

    val myIndentOptions = indentOptions
    if (myIndentOptions != null) {
      val defaultIndentOptions = defaultSettings.indentOptions
      val indentOptionsElement = Element(INDENT_OPTIONS_TAG)
      myIndentOptions.serialize(indentOptionsElement, defaultIndentOptions)
      if (!indentOptionsElement.children.isEmpty()) {
        element.addContent(indentOptionsElement)
      }
    }

    val myArrangementSettings = arrangementSettings
    if (myArrangementSettings != null) {
      val container = Element(ARRANGEMENT_ELEMENT_NAME)
      ArrangementUtil.writeExternal(container, myArrangementSettings, provider.getLanguage())
      if (!container.children.isEmpty()) {
        element.addContent(container)
      }
    }
  }

  // SoftMargins.serializeInto
  private fun serializeInto(softMargins: MutableList<Int?>, element: Element) {
    if (!softMargins.isEmpty()) {
      XmlSerializer.serializeInto(this, element)
    }
  }

  //</editor-fold>
  override fun clone(rootSettings: CodeStyleSettings): CommonCodeStyleSettings {
    val commonSettings = PyCommonCodeStyleSettings()
    copyPublicFields(this, commonSettings)

    // Reflection is used here only because the base CommonCodeStyleSettings#clone hard-codes
    // `new CommonCodeStyleSettings(...)` and offers no hook to clone into a subclass instance, while
    // `setRootSettings` / `setSoftMargins` are package-private. This mirrors KotlinCommonCodeStyleSettings#clone;
    // keep the two in sync if the platform ever exposes a supported extension point.
    try {
      val setRootSettingsMethod =
        CommonCodeStyleSettings::class.java.getDeclaredMethod("setRootSettings", CodeStyleSettings::class.java)
      setRootSettingsMethod.setAccessible(true)
      setRootSettingsMethod.invoke(commonSettings, rootSettings)
    }
    catch (e: NoSuchMethodException) {
      throw IllegalStateException(e)
    }
    catch (e: IllegalAccessException) {
      throw IllegalStateException(e)
    }
    catch (e: InvocationTargetException) {
      throw IllegalStateException(e)
    }

    commonSettings.isForceArrangeMenuAvailable = isForceArrangeMenuAvailable

    val indentOptions = getIndentOptions()
    if (indentOptions != null) {
      val targetIndentOptions = commonSettings.initIndentOptions()
      targetIndentOptions.copyFrom(indentOptions)
    }

    val arrangementSettings = getArrangementSettings()
    if (arrangementSettings != null) {
      commonSettings.setArrangementSettings(arrangementSettings.clone())
    }

    try {
      var setSoftMarginsMethod: Method? = null
      for (method in CommonCodeStyleSettings::class.java.declaredMethods) {
        if ("setSoftMargins" == method.name) {
          setSoftMarginsMethod = method
          break
        }
      }
      if (setSoftMarginsMethod != null) {
        setSoftMarginsMethod.setAccessible(true)
        setSoftMarginsMethod.invoke(commonSettings, softMargins)
      }
    }
    catch (e: IllegalAccessException) {
      throw IllegalStateException(e)
    }
    catch (e: InvocationTargetException) {
      throw IllegalStateException(e)
    }

    return commonSettings
  }

  override fun equals(obj: Any?): Boolean {
    if (obj !is PyCommonCodeStyleSettings) {
      return false
    }

    if (!comparePublicNonFinalFieldsWithSkip(this, obj)) {
      return false
    }

    if (softMargins != obj.softMargins) {
      return false
    }

    val options = indentOptions
    if ((options == null && obj.indentOptions != null) ||
        (options != null && options != obj.indentOptions)
    ) {
      return false
    }

    return arrangementSettingsEqual(obj)
  }

  companion object {
    private const val INDENT_OPTIONS_TAG = "indentOptions"
    private const val ARRANGEMENT_ELEMENT_NAME = "arrangement"

    private fun createForTempDeserialize(): PyCommonCodeStyleSettings {
      return PyCommonCodeStyleSettings(true)
    }

    private fun applyPyCodeStyle(
      codeStyleId: String?,
      codeStyleSettings: CommonCodeStyleSettings,
      modifyCodeStyle: Boolean,
    ) {
      if (codeStyleId == null) return
      when (codeStyleId) {
        PyDefaultStyleGuide.CODE_STYLE_ID -> PyDefaultStyleGuide.applyToCommonSettings(codeStyleSettings, modifyCodeStyle)
        PyClassicStyleGuide.CODE_STYLE_ID -> PyClassicStyleGuide.applyToCommonSettings(codeStyleSettings, modifyCodeStyle)
        else -> {}
      }
    }
  }
}
