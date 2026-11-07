# coding=utf-8
# behave reaches this module through a plain import, not through its own loader
# (PY-86174).
from behave import given, then

from nested_pkg.counters import count_execution

count_execution('nested_pkg.more_steps')


@given("I am set up by a nested step")
def step_nested_given(context):
    pass


@then("the nested step module is loaded")
def step_nested_then(context):
    pass
