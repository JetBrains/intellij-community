class AccessorsOuter {
  private String fTest;
  private String zTest2;
  class AccessorsInner1 {
    private String zTest3;
    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public AccessorsOuter.AccessorsInner1 setTest3(final String zTest3) {
      this.zTest3 = zTest3;
      return this;
    }
  }
  class AccessorsInner2 {
    private String fTest4;
    @java.lang.SuppressWarnings("all")
    public void setTest4(final String fTest4) {
      this.fTest4 = fTest4;
    }
  }
  /**
   * @return {@code this}.
   */
  @java.lang.SuppressWarnings("all")
  public AccessorsOuter setTest(final String fTest) {
    this.fTest = fTest;
    return this;
  }
  /**
   * @return {@code this}.
   */
  @java.lang.SuppressWarnings("all")
  public AccessorsOuter setTest2(final String zTest2) {
    this.zTest2 = zTest2;
    return this;
  }
}