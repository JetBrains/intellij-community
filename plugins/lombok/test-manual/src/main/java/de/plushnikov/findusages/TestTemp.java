package de.plushnikov.findusages;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Data
@Slf4j
public class TestTemp {

  private int a;
  private int b;
  private String c;

  public static void main(String[] args) {
    TestTemp testTemp = new TestTemp();
    testTemp.setA(1);

    testTemp.getB();
    System.out.println(testTemp.getC());
    log.warn(testTemp.toString());

    DataItem item = DataItem.builder()
      .name("a")
      .createTimestamp(Instant.now())
      .content("content")
      .build();
    System.out.println("Item.name = " + item.name());
    System.out.println(item.password());

    Main m = new Main();
    System.out.println(m.getFoo());
    System.out.println(m.getFmyBar());
    System.out.println(m.getBaz());
    System.out.println(m.getBasic());

    m.setFoo(1);
    m.setFmyBar(2);
    m.setBaz(4);
    m.setBasic(5);
  }
}
