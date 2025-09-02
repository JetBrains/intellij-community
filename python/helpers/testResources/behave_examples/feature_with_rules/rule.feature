Feature: Gherkin v6 Example -- with Rules
  Feature description line 1.

  Background: Feature.Background
    Given feature background step_1

  Rule: R1 (with Rule.Background)
    Rule R1 description line 1.

    Background: R1.Background
      Given rule R1 background step_1
      When  rule R1 background step_2

    Example: R1.Scenario_1
      When rule R1 scenario_1 step_1
      Then rule R1 scenario_1 step_2

    Example: R1.Scenario_2
      Given rule R1 scenario_2 step_1
      Then  rule R1 scenario_2 step_2

  Rule: R2 (without Rule.Background)
    Rule R2 description line 1.

    Example: R2.Scenario_1
      When rule R2 scenario_1 step_1
      Then rule R2 scenario_1 step_2


  Rule: R3 (with empty Rule.Background)
    Rule R3 description line 1.
    Rule R3 description line 2.

    Background: R3.EmptyBackground

    Scenario Template: R3.Scenario
      Given a person named "<name>"

      Examples:
        | name  |
        | Alice |
        | Bob   |