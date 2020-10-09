package de.plushnikov.builder.singularperformance;

import lombok.Builder;
import lombok.Singular;

import java.util.Collection;

@Builder
public class SingularPerformanceIssueDemo {
  @Singular
  private Collection<java.lang.String> number_0_strings;


  public static void main(String[] args) {
    for (int i = 0; i < 20; i++) {
      System.out.println("\t@Singular\n" + "\tprivate Collection<java.lang.String> number_" + i + "_strings;");
//      System.out.println("\tprivate Collection<java.lang.String> number_" + i + "_strings;");
    }
  }
}
