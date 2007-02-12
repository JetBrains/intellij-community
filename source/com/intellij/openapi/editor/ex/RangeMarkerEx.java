/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 10, 2002
 * Time: 5:54:59 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;

public interface RangeMarkerEx extends RangeMarker {
  void documentChanged(DocumentEvent e);
}