package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.editor.Editor;

public interface EditingSides {
  Editor getEditor(FragmentSide side);
  LineBlocks getLineBlocks();
}
