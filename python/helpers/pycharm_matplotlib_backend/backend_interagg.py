import base64
import io

import matplotlib
import os
import sys

from matplotlib._pylab_helpers import Gcf
from matplotlib.backend_bases import FigureManagerBase, ShowBase
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.figure import Figure
from mpl_toolkits.mplot3d import Axes3D

from datalore.display import debug, display, SHOW_DEBUG_INFO

PY3 = sys.version_info[0] >= 3
IS_INTERACTIVE_PLOT = False
DEFAULT_FIGURE_WIDTH = 6.08
DEFAULT_FIGURE_HEIGHT = 4.56
STRING_3D = '3D'

if int(os.getenv("PYCHARM_INTERACTIVE_PLOTS", 0)):
    try:
        import mpld3
        IS_INTERACTIVE_PLOT = True
    except:
        pass

index = int(os.getenv("PYCHARM_MATPLOTLIB_INDEX", 0))

rcParams = matplotlib.rcParams


class Show(ShowBase):
    def __call__(self, **kwargs):
        debug("show() called with args %s" % kwargs)
        managers = Gcf.get_all_fig_managers()
        if not managers:
            debug("Error: Managers list in `Gcf.get_all_fig_managers()` is empty")
            return

        for manager in managers:
            manager.show(**kwargs)

    def mainloop(self):
        pass


show = Show()


# from pyplot API
def draw_if_interactive():
    if matplotlib.is_interactive():
        figManager = Gcf.get_active()
        if figManager is not None:
            figManager.canvas.show()
        else:
            debug("Error: Figure manager `Gcf.get_active()` is None")


# from pyplot API
def new_figure_manager(num, *args, **kwargs):
    FigureClass = kwargs.pop('FigureClass', Figure)
    figure = FigureClass(*args, **kwargs)
    return new_figure_manager_given_figure(num, figure)


# from pyplot API
def new_figure_manager_given_figure(num, figure):
    canvas = FigureCanvasInterAgg(figure)
    manager = FigureManagerInterAgg(canvas, num)
    return manager


# from pyplot API
class FigureCanvasInterAgg(FigureCanvasAgg):
    def __init__(self, figure):
        FigureCanvasAgg.__init__(self, figure)

    def show(self):
        FigureCanvasAgg.draw(self)

        buf = io.BytesIO()
        self.print_png(buf)
        buf.seek(0)
        buffer = buf.read()
        buf.close()

        if len(set(buffer)) <= 1:
            # do not plot empty
            debug("Error: Buffer FigureCanvasAgg.tostring_rgb() is empty")
            return

        html_string = ""
        for elem in self.figure.axes:
            if isinstance(elem, Axes3D):
                html_string = STRING_3D
                break

        # mpld3 doesn't support 3D plots
        if IS_INTERACTIVE_PLOT and not html_string:
            w, h = self.figure.get_figwidth(), self.figure.get_figheight()

            try:
                html_string = mpld3.fig_to_html(self.figure)
            except:
                pass

        render = self.get_renderer()
        width = int(render.width)
        debug("Image width: %d" % width)

        is_interactive = os.getenv("PYCHARM_MATPLOTLIB_INTERACTIVE", False)
        if is_interactive:
            debug("Using interactive mode (Run with Python Console)")
            debug("Plot index = %d" % index)
        else:
            debug("Using non-interactive mode (Run without Python Console)")
        plot_index = index if is_interactive else -1
        display(DisplayDataObject(plot_index, width, buffer, html_string))

    def draw(self):
        FigureCanvasAgg.draw(self)
        is_interactive = os.getenv("PYCHARM_MATPLOTLIB_INTERACTIVE", False)
        if is_interactive and matplotlib.is_interactive():
            self.show()
        else:
            debug("Error: calling draw() in non-interactive mode won't show a plot. Try to 'Run with Python Console'")


class FigureManagerInterAgg(FigureManagerBase):
    def __init__(self, canvas, num):
        FigureManagerBase.__init__(self, canvas, num)
        global index
        index += 1
        self.canvas = canvas
        self._num = num
        self._shown = False

    def show(self, **kwargs):
        self.canvas.show()
        Gcf.destroy(self._num)


class DisplayDataObject:
    def __init__(self, plot_index, width, image_bytes, html_string=""):
        self.plot_index = plot_index
        self.image_width = width
        self.image_bytes = image_bytes
        self.html_string = html_string
        self.session_id = os.getenv("PYCHARM_PLOTS_CONSOLE_ID") or str(os.getpid())

    def _repr_display_(self):
        image_bytes_base64 = base64.b64encode(self.image_bytes)
        if PY3:
            image_bytes_base64 = image_bytes_base64.decode()
        body = {
            'plot_index': self.plot_index,
            'image_width': self.image_width,
            'image_base64': image_bytes_base64,
            'html_string': self.html_string,
            'session_id': self.session_id,
        }
        return ('pycharm-matplotlib', body)


FigureCanvas = FigureCanvasAgg
FigureManager = FigureManagerInterAgg
