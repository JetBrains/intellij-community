// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package maven.dsl.groovy

class configurationContainer {

  /**
   *             Whether any configuration should be propagated
   * to child POMs. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>true</code>.
   */
  String inherited

  /**
   *
   *
   *             <p>The configuration as DOM object.</p>
   *             <p>By default, every element content is trimmed,
   * but starting with Maven 3.1.0, you can add
   *             <code>xml:space="preserve"</code> to elements
   * you want to preserve whitespace.</p>
   *             <p>You can control how child POMs inherit
   * configuration from parent POMs by adding
   * <code>combine.children</code>
   *             or <code>combine.self</code> attributes to the
   * children of the configuration element:</p>
   *             <ul>
   *             <li><code>combine.children</code>: available
   * values are <code>merge</code> (default) and
   * <code>append</code>,</li>
   *             <li><code>combine.self</code>: available values
   * are <code>merge</code> (default) and
   * <code>override</code>.</li>
   *             </ul>
   *             <p>See <a
   * href="http://maven.apache.org/pom.html#Plugins">POM
   * Reference documentation</a> and
   *             <a
   * href="http://plexus.codehaus.org/plexus-utils/apidocs/org/codehaus/plexus/util/xml/Xpp3DomUtils.html">Xpp3DomUtils</a>
   *             for more information.</p>
   *
   *
   */
  def configuration

  /**
   * <p>The configuration as DOM object.</p>
   *             <p>By default, every element content is trimmed,
   * but starting with Maven 3.1.0, you can add
   *             <code>xml:space="preserve"</code> to elements
   * you want to preserve whitespace.</p>
   *             <p>You can control how child POMs inherit
   * configuration from parent POMs by adding
   * <code>combine.children</code>
   *             or <code>combine.self</code> attributes to the
   * children of the configuration element:</p>
   *             <ul>
   *             <li><code>combine.children</code>: available
   * values are <code>merge</code> (default) and
   * <code>append</code>,</li>
   *             <li><code>combine.self</code>: available values
   * are <code>merge</code> (default) and
   * <code>override</code>.</li>
   *             </ul>
   *             <p>See <a
   * href="http://maven.apache.org/pom.html#Plugins">POM
   * Reference documentation</a> and
   *             <a
   * href="http://plexus.codehaus.org/plexus-utils/apidocs/org/codehaus/plexus/util/xml/Xpp3DomUtils.html">Xpp3DomUtils</a>
   *             for more information.</p>
   */
  void configuration(Object configuration) {}

  /**
   * <p>The configuration as DOM object.</p>
   *             <p>By default, every element content is trimmed,
   * but starting with Maven 3.1.0, you can add
   *             <code>xml:space="preserve"</code> to elements
   * you want to preserve whitespace.</p>
   *             <p>You can control how child POMs inherit
   * configuration from parent POMs by adding
   * <code>combine.children</code>
   *             or <code>combine.self</code> attributes to the
   * children of the configuration element:</p>
   *             <ul>
   *             <li><code>combine.children</code>: available
   * values are <code>merge</code> (default) and
   * <code>append</code>,</li>
   *             <li><code>combine.self</code>: available values
   * are <code>merge</code> (default) and
   * <code>override</code>.</li>
   *             </ul>
   *             <p>See <a
   * href="http://maven.apache.org/pom.html#Plugins">POM
   * Reference documentation</a> and
   *             <a
   * href="http://plexus.codehaus.org/plexus-utils/apidocs/org/codehaus/plexus/util/xml/Xpp3DomUtils.html">Xpp3DomUtils</a>
   *             for more information.</p>
   */
  void configuration(Closure closure) {}

  /**
   * Set whether any configuration should be propagated to child
   * POMs. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>true</code>.
   */
  void inherited(String inherited) {}

  /**
   * Set whether any configuration should be propagated to child
   * POMs. Note: While the type
   *             of this field is <code>String</code> for
   * technical reasons, the semantic type is actually
   *             <code>Boolean</code>. Default value is
   * <code>true</code>.
   */
  void inherited(boolean inherited) {}
}
