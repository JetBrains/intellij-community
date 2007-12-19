/**
 * @author Yura Cangea
 */
package com.intellij.xdebugger.ui;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import java.awt.*;

public interface DebuggerColors {
  TextAttributesKey BREAKPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("BREAKPOINT_ATTRIBUTES");
  TextAttributesKey EXECUTIONPOINT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("EXECUTIONPOINT_ATTRIBUTES");
  ColorKey RECURSIVE_CALL_ATTRIBUTES = ColorKey.createColorKey("RECURSIVE_CALL_ATTRIBUTES", new Color(255, 255, 215));
}
