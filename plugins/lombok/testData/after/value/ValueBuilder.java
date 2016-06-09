import lombok.Value;
import lombok.experimental.Builder;

public final class ValueBuilder {
  private final String o1;
  private final int o2;
  private final double o3;

  public static void main(String[] args) {
    ValueBuilder builder = new ValueBuilder("1", 2, 3.0);
    System.out.println(builder);
  }

  @java.beans.ConstructorProperties({"o1", "o2", "o3"})
  ValueBuilder(String o1, int o2, double o3) {
    this.o1 = o1;
    this.o2 = o2;
    this.o3 = o3;
  }

  public static ValueBuilderBuilder builder() {
    return new ValueBuilderBuilder();
  }

  public String getO1() {
    return this.o1;
  }

  public int getO2() {
    return this.o2;
  }

  public double getO3() {
    return this.o3;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ValueBuilder)) return false;
    final ValueBuilder other = (ValueBuilder) o;
    final Object this$o1 = this.o1;
    final Object other$o1 = other.o1;
    if (this$o1 == null ? other$o1 != null : !this$o1.equals(other$o1)) return false;
    if (this.o2 != other.o2) return false;
    if (Double.compare(this.o3, other.o3) != 0) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $o1 = this.o1;
    result = result * PRIME + ($o1 == null ? 0 : $o1.hashCode());
    result = result * PRIME + this.o2;
    final long $o3 = Double.doubleToLongBits(this.o3);
    result = result * PRIME + (int) ($o3 >>> 32 ^ $o3);
    return result;
  }

  public String toString() {
    return "ValueBuilder(o1=" + this.o1 + ", o2=" + this.o2 + ", o3=" + this.o3 + ")";
  }

  public static class ValueBuilderBuilder {
    private String o1;
    private int o2;
    private double o3;

    ValueBuilderBuilder() {
    }

    public ValueBuilder.ValueBuilderBuilder o1(String o1) {
      this.o1 = o1;
      return this;
    }

    public ValueBuilder.ValueBuilderBuilder o2(int o2) {
      this.o2 = o2;
      return this;
    }

    public ValueBuilder.ValueBuilderBuilder o3(double o3) {
      this.o3 = o3;
      return this;
    }

    public ValueBuilder build() {
      return new ValueBuilder(o1, o2, o3);
    }

    public String toString() {
      return "ValueBuilder.ValueBuilderBuilder(o1=" + this.o1 + ", o2=" + this.o2 + ", o3=" + this.o3 + ")";
    }
  }
}