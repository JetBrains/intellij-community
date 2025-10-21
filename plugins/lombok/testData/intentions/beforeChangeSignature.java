// "Add 'String' as 1st parameter to method 'getFoo()'" "false"
@lombok.Getter
class A {
  String foo;
  {
    getFoo("<caret>");
  }
}