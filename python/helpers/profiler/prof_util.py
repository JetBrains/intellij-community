__author__ = 'traff'

import threading
import os

class ProfDaemonThread(threading.Thread):
    def __init__(self):
        super(ProfDaemonThread, self).__init__()
        self.setDaemon(True)
        self.killReceived = False

    def run(self):
        self.OnRun()

    def OnRun(self):
        pass

def generate_snapshot_filepath():
    basepath = os.getenv('PYCHARM_SNAPSHOT_PATH')
    n = 0
    path = basepath + '.pstat'
    while os.path.exists(path):
        n+=1
        path = basepath + (str(n) if n>0 else '') + '.pstat'

    return path

