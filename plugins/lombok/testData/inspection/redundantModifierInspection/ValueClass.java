@lombok.Value
public <warning descr="@Value already marks the class final.">final</warning> class ValueClass {
  String field;
  <warning descr="@Value already marks non-static, package-local fields private.">private</warning> String field2;
  <warning descr="@Value already marks non-static fields final.">final</warning> String field3;
  static private final String field4 = "field4";
}
