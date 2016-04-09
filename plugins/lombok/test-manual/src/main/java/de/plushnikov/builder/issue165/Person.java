package de.plushnikov.builder.issue165;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Person {
  String gender;
  String firstName;
  String lastName;

  List<String> children = new ArrayList<>();
  boolean isParent;
}
