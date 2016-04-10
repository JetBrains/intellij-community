package de.plushnikov.value;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ValueExample {

  String name;
  int age = 0;
  double score;

  public void la() {
//    ValueExample.builder().age(1).build();
  }
}