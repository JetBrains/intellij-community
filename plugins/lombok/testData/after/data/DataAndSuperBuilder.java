public class DataAndSuperBuilder {
  private int x;
  private int y;

  protected DataAndSuperBuilder(DataAndSuperBuilderBuilder<?, ?> b) {
    this.x = b.x;
    this.y = b.y;
  }

  public static DataAndSuperBuilderBuilder<?, ?> builder() {
    return new DataAndSuperBuilderBuilderImpl();
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
    if (!(o instanceof DataAndSuperBuilder)) return false;
    final DataAndSuperBuilder other = (DataAndSuperBuilder) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.getX() != other.getX()) return false;
    if (this.getY() != other.getY()) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataAndSuperBuilder;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getX();
    result = result * PRIME + this.getY();
    return result;
  }

  public String toString() {
    return "DataAndSuperBuilder(x=" + this.getX() + ", y=" + this.getY() + ")";
  }

  public static abstract class DataAndSuperBuilderBuilder<C extends DataAndSuperBuilder, B extends DataAndSuperBuilderBuilder<C, B>> {
    private int x;
    private int y;

    public B x(int x) {
      this.x = x;
      return self();
    }

    public B y(int y) {
      this.y = y;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "DataAndSuperBuilder.DataAndSuperBuilderBuilder(x=" + this.x + ", y=" + this.y + ")";
    }
  }

  private static final class DataAndSuperBuilderBuilderImpl extends DataAndSuperBuilderBuilder<DataAndSuperBuilder, DataAndSuperBuilderBuilderImpl> {
    private DataAndSuperBuilderBuilderImpl() {
    }

    protected DataAndSuperBuilder.DataAndSuperBuilderBuilderImpl self() {
      return this;
    }

    public DataAndSuperBuilder build() {
      return new DataAndSuperBuilder(this);
    }
  }
}
