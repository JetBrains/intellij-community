package calls;

class CallGraph {
  private static final String NAME = "root";

  static CallGraph createInstance() {
    return new CallGraph();
  }

  @Deprecated
  void root() {
    String local = NAME;
    first();
    second();
    second();
  }

  void first() {
    leaf();
  }

  void second() {
  }

  void leaf() {
  }

  void overloaded() {
    first();
  }

  void overloaded(String value) {
    second();
  }

  @org.junit.jupiter.api.Test
  void junitEntry() {
    leaf();
  }

  void callsBinaryJar() {
    org.apache.commons.csv.CSVParser.builder();
  }
}
