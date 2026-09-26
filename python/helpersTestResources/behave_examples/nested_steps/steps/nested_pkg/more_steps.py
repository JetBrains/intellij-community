# coding=utf-8
# This module registers steps, so it has to be reexecuted for the real run: the step
# registry is cleared after the dry run, and behave reaches this module through a
# plain import, which is a no-op while it stays in sys.modules (PY-86174).
from behave import given, then

from nested_pkg.counters import count_execution

count_execution('nested_pkg.more_steps')


@given("I am set up by a nested step")
def step_nested_given(context):
    pass


@then("the nested step module is loaded")
def step_nested_then(context):
    pass
