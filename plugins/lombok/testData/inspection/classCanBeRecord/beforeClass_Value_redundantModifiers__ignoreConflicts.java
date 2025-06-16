// "Convert to record class" "true-preview"

/// Instance fields of this class have redundant modifiers, because @Value automatically:
///  1. marks non-static fields as 'final'
///  2. marks non-static, package-local fields as 'private'
@lombok.Value
class Person<caret> {
  private String name; // 'private' is redundant because of (1)
  final int age; // 'final' is redundant because of (2)
  private final String city; // 'private' and 'final' are redundant because of (1) and (2)
}
