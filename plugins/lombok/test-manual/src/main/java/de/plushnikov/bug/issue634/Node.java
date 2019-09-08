package de.plushnikov.bug.issue634;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class Node {
  @Setter
  private Node next;

  private final Object element;
}
