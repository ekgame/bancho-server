package lt.ekgame.bancho.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import lt.ekgame.bancho.api.Bancho;
import lt.ekgame.bancho.api.packets.ByteDataInputStream;
import lt.ekgame.bancho.api.packets.ByteDataOutputStream;
import lt.ekgame.bancho.api.packets.Packet;
import lt.ekgame.bancho.api.packets.Packets;
import lt.ekgame.bancho.api.packets.client.PacketCreateRoom;
import lt.ekgame.bancho.api.packets.client.PacketIdle;
import lt.ekgame.bancho.api.packets.client.PacketJoinChannel;
import lt.ekgame.bancho.api.packets.client.PacketLeaveChannel;
import lt.ekgame.bancho.api.packets.client.PacketSendMessageUser;
import lt.ekgame.bancho.api.packets.server.PacketUserId;
import lt.ekgame.bancho.api.packets.server.PacketUserInfo;
import lt.ekgame.bancho.api.packets.server.PacketChatRoomInfo;
import lt.ekgame.bancho.api.packets.server.PacketRoomBecameHost;
import lt.ekgame.bancho.api.packets.server.PacketProtocolVersion;
import lt.ekgame.bancho.api.packets.server.PacketRoomJoined;
import lt.ekgame.bancho.api.packets.server.PacketTimeSync;
import lt.ekgame.bancho.api.packets.server.PacketRoomUpdate;
import lt.ekgame.bancho.api.packets.server.PacketUserType;
import lt.ekgame.bancho.api.units.MultiplayerRoom;
import lt.ekgame.bancho.api.units.PlayMode;
import lt.ekgame.bancho.api.packets.server.PacketReceivingFinished;
import lt.ekgame.bancho.api.packets.server.PacketUnknown61;
import spark.Request;
import spark.Response;
import spark.Spark;

public class BanchoServer implements Bancho {

	public static void main(String[] args) {
		
		BanchoServer bancho = new BanchoServer(19);
		
		Spark.port(80);
		Spark.staticFileLocation("/public");
		// The main request to handle
		Spark.post("/", (request, response) ->  {
			return bancho.handleRequest(request, response);
		});
		// The web connection that exists for some reason
		Spark.get("/web/bancho_connect.php", (request, response) -> "us");
		// The webpage to serve on a browser
		Spark.get("/", (request, response) -> {
			response.status(200);
			response.type("text/html");
			return FileUtils.readFileToString(new File("public", "index.html"));
		});
	}

	private int protocolVersion;
	
	private List<Channel> publicChannels = new ArrayList<>();
	
	private Map<String, String> tokens = new HashMap<>();
	private Map<String, Client> clients = new HashMap<>();
	
	public Channel CHANNEL_OSU, CHANNEL_ALTER;
	AlterBot bot = new AlterBot(this);
	
	public BanchoServer(int protocolVersion) {
		this.protocolVersion = protocolVersion;
		CHANNEL_OSU = channel("osu", "Main osu! channel.");
		CHANNEL_ALTER = channel("alter", "AlterBancho discussion channel.");
		channel("lobby", "Advertise (unfunctional) multiplayer lobbies.");
		channel("memes", "Discuss the dankest memes here.");
		channel("multiplayer", "Multiplayer room channel.");
		channel("lithuanian", "Because real Bancho refuses to add this.");
		
		for (Channel channel : publicChannels)
			channel.join(bot);
	}
	
	private Channel channel(String channel, String description) {
		Channel ch = new Channel(channel, description);
		publicChannels.add(ch);
		return ch;
	}
	
	public Channel getChannel(String name) {
		for (Channel channel : publicChannels) 
			if (name.equals(channel.getName()))
				return channel;
		return null;
	}
	
	@Override
	public int getProtocolVersion() {
		return protocolVersion;
	}
	
	public ByteArrayOutputStream handleRequest(Request request, Response response) {
		Set<String> headers = request.headers();
		if (headers.contains("osu-version") && !headers.contains("osu-token")) {
			// Assume authentication.
			return handleAuth(request, response);
		} else if (headers.contains("osu-token")) {
			// Assume update request
			return handleUpdate(request, response);
		} else {
			// Invadid, empty response.
			response.status(400);
			return new ByteArrayOutputStream();
		}
	}
	
	private ByteArrayOutputStream handleAuth(Request request, Response response) {
		try {
			String[] args = request.body().split("\n");
			String username = args[0];
			String password = args[1];
			String clientInfo = args[2];
			
			// TODO auth
			
			int uid = 2;
			String token = UUID.randomUUID().toString();
			Client client = new Client(token, username, uid, this);
			
			tokens.put(token, username);
			clients.put(username, client);
			
			response.header("cho-token", token);
			response.header("Connection", "keep-slive");
			client.sendPacket(new PacketTimeSync(0));
			client.sendPacket(new PacketProtocolVersion(19));
			client.sendPacket(new PacketUserId(uid));
			client.sendPacket(new PacketUserType(4));
			client.sendPacket(new PacketUnknown61(0));
			client.sendPacket(new PacketUserInfo(true, uid, username, 0, (byte)0, 0f, 0f, 1, 4, PlayMode.OSU));
			sendChannelData(client);
			CHANNEL_OSU.join(client);
			CHANNEL_ALTER.join(client);
			client.sendPacket(new PacketReceivingFinished());
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteDataOutputStream output = new ByteDataOutputStream(out, this);
			client.sendData(output);
			
			System.out.println(username + " has connected.");
			
			return out;
		} catch (Exception e) {
			e.printStackTrace();
			return new ByteArrayOutputStream();
		}
	}
	
