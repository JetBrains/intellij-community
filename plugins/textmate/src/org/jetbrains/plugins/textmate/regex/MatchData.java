package org.jetbrains.plugins.textmate.regex;

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.TextRange;
import org.joni.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MatchData {
  public static final MatchData NOT_MATCHED = new MatchData("", false, Collections.emptyList());

  private final boolean matched;
  private final List<TextRange> offsets;
  private final CharSequence text;

  private MatchData(CharSequence text, boolean matched, List<TextRange> offsets) {
    this.text = text;
    this.matched = matched;
    this.offsets = offsets;
  }

  public static MatchData fromRegion(CharSequence text, byte[] bytes, Region matchedRegion) {
    List<TextRange> offsets;
    if (matchedRegion != null) {
      offsets = new ArrayList<>(matchedRegion.numRegs);
      for (int i = 0; i < matchedRegion.numRegs; i++) {
        int startOffset = RegexUtil.charOffsetByByteOffset(bytes, matchedRegion.beg[i]);
        int endOffset = RegexUtil.charOffsetByByteOffset(bytes, matchedRegion.end[i]);
        offsets.add(i, TextRange.create(startOffset, endOffset));
      }
      return new MatchData(text, true, offsets);
    }
    return NOT_MATCHED;
  }

  public CharSequence capture(int group) {
    TextRange textRange = offset(group);
    return text.subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public TextRange offset() {
    return offset(0);
  }

  public int count() {
    return offsets.size();
  }

  public TextRange offset(int group) {
    Preconditions.checkElementIndex(group, offsets.size());
    return offsets.get(group);
  }

  public boolean matched() {
    return matched;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MatchData matchData = (MatchData)o;

    if (matched != matchData.matched) return false;
    if (!Objects.equals(offsets, matchData.offsets)) return false;
    if (!Objects.equals(text, matchData.text)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (matched ? 1 : 0);
    result = 31 * result + (offsets != null ? offsets.hashCode() : 0);
    result = 31 * result + (text != null ? text.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "{ matched=" + matched +
           ", offsets=" + offsets +
           ", text=" + text +
           '}';
  }
}
