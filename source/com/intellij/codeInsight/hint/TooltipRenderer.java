package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.Editor;
import com.intellij.ui.LightweightHint;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Feb 21, 2005
 * Time: 7:11:53 PM
 * To change this template use File | Settings | File Templates.
 */
public interface TooltipRenderer {
  LightweightHint show(final Editor editor, Point p, boolean alignToRight, TooltipGroup group);
}
