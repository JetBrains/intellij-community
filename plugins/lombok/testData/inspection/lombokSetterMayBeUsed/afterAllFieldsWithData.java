// "Use lombok @Setter for 'Foo'" "true"
import lombok.Data;

@Data
class Foo {
  private int bar;
  private boolean baz;

}