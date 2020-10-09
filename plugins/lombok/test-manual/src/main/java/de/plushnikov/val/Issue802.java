package de.plushnikov.val;

import lombok.val;
import java.util.Optional;
public class Issue802 {
  public static void main(String... args) {
    val strOpt = Optional.of("1");
    val intOptInferredLambda = strOpt.map(str -> Integer.valueOf(str));
    val intOptInferredMethodRef = strOpt.map(Integer::valueOf);
    Optional<Integer> intOptExplicit = intOptInferredMethodRef;
    intOptExplicit = intOptInferredLambda;
  }
}
