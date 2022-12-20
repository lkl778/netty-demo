package com.lkl.netty.c4;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import static com.lkl.netty.utils.ByteBufferUtil.debugRead;

/**
 * @author likelong
 * @date 2022/12/19
 */
@Slf4j
public class SelectServer {

    public static void main(String[] args) {

        // 1、创建选择器，管理多个 channel
        try (
                Selector selector = Selector.open();
                ServerSocketChannel ssc = ServerSocketChannel.open()
        ) {

            // channel 必须设置为非阻塞，否咋会报异常
            ssc.configureBlocking(false);
            // 2、建立 selector 和 channel 的联系（注册）
            // SelectionKey 就是将来事件发生后，通过它知道事件和哪个channel的事件
            SelectionKey sscKey = ssc.register(selector, 0, null);
            // 该 key 只关注 accept 事件
            sscKey.interestOps(SelectionKey.OP_ACCEPT);
            log.debug("register key: {}", sscKey);

            ssc.bind(new InetSocketAddress(8080));
            while (true) {
                // 3、select 方法，没有事件发送，线程阻塞，有事件发送，线程才会恢复运行
                // select 在事件未处理时，它不会阻塞，事件已处理或者取消了其会阻塞
                // 事件发生后，要么处理要么取消，不能置之不理
                selector.select();

                // 4、处理事件，selectedKeys 内部包含了所有发生的事件
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    log.debug("key: {}", key);

                    // 5、可能有不同事件类型，区分事件类型
                    if (key.isAcceptable()) { // accept
                        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                        SocketChannel sc = channel.accept();
                        sc.configureBlocking(false);
                        SelectionKey scKey = sc.register(selector, 0, null);
                        scKey.interestOps(SelectionKey.OP_READ);
                        log.debug("{}", sc);
                    } else if (key.isReadable()) {
                        try {
                            SocketChannel channel = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(16);
                            int read = channel.read(buffer);// 强制关闭客户端，这里会报异常
                            // 如果是正常断开，read值为-1
                            if (read == -1) {
                                key.cancel(); // 此时任然要取消 key
                            } else {
                                buffer.flip();
                                debugRead(buffer);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            // 异常断开
                            key.cancel(); // 因为客户端断开了，因此需要将 key 取消（从 Selector 的 keys 集合中真正删除 key）
                        }
                    }

                    // 事件处理完后，集合（selectedKeys）中的对应的key不会自动删除，需要我们手动删除，防止事件再次被处理从而引发错误
                    iterator.remove();

                    // key.cancel();
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
