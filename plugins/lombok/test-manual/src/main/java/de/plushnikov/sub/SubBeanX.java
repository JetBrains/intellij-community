package de.plushnikov.sub;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@ToString
public class SubBeanX {

  @Setter(AccessLevel.PUBLIC)
  @Getter
  private int name;
  @Setter(AccessLevel.PROTECTED)
  @Getter
  private String VorName;
  @Setter(AccessLevel.PACKAGE)
  @Getter
  private Date Geburtsdatum2;
  @Setter
  @Getter
  private boolean hasACat;

  private boolean hasACast;


  protected void calcMe(int aaa) {
    getGeburtsdatum2();
  }

  void calcMe2(int aaa) {
    setVorName("" + aaa);
  }

  private void calcMe3(int aaa) {
    getName();
  }
}
