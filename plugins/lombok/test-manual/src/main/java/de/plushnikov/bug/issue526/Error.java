package de.plushnikov.bug.issue526;

import lombok.Value;
import lombok.experimental.Accessors;

@Accessors
@Value(staticConstructor = "of")
public final class Error<C, T> {
  private final C code;
  private final T subject;
  private final String reason;

}

