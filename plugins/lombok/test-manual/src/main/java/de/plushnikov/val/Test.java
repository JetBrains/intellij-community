package de.plushnikov.val;

import lombok.val;

import java.util.ArrayList;
import java.util.List;

public class Test {

  public static void main(String[] args) {
    val myList = Test.forClass(Integer.class);

    myList.add(new Integer(args[0]));
  }

  public static <T> List<T> forClass(Class<T> clazz) {
    return new ArrayList<T>();
  }

}