	private void sendChannelData(Client client) {
		for (Channel channel : publicChannels)
			client.sendPacket(new PacketChatRoomInfo(channel.getName(), channel.getDescription(), 0));
	}

	private ByteArrayOutputStream handleUpdate(Request request, Response response) {
		try {
			String token = request.headers("osu-token");
			Client client = getClient(token);
			if (client == null) // Invalid token, abort.
				return new ByteArrayOutputStream();
			
			ByteArrayInputStream byteStream = new ByteArrayInputStream(request.bodyAsBytes());
			ByteDataInputStream in = new ByteDataInputStream(byteStream, this);
			
			try {
				while (true) {
					int type = in.readShort();
					in.skipBytes(1);
					int len = in.readInt();
					if (len>9999999) {
						System.out.println("Packet reading error. len: " + len);
						break; // Something went wrong, abort.
					}
					byte[] bytes = new byte[len];
					for (int i = 0; i < len; i++)
						bytes[i] = in.readByte();
					
					Class<? extends Packet> packetClass = Packets.getById(type);
					if (packetClass != null) {
						try {
							Packet packet = (Packet) packetClass.newInstance();
							ByteDataInputStream stream = new ByteDataInputStream(new ByteArrayInputStream(bytes), this);
							packet.read(stream, len);
							if (!(packet instanceof PacketIdle))
								System.out.printf("packet: %s\n", packet.getClass());
							handlePacket(packet, client);
						} catch (InstantiationException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						}
					} else {
						System.out.printf("Unknown packet: %02X %08X \n- Data: ", type, len);
						for (int i = 0; i < len; i++)
							System.out.printf("%02x ", bytes[i]);
						System.out.println();
					}
				}
			} catch (EOFException e) {
				// Finished
			}
			response.header("Connection", "keep-alive");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteDataOutputStream output = new ByteDataOutputStream(out, this);
			client.sendData(output);
			return out;
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ByteArrayOutputStream();
		}
	}

	private Client getClient(String token) {
		if (tokens.containsKey(token)) {
			String user = tokens.get(token);
			if (clients.containsKey(user)) {
				Client client = clients.get(user);
				if (!client.validateToken(token))
					return null;
				return client;
			}
		}
		return null;
	}
	
	private Client getClientByUsername(String username) {
		if (clients.containsKey(username))
			return clients.get(username);
		return null;
	}
	
	private void handlePacket(Packet packet, Client client) {
		if (packet instanceof PacketSendMessageUser) {
			System.out.println("chat");
			PacketSendMessageUser p = (PacketSendMessageUser) packet;
			if (p.user.startsWith("#")) {
				Channel channel = getChannel(p.user);
				if (channel != null) {
					channel.sendMessage(client, p.message);
					System.out.printf("[%s] %s: %s\n", channel.getName(), client.getUsername(), p.message);
				}
			} else {
				Client target = getClientByUsername(p.user);
				if (target != null) {
					client.sendMessage(target, p.message);
					System.out.printf("%s > %s: %s\n", client.getUsername(), target.getUsername(), p.message);
				}
			}
		}
		
		if (packet instanceof PacketJoinChannel) {
			String channelName = ((PacketJoinChannel) packet).channel;
			Channel channel = getChannel(channelName);
			if (channel != null)
				channel.join(client);
		}
		
		if (packet instanceof PacketLeaveChannel) {
			String channelName = ((PacketLeaveChannel) packet).channel;
			Channel channel = getChannel(channelName);
			if (channel != null)
				channel.leave(client);
		}
		
		if (packet instanceof PacketCreateRoom) {
			MultiplayerRoom room = ((PacketCreateRoom)packet).room;
			room.roomPassword = ""; // don't send password
			room.slotStatus[0] = 4;
			room.slotId[0] = client.getId();
			room.matchId = 456;
			getChannel("#multiplayer").join(client);
			client.sendPacket(new PacketRoomJoined(room));
			client.sendPacket(new PacketRoomBecameHost());
			client.sendPacket(new PacketRoomUpdate(room));
			client.sendPacket(new PacketRoomUpdate(room));
			client.sendPacket(new PacketRoomUpdate(room));
			client.sendPacket(new PacketRoomUpdate(room));
			client.sendPacket(new PacketRoomUpdate(room));
		}
	}
}
