package de.plushnikov.usages;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public class FindUsages {
  @Accessors(prefix = "_", fluent = true)
  @Getter
  @Setter
  private Integer _variableA;

  @Accessors(prefix = "_")
  @Getter
  @Setter
  private Integer _varaibleB;

  @Getter
  @Setter
  private Integer variableC;

  private Integer variableD;

  public Integer getVariableD() {
    return variableD;
  }

  public void setVariableD(Integer variableD) {
    this.variableD = variableD;
  }

  private FindUsages() {
    _variableA = 10;
    _varaibleB = 10;
    variableC = 20;
    variableD = 40;
  }

  public static void main(String[] args) {
    FindUsages m = new FindUsages();
    System.out.println(m.variableA());
    System.out.println(m.getVaraibleB());
    System.out.println(m.getVariableC());
    System.out.println(m.getVariableD());

    m.variableA(1);
    m.setVaraibleB(1);
    m.setVariableC(2);
    m.setVariableD(4);

    System.out.println(m.variableA());
    System.out.println(m.getVaraibleB());
    System.out.println(m.getVariableC());
    System.out.println(m.getVariableD());
  }
}