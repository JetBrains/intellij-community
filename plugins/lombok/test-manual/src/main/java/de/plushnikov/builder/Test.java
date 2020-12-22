package de.plushnikov.builder;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Builder
@Value
public class Test<T> {
  T value;

  public static void main(String[] args) {
    final TestBuilder<String> b = Test.<String>builder().value("a");
    final Test<String> t = b.build();
    //TODO deactivate RedundantTypeArgsInspection
    //https://ea.jetbrains.com/browser/ea_reports/1163987
  }
}