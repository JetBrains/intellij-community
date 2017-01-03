import lombok.*;
import java.math.*;

class BarBar<T> {
  T content;

  public BarBar(T content) {
    this.content = content;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  public static class BarBarBuilder<T> {
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private T content;

    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    BarBarBuilder() {
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public BarBarBuilder<T> content(final T content) {
      this.content = content;
      return this;
    }
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public BarBar<T> build() {
      return new BarBar(content);
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public java.lang.String toString() {
      return "BarBar.BarBarBuilder(content=" + this.content + ")";
    }
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  public static <T> BarBarBuilder<T> builder() {
    return new BarBarBuilder<T>();
  }
}

class Main {
  public static void main(String[] args) {
    BarBar<BigDecimal> barBar = BarBar.<BigDecimal>builder().content(BigDecimal.ONE).build();
  }
}
