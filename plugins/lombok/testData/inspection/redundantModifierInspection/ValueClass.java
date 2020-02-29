@lombok.Value
public <warning descr="Redundant final class modifier">final</warning> class ValueClass {
  String field;
  <warning descr="Redundant private field modifier">private</warning> String field2;
  <warning descr="Redundant final field modifier">final</warning> String field3;
  static private final String field4 = "field4";
}
