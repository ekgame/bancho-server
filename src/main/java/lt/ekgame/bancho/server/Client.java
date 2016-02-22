package lt.ekgame.bancho.server;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import lt.ekgame.bancho.api.packets.ByteDataOutputStream;
import lt.ekgame.bancho.api.packets.Packet;
import lt.ekgame.bancho.api.packets.Packets;
import lt.ekgame.bancho.api.packets.client.PacketIdle;
import lt.ekgame.bancho.api.packets.server.PacketChat;

public class Client {
	
	private Queue<Packet> outgoingPackets = new LinkedList<>();
	
	private String token, username;
	private int userId;
	private BanchoServer server;
	
	public Client(String token, String username, int userId, BanchoServer server) {
		this.token = token;
		this.server = server;
		this.username = username;
		this.userId = userId;
	}
	
	public boolean validateToken(String token) {
		return token.equals(this.token);
	}
	
	public String getUsername() {
		return username;
	}

	public void sendPacket(Packet packet) {
		outgoingPackets.add(packet);
	}
	
	public void sendData(ByteDataOutputStream stream) throws IOException {
		while (!outgoingPackets.isEmpty()) {
			Packet packet = outgoingPackets.poll();
			/*System.out.println(username + " writing " + packet.getClass());
			if (packet instanceof PacketChat) {
				PacketChat pc = (PacketChat) packet;
				System.out.println("chat: " + pc.channel + ", " + pc.sender + ", " + pc.message);
			}*/
			
			if (!(packet instanceof PacketIdle))
				System.out.printf(">> out: %s\n", packet.getClass());
				
			short id = (short) Packets.getId(packet);
			if (id == -1) {
				System.err.println("Can't find ID for " + packet.getClass());
				continue;
			}
			stream.writeShort(id);
			stream.writeByte((byte) 0);
			stream.writeInt(packet.size(server));
			packet.write(stream);
		}
	}

	public void sendChatPacket(Channel channel, Client user, String message) {
		String c = channel == null ? user.username : channel.getName();
		String u = channel == null ? "" : user.username;
		this.sendPacket(new PacketChat(u, message, c, userId));
	}
	

	public void sendMessage(Channel target, String message) {
		target.sendMessage(this, message);
	}
	
	public void sendMessage(Client target, String message) {
		target.sendChatPacket(null, this, message);
	}

	public int getId() {
		return userId;
	}
}
