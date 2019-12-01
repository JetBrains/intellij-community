package de.plushnikov.bug.issue634;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Builder
@Getter
@Setter
public class SomeBoxClass {

  private Node.NodeBuilder intField;

  @NonNull
  private String someString;

  @NonNull
  private Node node;

  @NonNull
  private LinkedListImpl someImpl;
}
