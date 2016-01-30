package de.plushnikov.refactor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.Date;
import java.util.logging.Logger;

@Getter(onMethod = {}, value = AccessLevel.PACKAGE)
@Setter(onMethod = {}, value = AccessLevel.PROTECTED)
public class Bean {

  public static final Logger log324 = java.util.logging.Logger.getLogger(Bean.class.getName());

  public void logHallo() {
    log324.info("Hello!");
  }

  public static void main(String[] args) {
    Bean bean = new Bean();
    bean.setA(123);
    System.out.println(bean.getA());
    bean.logHallo();
  }

  private static int a;
  private float b;
  private double c;
  private String d;
  private Integer e;
  private InputStream f;
  private Date g;

  public Bean() {
  }

  public static int getA() {
    return a;
  }

  public void setA(int a) {
    this.a = a;
  }

  public float getB() {
    return b;
  }

  public void setB(float b) {
    this.b = b;
  }

  public double getC() {
    return c;
  }

  public void setC(double c) {
    this.c = c;
  }

  public String getD() {
    return d;
  }

  public void setD(String d) {
    this.d = d;
  }

  public Integer getE() {
    return e;
  }

  public void setE(Integer e) {
    this.e = e;
  }

  public InputStream getF() {
    return f;
  }

  public void setF(InputStream f) {
    this.f = f;
  }

  public Date getG() {
    return g;
  }

  public void setG(Date g) {
    this.g = g;
  }
}
