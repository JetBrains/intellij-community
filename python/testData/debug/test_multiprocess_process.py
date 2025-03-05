import time
import multiprocessing


def run(name):
    print(name)

if __name__ == '__main__':
    p = multiprocessing.Process(target=run, args=("subprocess",))
    p.start()
    p.join()