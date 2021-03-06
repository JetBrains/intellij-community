import os

if __name__ == '__main__':
    pid = os.fork()
    if pid == 0:
        print('TEST SUCEEDED!')  # break here
