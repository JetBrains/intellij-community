/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;




/**
 *
 */
public class Pair<A, B> {
  public final A first;
  public final B second;

  public Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  public A getFirst() {
    return first;
  }

  public B getSecond() {
    return second;
  }

  public static <A, B> Pair<A, B> create(A first, B second) {
    return new Pair<A,B>(first, second);
  }

  public final boolean equals(Object o){
    return o instanceof Pair && Comparing.equal(first, ((Pair)o).first) && Comparing.equal(second, ((Pair)o).second);
  }

  public final int hashCode(){
    int hashCode = 0;
    if (first != null){
      hashCode += first.hashCode();
    }
    if (second != null){
      hashCode += second.hashCode();
    }
    return hashCode;
  }

  public String toString() {
    return "<" + first + "," + second + ">";
  }
}
