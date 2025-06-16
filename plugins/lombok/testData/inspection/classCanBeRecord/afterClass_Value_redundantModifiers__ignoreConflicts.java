// "Convert to record class" "true-preview"

/// Instance fields of this class have redundant modifiers, because @Value automatically:
///  1. marks non-static fields as 'final'
///  2. marks non-static, package-local fields as 'private'
///
/// @param name 'private' is redundant because of (1)
/// @param age  'final' is redundant because of (2)
/// @param city 'private' and 'final' are redundant because of (1) and (2)
record Person(String name, int age, String city) {
}
