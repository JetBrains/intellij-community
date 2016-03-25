package de.plushnikov.val;

import lombok.val;

import java.util.ArrayList;
import java.util.List;

public class Issue168 {
  public static <T> List<T> forClass(Class<T> clazz) {
    return new ArrayList<T>();
  }

  public static void main(String[] args) {
    val myList = Issue168.forClass(Integer.class);
    myList.add(123);
    myList.add(new Integer(args[0]));
    myList.size();

    myList.size();
  }
}
