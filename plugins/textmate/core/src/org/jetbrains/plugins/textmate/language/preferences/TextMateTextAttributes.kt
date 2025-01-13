package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.plist.Plist

class TextMateTextAttributes(val foreground: String?, val background: String?, val fontStyle: FontStyle) {
  enum class FontStyle {
    PLAIN, ITALIC, BOLD, UNDERLINE
  }

  companion object {
    fun fromPlist(settingsPlist: Plist): TextMateTextAttributes? {
      var empty = true
      var foreground: String? = null
      var background: String? = null
      var fontStyle = FontStyle.PLAIN

      for (entry in settingsPlist.entries()) {
        val propertyName = entry.key
        val value = entry.value.string
        if (Constants.FOREGROUND_KEY.equals(propertyName, ignoreCase = true)) {
          foreground = value
          empty = false
        }
        else if (Constants.FONT_STYLE_KEY.equals(propertyName, ignoreCase = true)) {
          if (Constants.ITALIC_FONT_STYLE.equals(value, ignoreCase = true)) {
            fontStyle = FontStyle.ITALIC
            empty = false
          }
          else if (Constants.BOLD_FONT_STYLE.equals(value, ignoreCase = true)) {
            fontStyle = FontStyle.BOLD
            empty = false
          }
          else if (Constants.UNDERLINE_FONT_STYLE.equals(value, ignoreCase = true)) {
            fontStyle = FontStyle.UNDERLINE
            empty = false
          }
          else {
            fontStyle = FontStyle.PLAIN
            empty = false
          }
        }
        else if (Constants.BACKGROUND_KEY.equals(propertyName, ignoreCase = true)) {
          background = value
          empty = false
        }
      }
      return if (empty) null else TextMateTextAttributes(foreground, background, fontStyle)
    }
  }
}
