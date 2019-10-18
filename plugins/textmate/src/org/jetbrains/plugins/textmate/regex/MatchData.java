package org.jetbrains.plugins.textmate.regex;

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joni.Region;

import java.util.Arrays;

public class MatchData {
  public static final MatchData NOT_MATCHED = new MatchData(false, TextRange.EMPTY_ARRAY);

  private final boolean matched;
  @NotNull
  private final TextRange[] offsets;

  private MatchData(boolean matched, @NotNull TextRange[] offsets) {
    this.matched = matched;
    this.offsets = offsets;
  }

  public static MatchData fromRegion(@Nullable Region matchedRegion) {
    if (matchedRegion != null) {
      TextRange[] offsets = new TextRange[matchedRegion.numRegs];
      for (int i = 0; i < matchedRegion.numRegs; i++) {
        offsets[i] = TextRange.create(Math.max(matchedRegion.beg[i], 0), Math.max(matchedRegion.end[i], 0));
      }
      return new MatchData(true, offsets);
    }
    return NOT_MATCHED;
  }

  public int count() {
    return offsets.length;
  }

  public TextRange byteOffset() {
    return byteOffset(0);
  }

  @NotNull
  public TextRange byteOffset(int group) {
    Preconditions.checkElementIndex(group, offsets.length);
    return offsets[group];
  }

  public TextRange charOffset(byte[] stringBytes) {
    return charOffset(stringBytes, 0);
  }

  @NotNull
  public TextRange charOffset(byte[] stringBytes, int group) {
    return RegexUtil.charRangeByByteRange(stringBytes, byteOffset(group));
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
    if (!Arrays.equals(offsets, matchData.offsets)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * (matched ? 1 : 0) + Arrays.hashCode(offsets);
  }

  @Override
  public String toString() {
    return "{ matched=" + matched + ", offsets=" + Arrays.toString(offsets) + '}';
  }
}
