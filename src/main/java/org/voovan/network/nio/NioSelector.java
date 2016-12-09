package org.voovan.network.nio;

import org.voovan.network.ConnectModel;
import org.voovan.network.EventTrigger;
import org.voovan.network.MessageLoader;
import org.voovan.network.SocketContext;
import org.voovan.tools.TObject;
import org.voovan.tools.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 事件监听器
 * 
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class NioSelector {
	
	private Selector selector;
	private SocketContext socketContext;
	private NioSession session;
	
	/**
	 * 事件监听器构造
	 * @param selector   对象Selector
	 * @param socketContext socketContext 对象
	 */
	public NioSelector(Selector selector, SocketContext socketContext) {
		this.selector = selector;
		this.socketContext = socketContext;
		if (socketContext instanceof NioSocket){
			session = ((NioSocket)socketContext).getSession();
		}
	}

	/**
	 * 所有的事件均在这里触发
	 */
	public void eventChose() {
		//读取用的缓冲区
		ByteBuffer readTempBuffer = ByteBuffer.allocateDirect(1024);
		
		if (socketContext instanceof NioSocket) {
			// 连接完成onConnect事件触发
			EventTrigger.fireConnectThread(session);
		}
		
		// 事件循环
		try {
			while (socketContext != null && socketContext.isConnect()) {
				if (selector.select(1000) > 0) {
					Set<SelectionKey> selectionKeys = selector.selectedKeys();
					Iterator<SelectionKey> selectionKeyIterator = selectionKeys
							.iterator();
					while (selectionKeyIterator.hasNext()) {
						SelectionKey selectionKey = selectionKeyIterator.next();
						if (selectionKey.isValid()) {
							// 获取 socket 通道
							SocketChannel socketChannel = getSocketChannel(selectionKey);
							if (socketChannel.isOpen() && selectionKey.isValid()) {
								// 事件分发,包含时间 onRead onAccept
								
								switch (selectionKey.readyOps()) {
									// Server接受连接
									case SelectionKey.OP_ACCEPT: {
										NioServerSocket serverSocket = (NioServerSocket)socketContext;
										NioSocket socket = new NioSocket(serverSocket,socketChannel);
										session = socket.getSession();
										EventTrigger.fireAcceptThread(session);
										break;
									}
									// 有数据读取
									case SelectionKey.OP_READ: {
                                            int readSize = socketChannel.read(readTempBuffer);
											//判断连接是否关闭
											if(MessageLoader.isRemoteClosed(readSize,readTempBuffer) && session.isConnect()){
												session.close();
											}else if(readSize>0){
												readTempBuffer.flip();
												session.getByteBufferChannel().write(readTempBuffer);
												readTempBuffer.clear();
											}else if(readSize == -1){
												session.getMessageLoader().setStopType(MessageLoader.StopType.STREAM_END);
											}
											// 触发 onRead 事件,如果正在处理 onRead 事件则本次事件触发忽略
											EventTrigger.fireReceiveThread(session);
										break;
									}
									default: {
										Logger.debug("Nothing to do ,SelectionKey is:"
												+ selectionKey.readyOps());
									}
								}
								selectionKeyIterator.remove();
							}
						}
					}
				}
			}
		} catch (IOException e) {
			// 触发 onException 事件
			EventTrigger.fireExceptionThread(session, e);
		} finally{
			// 触发连接断开事件
			EventTrigger.fireDisconnectThread(session);
			//关闭线程池
			if(socketContext!=null &&
					(socketContext instanceof NioServerSocket
					||  socketContext.getConnectModel() == ConnectModel.CLIENT)){
				EventTrigger.shutdown();
			}
		}
	}

	/**
	 * 获取 socket 通道
	 * 
	 * @param selectionKey  当前 Selectionkey
	 * @return SocketChannel 对象
	 * @throws IOException  IO 异常
	 */
	public SocketChannel getSocketChannel(SelectionKey selectionKey)
			throws IOException {
		SocketChannel socketChannel = null;
		// 取得通道
		Object unknowChannel = selectionKey.channel();
		//  根据通道的类来判断类型是 ServerSocketChannel 还是 SocketChannel
		if (unknowChannel instanceof ServerSocketChannel) {
			ServerSocketChannel serverSocketChannel = TObject.cast( unknowChannel );
			socketChannel = serverSocketChannel.accept();
		} else if (unknowChannel instanceof SocketChannel) {
			socketChannel = TObject.cast( unknowChannel );
		}
		return socketChannel;
	}
}
