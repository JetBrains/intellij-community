import lombok.*;
import java.math.*;

class BarBar<T> {
  T content;
  @Builder
  public BarBar(T content) {
    this.content = content;
  }
}

class Main {
  public static void main(String[] args) {
    BarBar<BigDecimal> barBar = BarBar.<BigDecimal>builder().content(BigDecimal.ONE).build();
  }
}
