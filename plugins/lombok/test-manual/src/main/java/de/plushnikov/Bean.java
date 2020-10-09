package de.plushnikov;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@ToString
public class Bean {
  @Setter(AccessLevel.PUBLIC)
  @Getter
  private Integer wert;

  @Setter(AccessLevel.PACKAGE)
  @Getter
  private int name;

  @Setter(AccessLevel.PROTECTED)
  @Getter
  private String VorName;

  @Setter(AccessLevel.PACKAGE)
  @Getter
  private Date birthsday;

  @Setter
  @Getter
  private boolean hasACat;

  @Setter
  @Getter
  private Boolean hasBCat;

  protected void calcMe(int aaa) {

  }

  void calcMe2(int aaa) {

  }

  private void calcMe3(int aaa) {

  }

//    public Integer getWert() {
//        return 100;
//    }

}
