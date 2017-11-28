import imp
import os
import subprocess
import sys

USER_TESTS = "userTests"

TEST_FAILED = "FAILED"

TEST_PASSED = "PASSED"

INPUT = "input"
OUTPUT = "output"


def get_index(logical_name, full_name):
    logical_name_len = len(logical_name)
    if full_name[:logical_name_len] == logical_name:
        return int(full_name[logical_name_len])
    return -1


def process_user_tests(file_path):
    user_tests = []
    imp.load_source('user_file', file_path)
    user_tests_dir_path = os.path.abspath(os.path.join(file_path, os.pardir, USER_TESTS))
    user_test_files = os.listdir(user_tests_dir_path)
    for user_file in user_test_files:
        index = get_index(INPUT, user_file)
        if index == -1:
            continue
        output = OUTPUT + str(index)
        if output in user_test_files:
            input_path = os.path.abspath(os.path.join(user_tests_dir_path, user_file))
            output_path = os.path.abspath(os.path.join(user_tests_dir_path, output))
            user_tests.append((input_path, output_path, index))
    return sorted(user_tests, key=(lambda x: x[2]))


def run_user_test(python, executable_path):
    user_tests = process_user_tests(executable_path)
    for test in user_tests:
        input, output, index = test
        test_output = subprocess.check_output([python, executable_path, input])
        expected_output = open(output).read()
        test_status = TEST_PASSED if test_output == expected_output else TEST_FAILED
        print "TEST" + str(index) + " " + test_status
        print "OUTPUT:"
        print test_output + "\n"
        if test_status == TEST_FAILED:
            print "EXPECTED OUTPUT:"
            print expected_output + "\n"


if __name__ == "__main__":
    python = sys.argv[1]
    executable_path = sys.argv[2]
    run_user_test(python , executable_path)