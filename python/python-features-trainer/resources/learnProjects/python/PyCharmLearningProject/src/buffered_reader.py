
class BufferedReader:
    def __init__(self, input_stream, max_buffer_size=1024):
        self.input_stream = input_stream
        self.max_buffer_size = max_buffer_size

    buffer = []

    def read(self, n=1):
        """
        :param n: number of bytes to return
        :return: n bytes from buffer or try read max_buffer_size bytes from input stream and return n bytes
        """
        pass

    def read_line(self):
        """
        :return: one line from input stream as string
        """
        pass

    def lines(self):
        """
        :return: all lines from input stream as list of strings
        """
        pass
