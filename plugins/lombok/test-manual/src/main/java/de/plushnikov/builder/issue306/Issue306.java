package de.plushnikov.builder.issue306;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
class FooFoo {
  private String text;
}

@Getter
class BarBar<T> extends FooFoo {
  private T content;

  @Builder
  public BarBar(String text, T content) {
    super(text);
    this.content = content;
  }
}

public class Issue306 {

  public static void main(String[] args) {
    BarBar.BarBarBuilder<BigDecimal> builder = BarBar.<BigDecimal>builder();
    BarBar<BigDecimal> barBar = builder.text("Hiho").content(BigDecimal.ONE).build();
  }

}
