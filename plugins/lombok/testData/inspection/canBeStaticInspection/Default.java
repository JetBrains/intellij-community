import lombok.Builder;

@Builder
class Main {
  @Builder.Default
  private final int y = 3;

  void test() {
    System.out.println(y);
  }

  public void main(String[] args) {
    System.out.println(y);
  }
}