from __future__ import print_function
from multiprocessing import Pool
from time import sleep

if __name__ == '__main__':
    pool = Pool(4)
    pool.map(print, ['1', '2', '3'])
    pool.close()
    pool.join()
    sleep(1)
    print('Done')
