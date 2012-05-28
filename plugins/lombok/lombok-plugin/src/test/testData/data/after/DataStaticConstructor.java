class Entity {
  private final String field;

  private Entity(String fieldValue) {
    field = fieldValue;
  }

  public static Entity of(String fieldValue) {
    return new Entity(fieldValue);
  }

  public String getField() {
    return field;
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }

  public boolean canEqual(final Object other) {
    return other instanceof Data1;
  }
}