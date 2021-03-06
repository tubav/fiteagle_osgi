package org.fiteagle.dm.fff13;

import java.io.IOException;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.naming.NamingException;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.fiteagle.boundary.MessageBus;
import org.fiteagle.boundary.MessageBusApplicationServerFactory;
import org.json.JSONException;
import org.json.JSONObject;

@ServerEndpoint("/status/{syntax}")
public class StatusWebSocket implements MessageListener {
	private static final String JSON_FIELD_STATUS = "status";
	private static final String JSON_FIELD_UID = "uid";
	private static final Logger LOGGER = Logger.getLogger(StatusWebSocket.class
			.getName());
	private Session socket;
	private final MessageBus bus;

	public StatusWebSocket() throws NamingException, JMSException {
		this(MessageBusApplicationServerFactory.createMessageBus());
	}

	public StatusWebSocket(final MessageBus bus) throws JMSException {
		this.bus = bus;
		final String filter = MessageBus
				.getFilter(MessageBus.Type.STATUSNOTIFICATION);
		this.bus.getConsumer(filter).setMessageListener(this);
	}

	@OnClose
	public void onClose(final CloseReason reason) throws JMSException {
		StatusWebSocket.LOGGER.log(Level.INFO, "Closing a WebSocket due to "
				+ reason.getReasonPhrase());
	}

	@OnOpen
	public void onOpen(final Session websocket,
			@PathParam("syntax") final String syntax) throws JMSException {
		StatusWebSocket.LOGGER.log(Level.INFO,
				"WebSocket opened: " + websocket.getId());
		StatusWebSocket.LOGGER.log(Level.INFO,
				"Query String: " + websocket.getQueryString());
		StatusWebSocket.LOGGER.log(Level.INFO,
				"URI: " + websocket.getRequestURI());
		StatusWebSocket.LOGGER.log(Level.INFO,
				"Path: " + websocket.getPathParameters());
		StatusWebSocket.LOGGER.log(Level.INFO, "Syntax: " + syntax);

		this.socket = websocket;

		this.queryData();
		// this.simulateChange();
	}

	private void queryData() throws JMSException {
		TextMessage message = this.bus.getSession().createTextMessage();
		message.setStringProperty(MessageBus.Property.TYPE.toString(),
				MessageBus.Type.STATUSQUERY.toString());
		this.bus.getProducer().send(message);
	}

	private void sendStatusUpdate(final String uid, final String status)
			throws JSONException, IOException {
		final JSONObject json = new JSONObject();
		json.put(StatusWebSocket.JSON_FIELD_UID, uid);
		json.put(StatusWebSocket.JSON_FIELD_STATUS, status);
		final String result = json.toString();
		StatusWebSocket.LOGGER.log(Level.INFO, "Sending: " + result);
		this.socket.getBasicRemote().sendText(result);
	}

	@Override
	public void onMessage(final Message message) {
		try {
			final TextMessage textMessage = (TextMessage) message;
			final String uid = textMessage
					.getStringProperty(MessageBus.Property.UID.toString());
			final String status = textMessage.getText();
			this.sendStatusUpdate(uid, status);
		} catch (JMSException | JSONException | IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage());
		}
	}

	private void simulateChange() {
		final Timer timer = new Timer();
		timer.schedule(new SimulateChange("Samsung S4",
				MessageBus.Status.UNKNOWN), 4000);
		timer.schedule(
				new SimulateChange("Samsung S4", MessageBus.Status.ERROR), 8000);
		timer.schedule(new SimulateChange("Samsung S4",
				MessageBus.Status.WARNING), 13000);
		timer.schedule(new SimulateChange("Samsung S4", MessageBus.Status.UP),
				19000);
	}

	private class SimulateChange extends TimerTask {
		private final MessageBus.Status status;
		private final String uid;

		public SimulateChange(final String uid, final MessageBus.Status status) {
			this.uid = uid;
			this.status = status;
		}

		@Override
		public void run() {
			try {
				final TextMessage message = StatusWebSocket.this.bus
						.getSession().createTextMessage();
				message.setStringProperty(MessageBus.Property.TYPE.toString(),
						MessageBus.Type.STATUSNOTIFICATION.toString());
				message.setStringProperty(MessageBus.Property.UID.toString(),
						this.uid);
				message.setText(String.valueOf(this.status));
				StatusWebSocket.this.bus.getProducer().send(message);
			} catch (final JMSException e) {
				StatusWebSocket.LOGGER.log(Level.SEVERE, e.getCause()
						.toString());
			}
		}
	}
}
