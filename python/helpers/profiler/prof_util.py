__author__ = 'traff'

import threading

class ProfDaemonThread(threading.Thread):
    def __init__(self):
        super(ProfDaemonThread, self).__init__()
        self.setDaemon(True)
        self.killReceived = False

    def run(self):
        self.OnRun()

    def OnRun(self):
        pass