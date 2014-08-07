package org.jetbrains.io.fastCgi;

import com.intellij.util.Consumer;
import gnu.trove.TIntObjectHashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.jetbrains.io.Decoder;

import static org.jetbrains.io.fastCgi.FastCgiService.LOG;

public class FastCgiDecoder extends Decoder {
  private enum State {
    HEADER, CONTENT
  }

  private State state = State.HEADER;

  private enum ProtocolStatus {
    REQUEST_COMPLETE, CANT_MPX_CONN, OVERLOADED, UNKNOWN_ROLE
  }

  public static final class RecordType {
    public static final int END_REQUEST = 3;
    public static final int STDOUT = 6;
    public static final int STDERR = 7;
  }

  private int type;
  private int id;
  private int contentLength;
  private int paddingLength;

  private final TIntObjectHashMap<ByteBuf> dataBuffers = new TIntObjectHashMap<ByteBuf>();

  private final Consumer<String> errorOutputConsumer;

  public FastCgiDecoder(Consumer<String> errorOutputConsumer) {
    this.errorOutputConsumer = errorOutputConsumer;
  }

  @Override
  protected void messageReceived(ChannelHandlerContext context, ByteBuf input) throws Exception {
    while (true) {
      switch (state) {
        case HEADER: {
          if (paddingLength > 0) {
            if (input.readableBytes() >= paddingLength) {
              input.skipBytes(paddingLength);
              paddingLength = 0;
            }
            else {
              paddingLength -= input.readableBytes();
              input.skipBytes(input.readableBytes());
              input.release();
              return;
            }
          }

          ByteBuf buffer = getBufferIfSufficient(input, FastCgiConstants.HEADER_LENGTH, context);
          if (buffer == null) {
            input.release();
            return;
          }

          decodeHeader(buffer);
          state = State.CONTENT;
        }

        case CONTENT: {
          if (contentLength > 0) {
            ByteBuf buffer = getBufferIfSufficient(input, contentLength, context);
            if (buffer == null) {
              input.release();
              return;
            }

            FastCgiResponse response = readContent(buffer);
            if (response != null) {
              context.fireChannelRead(response);
            }
          }
          state = State.HEADER;
        }
      }
    }
  }

  private void decodeHeader(ByteBuf buffer) {
    buffer.skipBytes(1);
    type = buffer.readUnsignedByte();
    id = buffer.readUnsignedShort();
    contentLength = buffer.readUnsignedShort();
    paddingLength = buffer.readUnsignedByte();
    buffer.skipBytes(1);
  }

  private FastCgiResponse readContent(ByteBuf buffer) {
    switch (type) {
      case RecordType.END_REQUEST:
        int appStatus = buffer.readInt();
        int protocolStatus = buffer.readUnsignedByte();
        buffer.skipBytes(3);
        if (appStatus != 0 || protocolStatus != ProtocolStatus.REQUEST_COMPLETE.ordinal()) {
          LOG.warn("Protocol status " + protocolStatus);
          dataBuffers.remove(id);
          return new FastCgiResponse(id, null);
        }
        else if (protocolStatus == ProtocolStatus.REQUEST_COMPLETE.ordinal()) {
          return new FastCgiResponse(id, dataBuffers.remove(id));
        }
        break;

      case RecordType.STDOUT:
        ByteBuf data = dataBuffers.get(id);
        ByteBuf sliced = buffer.slice(buffer.readerIndex(), contentLength);
        if (data == null) {
          dataBuffers.put(id, sliced);
        }
        else if (data instanceof CompositeByteBuf) {
          ((CompositeByteBuf)data).addComponent(sliced);
          data.writerIndex(data.writerIndex() + sliced.readableBytes());
        }
        else {
          dataBuffers.put(id, Unpooled.wrappedBuffer(data, sliced));
        }
        sliced.retain();
        buffer.skipBytes(contentLength);
        break;

      case RecordType.STDERR:
        try {
          errorOutputConsumer.consume(buffer.toString(buffer.readerIndex(), contentLength, CharsetUtil.UTF_8));
        }
        catch (Throwable e) {
          LOG.error(e);
        }
        buffer.skipBytes(contentLength);
        break;

      default:
        LOG.error("Unknown type " + type);
        break;
    }
    return null;
  }
}