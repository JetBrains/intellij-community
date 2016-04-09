package de.plushnikov.manual;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@Slf4j
public final class LucenedHelper {
  @Getter
  int x;

  @Data
  private static class TreeLeafTest {
    private final String id;
    private final String text;
    private final boolean checked;
    private final String cls = "folder";
    private final boolean leaf = true;
  }

  public static void main(String[] args) {
    LucenedHelper luceneHelper = new LucenedHelper(3);
    luceneHelper.getX();
  }
}
