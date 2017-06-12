package slowCheck;

import java.util.function.Function;

class DoubleGenerator implements Function<DataStructure, Double> {

  private static double calcEdgeCaseProbability(double fraction) {
    double atStart = 0.9;
    double atEnd = 0.001;
    return atStart * Math.pow(atEnd / atStart, fraction);
  }

  @Override
  public Double apply(DataStructure data) {
    long i1 = data.drawInt();
    long i2 = data.drawInt();
    return Double.longBitsToDouble((i1 << 32) + i2);
  }
}
