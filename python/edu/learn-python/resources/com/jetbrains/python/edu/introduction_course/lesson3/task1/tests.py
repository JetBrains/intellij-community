from test_helper import run_common_tests, passed, failed, import_task_file, get_task_windows


def test_value():
    file = import_task_file()
    if file.hello_world == "HelloWorld":
        failed("Use one-space string ' ' in concatenation.")
    else:
        passed()


def test_value_2():
    file = import_task_file()
    if file.hello_world == "Hello World":
        passed()
    else:
        failed("Use + operator")

def test_concat_used():
    window = get_task_windows()[0]
    if "hello" in window and "world" in window and "+" in window:
        passed()
    else:
        failed("Use previously defined variables and concatenation (+) to combine variables")

if __name__ == '__main__':
    run_common_tests()
    test_value()
    test_value_2()
    test_concat_used()

