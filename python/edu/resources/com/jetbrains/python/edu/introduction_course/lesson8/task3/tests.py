from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window():
    window = get_task_windows()[0]
    if "Car" in window:
        passed()
    else:
        failed("Create new car using Car()")


def test_window2():
    window = get_task_windows()[1]
    if "car1" in window and "color" in window:
        passed()
    else:
        failed("Change color using car1.color = ")


if __name__ == '__main__':
    run_common_tests()
    test_window()
    test_window2()