import lombok.Data;

@Data(staticConstructor = "of")
class Entity {
  private final String field;
}