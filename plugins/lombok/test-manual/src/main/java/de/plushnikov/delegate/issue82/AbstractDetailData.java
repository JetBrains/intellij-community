package de.plushnikov.delegate.issue82;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

public abstract class AbstractDetailData<T> implements Serializable {
  @Getter
  @Setter
  private List<T> result;
}




