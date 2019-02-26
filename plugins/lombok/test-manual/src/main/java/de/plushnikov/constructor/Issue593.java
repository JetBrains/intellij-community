package de.plushnikov.constructor;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Issue593 {
  private int age = 10; // this assignment should be marked as redundant

//  public Issue593(int age) {
//    this.age = age;
//  }
}
