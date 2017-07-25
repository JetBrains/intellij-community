package training.keymap

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.util.SystemInfo
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * Created by karashevich on 18/03/15.
 */
object KeymapUtil {

  /**
   * @param actionId
   * *
   * @return null if actionId is null
   */
  fun getShortcutByActionId(actionId: String?): KeyStroke? {

    actionId ?: return null

    val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
    var keyStroke: KeyStroke? = null
    for (shortcut in shortcuts) {
      if (shortcut is KeyboardShortcut) {
        val keyboardShortcut = shortcut
        keyStroke = keyboardShortcut.firstKeyStroke
        if (actionId != "learn.next.lesson")
          break
        else {
          if (KeymapManager.getInstance().activeKeymap.getConflicts(actionId, keyboardShortcut).isEmpty())
            break
        }
      }
    }

    return keyStroke
  }

  fun getKeyStrokeText(keyStroke: KeyStroke?): String {
    if (keyStroke == null) return ""
    val modifiers = getModifiersText(keyStroke.modifiers)
    val key = if (SystemInfo.isMac) MacKeymapUtil.getKeyText(keyStroke.keyCode) else KeyEvent.getKeyText(keyStroke.keyCode)

    return toCanonical(modifiers) + key
  }

  private fun getModifiersText(modifiers: Int): String {
    return KeyEvent.getKeyModifiersText(modifiers)
  }

  /**
   * Adding spaces to keyModifier text
   * "shift+ctrl" -> "shift + ctrl"
   * if it is Mac replace "+" from modifiers
   */
  private fun toCanonical(modifiersString: String): String {
    if (modifiersString.isEmpty()) return ""

    val result = StringBuilder()

    if (SystemInfo.isMac) {
      if (modifiersString.contains("+")) {
        for (modifier in modifiersString.getModifiers())
          result.append(modifier)
        return result.toString()
      } else {
        return modifiersString
      }
    } else {
      if (modifiersString.contains("+")) {
        for (modifier in modifiersString.getModifiers())
          result.append(modifier).append(" + ")
        return result.toString().replaceSpacesWithNonBreakSpace()
      } else {
        return (modifiersString + " + ").replaceSpacesWithNonBreakSpace()
      }
    }
  }

  private fun String.getModifiers(): Array<String>
          = this.split("[ \\+]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

  private fun String.replaceSpacesWithNonBreakSpace(): String
          = this.replace(" ", "\u00A0")

}
