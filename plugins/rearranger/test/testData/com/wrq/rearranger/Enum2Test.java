class Enum2Test {
  /** Presentation Events. */
  public enum Event {
    barcodeBorder,
    htmlResponse;
  }

  public enum Event2 {
    barcodeBorder,
    htmlResponse
  }

  ;

  enum Coin {
    Nickel(5),
    Dime(10);
    final int value;

    Coin(int value) {
      this.value = value;
    }
  }
}
