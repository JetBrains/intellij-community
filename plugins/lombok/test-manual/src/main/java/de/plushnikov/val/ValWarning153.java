package de.plushnikov.val;

import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ValWarning153 {
  public static void main(String[] args) {
    val valExample = new ArrayList<String>();

    Map<String, Float> notValExample = new HashMap<String, Float>();

    valExample.add("Hello");
    notValExample.put("Hello", 16.0f);

    System.out.println(valExample);
    System.out.println(notValExample);
  }
}
