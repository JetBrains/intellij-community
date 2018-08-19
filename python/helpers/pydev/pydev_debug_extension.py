def target_func(comm, msg):
    # comm is the kernel Comm instance
    # msg is the comm_open message

    # Register handler for later messages
    @comm.on_msg
    def _recv(msg):
        msg_data = msg['content']['data']
        comm.send({'answer': msg_data})
        # Use msg['content']['data'] for the data in the message

    # Send data to the frontend
    comm.send({'foo': 5})


def load_ipython_extension(shell):
    shell.kernel.comm_manager.register_target('my_comm_target', target_func)


def unload_ipython_extension(shell):
    pass
