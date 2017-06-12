package slowCheck;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
class ActionOnRange {
  private final RangeMarker myMarker;
  private TextRange finalRange;

  ActionOnRange(Document document, int start, int end) {
    myMarker = document.createRangeMarker(start, end);
  }

  int getStartOffset() {
    TextRange range = getFinalRange();
    return range == null ? -1 : range.getStartOffset();
  }

  @Nullable
  TextRange getFinalRange() {
    if (finalRange == null) {
      finalRange = myMarker.isValid() ? new TextRange(myMarker.getStartOffset(), myMarker.getEndOffset()) : null;
    }
    return finalRange;
  }
}
