package com.intellij.ide.structureView;

import com.intellij.openapi.fileEditor.FileEditor;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Feb 23, 2005
 * Time: 7:19:02 PM
 * To change this template use File | Settings | File Templates.
 */
public interface StructureViewWrapper {
  boolean selectCurrentElement(FileEditor fileEditor, boolean requestFocus);
}
