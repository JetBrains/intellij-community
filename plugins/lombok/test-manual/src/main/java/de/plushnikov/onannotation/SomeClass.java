package de.plushnikov.onannotation;


import lombok.Getter;

import javax.validation.constraints.NotNull;

@Getter(onMethod_ = @NotNull)
public class SomeClass {
  private Integer anInt;
}
