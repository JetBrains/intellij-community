import time
import multiprocessing


def run(name):
    print(name)

if __name__ == '__main__':
    multiprocessing.Process(target=run, args=("subprocess",)).start()
    while True:
        time.sleep(0.1)
