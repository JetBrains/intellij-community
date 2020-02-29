@lombok.Value
public class ValueClass {
  String field;
  <warning descr="Redundant private field modifier">private</warning> String field2;
  <warning descr="Redundant final field modifier">final</warning> String field3;
}
