package de.plushnikov.builder;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class Issue648 {
  private String id;
  private String firstName;

  @Singular
  private List<String> cars;

  public static void main(String[] args) {
    final Issue648 issue648 = Issue648.builder()
      .firstName("name")
      .cars(Collections.singletonList(""))
      .car("sss")
      .build();
    System.out.println(issue648);
  }
}
