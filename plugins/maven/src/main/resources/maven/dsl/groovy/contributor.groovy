package maven.dsl.groovy

class contributor {
  /**
   * The full name of the contributor.
   */
  String name;

  /**
   * The email address of the contributor.
   */
  String email;

  /**
   * The URL for the homepage of the contributor.
   */
  String url;

  /**
   * The organization to which the contributor belongs.
   */
  String organization;

  /**
   * The URL of the organization.
   */
  String organizationUrl;

  /**
   * Field roles.
   */
  List<String> roles;

  /**
   *
   *
   *               The timezone the contributor is in. Typically,
   * this is a number in the range
   *               <a
   * href="http://en.wikipedia.org/wiki/UTC%E2%88%9212:00">-12</a>
   * to <a
   * href="http://en.wikipedia.org/wiki/UTC%2B14:00">+14</a>
   *               or a valid time zone id like
   * "America/Montreal" (UTC-05:00) or "Europe/Paris"
   * (UTC+01:00).
   *
   *
   */
  String timezone

  /**
   * Field properties.
   */
  Map<String,String> properties

  void properties(Map<String,String> properties) {}

  /**
   * The email address of the contributor.
   */
  void email(String email) {}

  /**
   * The full name of the contributor.
   */
  void name(String name) {}

  /**
   * The organization to which the contributor belongs.
   */
  void organization(String organization) {}

  /**
   * The URL of the organization.
   */
  void organizationUrl(String organizationUrl) {}

  /**
   * Set the timezone the contributor is in. Typically, this is a
   * number in the range
   *               <a
   * href="http://en.wikipedia.org/wiki/UTC%E2%88%9212:00">-12</a>
   * to <a
   * href="http://en.wikipedia.org/wiki/UTC%2B14:00">+14</a>
   *               or a valid time zone id like
   * "America/Montreal" (UTC-05:00) or "Europe/Paris"
   * (UTC+01:00).
   */
  void timezone(String timezone) {}

  /**
   * Set the URL for the homepage of the contributor.
   */
  void url(String url) {}
}
