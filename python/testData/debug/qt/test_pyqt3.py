from PyQt5 import QtCore
import sys


class Runnable(QtCore.QRunnable):

    def run(self):
        app = QtCore.QCoreApplication.instance()
        for i in range(3):
            print("ping %d" % i)
        app.quit()


app = QtCore.QCoreApplication([])
runnable = Runnable()
QtCore.QThreadPool.globalInstance().start(runnable)
sys.exit(app.exec_())


