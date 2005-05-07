public class WrongParameter {
  public boolean resolveAction(@NotNull Object action, @NotNull Object combatant, @NotNull Object userInputProvider) {
    return false;
  }

  public void foo() {
    resolveAction(
      new Object(),
      new Object(),
      null); // Last parameter should be highlighted
  }
}