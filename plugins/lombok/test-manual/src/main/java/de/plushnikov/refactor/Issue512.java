package de.plushnikov.refactor;

import com.google.common.collect.ComparisonChain;
import lombok.NonNull;
import lombok.Value;

public class Issue512 {
  class Tile {
    int id;

    int getId() {
      return id;
    }
  }

  @Value              // <-- This needs to be Lombok supplied
  private static class TileIdFormatAndBaseLevel implements Comparable<TileIdFormatAndBaseLevel> {
    @NonNull
    Tile tile;      // <-- If you rename this 'tile' to 'xyz', the o.getTile() below becomes this.getXYZ()

    @Override
    public int compareTo(@NonNull final TileIdFormatAndBaseLevel o) {
      return ComparisonChain.start()
        .compare(o.getTile().getId(), tile.getId())
        .result();
    }
  }
}
