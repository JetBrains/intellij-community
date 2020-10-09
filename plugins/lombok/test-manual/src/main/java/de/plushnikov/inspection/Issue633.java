package de.plushnikov.inspection;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Issue633 {
  @Builder.Default
  private String deleted = "N";

  private String deleted2;

}
