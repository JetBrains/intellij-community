package de.plushnikov.value;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@RequiredArgsConstructor
public class Val {

//  @NonNull
  @NonFinal
  String s1;

  String other;

  public void test() {
    Val val = new Val("other");
    s1 = "this is possible";
//    other = "this is not possible";
  }

  public static void main(String[] args) {
    Val aaa = new Val("aaa");
    System.out.println(aaa.getS1());
  }
}

