package de.plushnikov.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public final class Data4 extends java.util.Timer {
  final int x = 8;

  public static void main(String[] args) {
    System.out.println(new Data4().toString());
  }
}
