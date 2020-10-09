package de.plushnikov.delegate.issue82;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class MyDetailData<T> extends AbstractDetailData<Integer> implements Serializable {
  @Getter
  @Setter
  private String something;
}