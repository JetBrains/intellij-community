public class GetterDefinitionTest {

  /** name: correct prefix; body: returns */
  public int getX() {
    return field + 1;
  }

  /** name: correct prefix; body: returns field */
  public int getZ() {
    return field;
  }

  /** name: matches field; body: returns */
  public int getField() {
    return field + 1;
  }

  /** name: matches field; body: returns field */
  public int getField2() {
    return field2;
  }
  int dummyField;
  int field;
  int field2;

  /** name: correct prefix; body: immaterial */
  public int getY() {
    dummyField++;
    return dummyField;
  }

  int dummyField2;
}