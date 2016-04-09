package de.plushnikov.builder;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestResource {
  private String field1;

  public static void main(String[] args) {
    TestResource.TestResourceBuilder builder = TestResource.builder();
    System.out.println(builder.field1("something"));
  }
}
