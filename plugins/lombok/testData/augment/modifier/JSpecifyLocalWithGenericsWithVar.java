package org.example.lombok2;

import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Scratch {

  <T> void exact(GenericFactory<T> factory) {
    val created = factory.create();
    System.out.println("Created: " + created);
  }

  @FunctionalInterface
  interface GenericFactory<T> {
    @Nullable T create();
  }
}