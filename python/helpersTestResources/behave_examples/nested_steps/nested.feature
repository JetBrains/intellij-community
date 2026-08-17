Feature: Step definitions in a nested package

  Scenario: Steps imported from a nested package are found
    Given I am set up by a nested step
    Then the nested step module is loaded
