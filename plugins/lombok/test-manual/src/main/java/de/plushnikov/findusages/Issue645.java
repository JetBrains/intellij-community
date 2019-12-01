package de.plushnikov.findusages;

import lombok.Builder;

public class Issue645 {
  @Builder
  public static class SomeDataClass {
    public static class SomeDataClassBuilder {
      private void buildWithJSON() {
        this.jsonObject = "test";
      }
    }

    public final String jsonObject;
  }

  public static void main(String[] args) {
    SomeDataClass dataClass = SomeDataClass.builder().jsonObject("sdsdsd").build();
    System.out.println(dataClass.jsonObject);
  }
}
