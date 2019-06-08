package org.intellij.plugins.markdown.ui.actions.editorLayout;

import org.intellij.plugins.markdown.ui.split.SplitFileEditor;

public class PreviewOnlyLayoutChangeAction extends BaseChangeSplitLayoutAction {
  protected PreviewOnlyLayoutChangeAction() {
    super(SplitFileEditor.SplitEditorLayout.SECOND);
  }
}
