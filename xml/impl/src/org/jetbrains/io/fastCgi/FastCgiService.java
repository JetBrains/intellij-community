package org.jetbrains.io.fastCgi;

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.ChannelExceptionHandler;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.io.Responses;

import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// todo send FCGI_ABORT_REQUEST if client channel disconnected
public abstract class FastCgiService implements Disposable {
  static final Logger LOG = Logger.getInstance(FastCgiService.class);

  protected final Project project;

  private final AtomicInteger requestIdCounter = new AtomicInteger();
  private final StripedLockIntObjectConcurrentHashMap<Channel> requests = new StripedLockIntObjectConcurrentHashMap<Channel>();

  private volatile Channel fastCgiChannel;

  protected final AsyncValueLoader<OSProcessHandler> processHandler = new AsyncValueLoader<OSProcessHandler>() {
    @Override
    protected boolean isCancelOnReject() {
      return true;
    }

    @Override
    protected void load(@NotNull final AsyncResult<OSProcessHandler> result) throws IOException {
      final int port = NetUtils.findAvailableSocketPort();
      final OSProcessHandler processHandler = createProcessHandler(project, port);
      if (processHandler == null) {
        result.setRejected();
        return;
      }

      result.doWhenRejected(new Runnable() {
        @Override
        public void run() {
          processHandler.destroyProcess();
        }
      });

      final MyProcessAdapter processListener = new MyProcessAdapter();
      processHandler.addProcessListener(processListener);
      processHandler.startNotify();

      if (result.isRejected()) {
        return;
      }

      JobScheduler.getScheduler().schedule(new Runnable() {
        @Override
        public void run() {
          if (result.isRejected()) {
            return;
          }

          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              if (!result.isRejected()) {
                try {
                  connectToProcess(result, port, processHandler, processListener);
                }
                catch (Throwable e) {
                  result.setRejected();
                  LOG.error(e);
                }
              }
            }
          });
        }
      }, NettyUtil.MIN_START_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void disposeResult(@NotNull OSProcessHandler processHandler) {
      try {
        Channel currentFastCgiChannel = fastCgiChannel;
        if (currentFastCgiChannel != null) {
          fastCgiChannel = null;
          NettyUtil.closeAndReleaseFactory(currentFastCgiChannel);
        }
        processHandler.destroyProcess();
      }
      finally {
        requestIdCounter.set(0);
        if (!requests.isEmpty()) {
          List<Channel> waitingClients = ContainerUtil.toList(requests.elements());
          requests.clear();
          for (Channel channel : waitingClients) {
            try {
              if (channel.isActive()) {
                Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
              }
            }
            catch (Throwable e) {
              NettyUtil.log(e, LOG);
            }
          }
        }
      }
    }
  };

  private ConsoleView console;

  protected FastCgiService(Project project) {
    this.project = project;
  }

  protected abstract OSProcessHandler createProcessHandler(Project project, int port);

  private void connectToProcess(final AsyncResult<OSProcessHandler> asyncResult, final int port, final OSProcessHandler processHandler, final Consumer<String> errorOutputConsumer) {
    Bootstrap bootstrap = NettyUtil.oioClientBootstrap();
    final FastCgiChannelHandler fastCgiChannelHandler = new FastCgiChannelHandler(requests);
    bootstrap.handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(new FastCgiDecoder(errorOutputConsumer), fastCgiChannelHandler, ChannelExceptionHandler.getInstance());
      }
    });
    fastCgiChannel = NettyUtil.connectClient(bootstrap, new InetSocketAddress(NetUtils.getLoopbackAddress(), port), asyncResult);
    if (fastCgiChannel != null) {
      asyncResult.setDone(processHandler);
    }
  }

  public void send(final FastCgiRequest fastCgiRequest, final ByteBuf content) {
    content.retain();

    if (processHandler.has()) {
      fastCgiRequest.writeToServerChannel(content, fastCgiChannel);
    }
    else {
      processHandler.get().doWhenDone(new Runnable() {
        @Override
        public void run() {
          fastCgiRequest.writeToServerChannel(content, fastCgiChannel);
        }
      }).doWhenRejected(new Runnable() {
        @Override
        public void run() {
          content.release();
          Channel channel = requests.get(fastCgiRequest.requestId);
          if (channel != null && channel.isActive()) {
            Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
          }
        }
      });
    }
  }

  public int allocateRequestId(Channel channel) {
    int requestId = requestIdCounter.getAndIncrement();
    if (requestId >= Short.MAX_VALUE) {
      requestIdCounter.set(0);
      requestId = requestIdCounter.getAndDecrement();
    }
    requests.put(requestId, channel);
    return requestId;
  }

  @Override
  public void dispose() {
    processHandler.reset();
  }

  protected abstract void buildConsole(@NotNull TextConsoleBuilder consoleBuilder);

  @NotNull
  protected abstract String getConsoleToolWindowId();

  @NotNull
  protected abstract Icon getConsoleToolWindowIcon();

  private final class MyProcessAdapter extends ProcessAdapter implements Consumer<String> {
    private void createConsole() {
      TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
      buildConsole(consoleBuilder);
      console = consoleBuilder.getConsole();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(getConsoleToolWindowId(), false, ToolWindowAnchor.BOTTOM, project, true);
          toolWindow.setIcon(getConsoleToolWindowIcon());
          toolWindow.getContentManager().addContent(ContentFactory.SERVICE.getInstance().createContent(console.getComponent(), "", false));
        }
      }, project.getDisposed());
    }

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType));
    }

    private void print(String text, ConsoleViewContentType contentType) {
      if (console == null) {
        createConsole();
      }
      console.print(text, contentType);
    }

    @Override
    public void processTerminated(ProcessEvent event) {
      processHandler.reset();
      print(getConsoleToolWindowId() + " terminated\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    @Override
    public void consume(String message) {
      print(message, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }
}