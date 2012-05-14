public class RearrangementTest25 {
  // ...

  private double getHorizontalstrecke() {
    // ...
    Double meanValue = new Double(4.0f);
    if (meanValue != null) {
      return meanValue.getValue();
    }
    // ...
    return 0.0f;
  }

  private double getRichtungErrorLimit(double sr, double sz, double s) {
    if (s == 0f) { // (!G2Null.isValid(s)) {

      return sr;
    }
    return Math.sqrt(QMath.square(sr) + QMath.square(sz / s));
  }

  private void checkDoubleMessung(double messung) {

  }
}
