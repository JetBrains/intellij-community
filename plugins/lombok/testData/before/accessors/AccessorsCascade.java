@lombok.experimental.Accessors(chain=true, prefix="f")
class AccessorsOuter {
  @lombok.Setter
  private String fTest;

  @lombok.experimental.Accessors(prefix="z")
  @lombok.Setter
  private String zTest2;

  class AccessorsInner1 {
    @lombok.experimental.Accessors(prefix="z")
    @lombok.Setter
    private String zTest3;
  }

  @lombok.experimental.Accessors(chain=false)
  class AccessorsInner2 {
    @lombok.Setter
    private String fTest4;
  }
}