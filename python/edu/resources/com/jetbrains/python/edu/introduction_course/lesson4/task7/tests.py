from test_helper import run_common_tests, failed, passed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "grocery_dict" in window and " in " in window and "fish" in window:
        passed()
    else:
        failed("Use in keyword")

if __name__ == '__main__':
    run_common_tests("Use in keyword")
    test_window()