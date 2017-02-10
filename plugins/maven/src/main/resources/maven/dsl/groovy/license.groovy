package maven.dsl.groovy

class license {
  /**
   * The full legal name of the license.
   */
  String name

  /**
   * The official url for the license text.
   */
  String url

  /**
   *
   *
   *             The primary method by which this project may be
   * distributed.
   *             <dl>
   *               <dt>repo</dt>
   *               <dd>may be downloaded from the Maven
   * repository</dd>
   *               <dt>manual</dt>
   *               <dd>user must manually download and install
   * the dependency.</dd>
   *             </dl>
   *
   *
   */
  String distribution

  /**
   * Addendum information pertaining to this license.
   */
  String comments

  /**
   * Set addendum information pertaining to this license.
   */
  void comments(String comments) {}

  /**
   * Set the primary method by which this project may be
   * distributed.
   *             <dl>
   *               <dt>repo</dt>
   *               <dd>may be downloaded from the Maven
   * repository</dd>
   *               <dt>manual</dt>
   *               <dd>user must manually download and install
   * the dependency.</dd>
   *             </dl>
   */
  void distribution(String distribution) {}

  /**
   * Set the full legal name of the license.
   */
  void name(String name) {}

  /**
   * Set the official url for the license text.
   */
  void url(String url) {}
}
