// "Add 'String' as 1st parameter to method 'getFoo'" "true"
@lombok.Getter
class A {
  String foo;
  {
    getFoo("<caret>");
  }
}