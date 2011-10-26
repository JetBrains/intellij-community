class Entity {
  private final String field;

  private Entity(String fieldValue) {
    field = fieldValue;
  }

  public static Entity of(String fieldValue) {
    return new Entity(fieldValue);
  }
}