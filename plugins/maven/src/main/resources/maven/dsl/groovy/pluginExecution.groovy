package maven.dsl.groovy

class pluginExecution {
  /**
   * The identifier of this execution for labelling the goals
   * during the build,
   *             and for matching executions to merge during
   * inheritance and profile injection.
   */
  String id = "default";

  /**
   * The build lifecycle phase to bind the goals in this
   * execution to. If omitted,
   *             the goals will be bound to the default phase
   * specified by the plugin.
   */
  String phase;

  /**
   *
   *
   *             The priority of this execution compared to other
   * executions which are bound to the same phase.
   *             <strong>Warning:</strong> This is an internal
   * utility property that is only public for technical reasons,
   *             it is not part of the public API. In particular,
   * this property can be changed or deleted without prior
   *             notice.
   *
   *
   */
  int priority = 0;

  /**
   * Field goals.
   */
  List<String> goals;

  /**
   * Goals to execute with the given configuration.
   */
  void goals(List<String> goals) {}

  /**
   * Goals to execute with the given configuration.
   */
  void goals(String... goals) {}

  /**
   * Goals to execute with the given configuration.
   */
  void goals(Closure closure) {}

  /**
   * The identifier of this execution for labelling the goals
   * during the build,
   *             and for matching executions to merge during
   * inheritance and profile injection.
   */
  void id(String id) {}

  /**
   * The build lifecycle phase to bind the goals in this
   * execution to. If omitted,
   *             the goals will be bound to the default phase
   * specified by the plugin.
   */
  void phase(String phase) {}

  /**
   * The priority of this execution compared to other
   * executions which are bound to the same phase.
   *             <strong>Warning:</strong> This is an internal
   * utility property that is only public for technical reasons,
   *             it is not part of the public API. In particular,
   * this property can be changed or deleted without prior
   *             notice.
   */
  void priority(int priority) {}
}
