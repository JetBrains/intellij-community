package de.plushnikov.data;

import lombok.Data;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Mirasch
 */
@Data
public class DataClass {
  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public void macheWas() {

  }

  public static void main(String[] args) {
    DataClass dataClass = new DataClass();
    dataClass.getFloatProperty();
  }
}

@Data
class Blah {

  private Map<String, String> map;

  public String getMap(String key) {
    return map.get(key);
  }

  public static void main(String[] args) {
    Blah blah = new Blah();
    blah.getMap();
  }
}
