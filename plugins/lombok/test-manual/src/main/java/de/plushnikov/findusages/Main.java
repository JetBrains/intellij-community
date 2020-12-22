package de.plushnikov.findusages;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public class Main {
  @Accessors(prefix = "_")
  @Getter
  @Setter
  private Integer _foo;
  @Accessors(prefix = "f")
  @Getter
  @Setter
  private Integer fBar;
  @Getter
  @Setter
  private Integer baz;
  private Integer basic;

  public Integer getBasic() {
    return basic;
  }

  public void setBasic(Integer basic) {
    this.basic = basic;
  }

  public Main() {
    _foo = 10;
    fBar = 20;
    baz = 40;
  }

  public static void main(String[] args) {
    Main m = new Main();
    System.out.println(m.getFoo());
    System.out.println(m.getBar());
    System.out.println(m.getBaz());
    System.out.println(m.getBasic());

    m.setFoo(1);
    m.setBar(2);
    m.setBaz(4);
    m.setBasic(5);

    System.out.println(m.getFoo());
    System.out.println(m.getBar());
    System.out.println(m.getBaz());
    System.out.println(m.getBasic());
  }
}
