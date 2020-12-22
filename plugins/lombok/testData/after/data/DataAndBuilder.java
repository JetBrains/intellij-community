public class DataAndBuilder {
  private int x;
  private int y;

  DataAndBuilder(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public static DataAndBuilderBuilder builder() {
    return new DataAndBuilderBuilder();
  }

  public int getX() {
    return this.x;
  }

  public int getY() {
    return this.y;
  }

  public void setX(int x) {
    this.x = x;
  }

  public void setY(int y) {
    this.y = y;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof DataAndBuilder)) return false;
    final DataAndBuilder other = (DataAndBuilder) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.getX() != other.getX()) return false;
    if (this.getY() != other.getY()) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataAndBuilder;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getX();
    result = result * PRIME + this.getY();
    return result;
  }

  public String toString() {
    return "DataAndBuilder(x=" + this.getX() + ", y=" + this.getY() + ")";
  }

  public static class DataAndBuilderBuilder {
    private int x;
    private int y;

    DataAndBuilderBuilder() {
    }

    public DataAndBuilder.DataAndBuilderBuilder x(int x) {
      this.x = x;
      return this;
    }

    public DataAndBuilder.DataAndBuilderBuilder y(int y) {
      this.y = y;
      return this;
    }

    public DataAndBuilder build() {
      return new DataAndBuilder(x, y);
    }

    public String toString() {
      return "DataAndBuilder.DataAndBuilderBuilder(x=" + this.x + ", y=" + this.y + ")";
    }
  }
}
