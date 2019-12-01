package de.plushnikov.bug.issue634;

import lombok.*;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
class Node {

  @Setter
  @NonNull
  private NodeBuilder next;
  @NonNull
  @Getter
  private final Node element;
}
