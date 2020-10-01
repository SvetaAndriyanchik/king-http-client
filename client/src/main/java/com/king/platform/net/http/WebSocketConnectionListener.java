package com.king.platform.net.http;

public interface WebSocketConnectionListener {
	/**
	 * Callback for when the web-socket is connected
	 *
	 * @param connection the current connection to the web-socket
	 */
	void onConnect(WebSocketConnection connection);

	/**
	 * Callback for when there is some exception with the web-socket client
	 *
	 * @param throwable the exception
	 */
	void onError(Throwable throwable);

	/**
	 * Callback for when the web-socket has disconnected from the server.
	 */
	void onDisconnect();

	/**
	 * Callback for when the client has received a close frame from the server.
	 * There is no need to echo close frames back to the server if {@link ConfKeys#WEB_SOCKET_AUTO_CLOSE_FRAME} or
	 * {@link HttpClientWebSocketRequestBuilder#autoCloseFrame(boolean)} is true.
	 *
	 * @param code   the close code
	 * @param reason the close reason
	 */
	void onCloseFrame(int code, String reason);

	/**
	 * Callback for when the server has sent an ping frame to the client.
	 * There is no need to echo back an pong frame if {@link ConfKeys#WEB_SOCKET_AUTO_PONG} or {@link HttpClientWebSocketRequestBuilder#autoPong(boolean)}
	 * is true.
	 *
	 * @param payload the payload
	 */
	default void onPingFrame(byte[] payload) {

	}

	/**
	 * Callback for when the server has sent an pong back to the client.
	 *
	 * @param payload the payload
	 */
	default void onPongFrame(byte[] payload) {

	}

}
