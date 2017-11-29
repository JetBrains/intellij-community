import org.intellij.lang.annotations.Language;

class InjectionChangeTo {
  @Language("HTML")
  String myString = "<html>typppo<caret></html>";
}
