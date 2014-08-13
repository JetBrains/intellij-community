from test_helper import run_common_tests, get_task_windows, passed, failed


def test_value():
    window = get_task_windows()[0]

    first = "The name of this ice-cream is \\\"Sweeet'n'Tasty\\\""
    second = 'The name of this ice-cream is "Sweeet\\\'n\\\'Tasty"'

    if first in window or second in window:
        passed()
    else:
        failed("Use backslash (\\) to escape quotes")


if __name__ == '__main__':
    run_common_tests()
    test_value()
