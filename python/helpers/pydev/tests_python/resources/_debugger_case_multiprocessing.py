import time
import multiprocessing


def run(name):
    print("argument: ", name)  # break 1 here


if __name__ == '__main__':
    multiprocessing.Process(target=run, args=("argument to run method",)).start()
    print('TEST SUCEEDED!')  # break 2 here
