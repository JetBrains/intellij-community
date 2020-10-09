package de.plushnikov.findusages;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyBean implements StartEndable{
  private LocalDateTime start;
  private LocalDateTime end;

  public static void main(String[] args) {
    MyBean myBean = new MyBean();

    System.out.println(myBean.getEnd());
    System.out.println(myBean.getStart());

    StartEndable startEndable = myBean;
    System.out.println(startEndable.getStart());
    System.out.println(startEndable.getEnd());
  }
}
