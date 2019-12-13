package org.jetbrains.plugins.textmate.regex;

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joni.Region;

import java.util.Arrays;

public class MatchData {
  public static final MatchData NOT_MATCHED = new MatchData(false, ArrayUtil.EMPTY_INT_ARRAY);

  private final boolean matched;
  @NotNull
  private final int[] offsets;

  private MatchData(boolean matched, @NotNull int[] offsets) {
    this.matched = matched;
    this.offsets = offsets;
  }

  public static MatchData fromRegion(@Nullable Region matchedRegion) {
    if (matchedRegion != null) {
      int[] offsets = new int[matchedRegion.numRegs * 2];
      for (int i = 0; i < matchedRegion.numRegs; i++) {
        int startIndex = i * 2;
        offsets[startIndex] = Math.max(matchedRegion.beg[i], 0);
        offsets[startIndex + 1] = Math.max(matchedRegion.end[i], 0);
      }
      return new MatchData(true, offsets);
    }
    return NOT_MATCHED;
  }

  public int count() {
    return offsets.length / 2;
  }

  public TextRange byteOffset() {
    return byteOffset(0);
  }

  @NotNull
  public TextRange byteOffset(int group) {
    int endIndex = group * 2 + 1;
    Preconditions.checkElementIndex(endIndex, offsets.length);
    return TextRange.create(offsets[endIndex - 1], offsets[endIndex]);
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
