package de.plushnikov.val;

import lombok.val;

import java.util.ArrayList;
import java.util.List;

public class Issue183 {
  public static <T> List<T> forClass(Class<T> clazz) {
    return new ArrayList<T>();
  }

  public static void main(String[] args) {
    val myList = Issue183.forClass(Integer.class);
    myList.add(1);

    final Issue183 demo = new Issue183();

    val x = demo.convertMsg("sisu", 1);
  }

  public <T> T convertMsg(String msg, int userMid) {
    return (T) (msg + userMid);
  }
}