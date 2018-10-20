import sys
import time

if __name__ == '__main__':
    if len(sys.argv) > 0:
        started_message = sys.argv[1]
        print(started_message)

    while True:
        time.sleep(100)
