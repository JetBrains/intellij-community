from hamcrest import (
    assert_that,
    # Object Matchers
    equal_to, has_length, has_string, has_property, instance_of, none, not_none,
    same_instance,
    # Number Matchers
    close_to, greater_than, greater_than_or_equal_to, less_than, less_than_or_equal_to,
    # Text Matchers
    contains_string, ends_with, equal_to_ignoring_case, equal_to_ignoring_whitespace,
    starts_with, string_contains_in_order,
    # Logical Matchers
    all_of, any_of, anything, is_not,
    # Sequence Matchers
    contains, contains_inanyorder, has_item, has_items, is_in, only_contains,
    # Dictionary Matchers
    has_entries, has_entry, has_key, has_value,
    # Decorator Matchers
    described_as, is_)
