import matplotlib
import os
import socket
import struct
from matplotlib._pylab_helpers import Gcf
from matplotlib.backend_bases import FigureManagerBase, ShowBase
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.figure import Figure

HOST = 'localhost'
PORT = os.getenv("PYCHARM_MATPLOTLIB_PORT")
PORT = int(PORT) if PORT is not None else None
PORT = PORT if PORT != -1 else None

rcParams = matplotlib.rcParams
verbose = matplotlib.verbose


class Show(ShowBase):
    def __call__(self, **kwargs):
        managers = Gcf.get_all_fig_managers()
        if not managers:
            return

        for manager in managers:
            manager.show(**kwargs)

    def mainloop(self):
        pass


show = Show()


# from pyplot API
def new_figure_manager(num, *args, **kwargs):
    FigureClass = kwargs.pop('FigureClass', Figure)
    figure = FigureClass(*args, **kwargs)
    return new_figure_manager_given_figure(num, figure)


# from pyplot API
def new_figure_manager_given_figure(num, figure):
    canvas = FigureCanvasInterAgg(figure)
    manager = FigureManagerInterAgg(canvas, num)
    if matplotlib.is_interactive():
        manager.show()
    return manager


# from pyplot API
class FigureCanvasInterAgg(FigureCanvasAgg):
    def __init__(self, figure):
        FigureCanvasAgg.__init__(self, figure)

    def show(self):
        FigureCanvasAgg.draw(self)
        if PORT is None:
            return

        if matplotlib.__version__ < '1.2':
            buffer = self.tostring_rgb(0, 0)
        else:
            buffer = self.tostring_rgb()

        render = self.get_renderer()
        width, height = int(render.width), int(render.height) # pass to the socket

        try:
            sock = socket.socket()
            sock.connect((HOST, PORT))
            sock.send(struct.pack('>i', width))
            sock.send(struct.pack('>i', len(buffer)))
            sock.send(buffer)
        except ConnectionRefusedError as _:
            # nothing bad. It just means, that our tool window doesn't run yet
            pass


class FigureManagerInterAgg(FigureManagerBase):
    def __init__(self, canvas, num):
        FigureManagerBase.__init__(self, canvas, num)
        self.canvas = canvas
        self._num = num
        self._shown = False

    def show(self, **kwargs):
        self.canvas.show()
        Gcf.destroy(self._num)
