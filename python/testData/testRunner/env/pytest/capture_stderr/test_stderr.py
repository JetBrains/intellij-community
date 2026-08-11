import sys

def test_pass_with_stderr():
    sys.stderr.write("stderr_from_passing_test\n")
    assert True

def test_fail_with_stderr():
    sys.stderr.write("stderr_from_failing_test\n")
    assert False, "intentional failure"
