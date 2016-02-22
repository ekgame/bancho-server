package lt.ekgame.bancho.server;

import java.util.ArrayList;
import java.util.List;

import lt.ekgame.bancho.api.packets.server.PacketChatRoomInfo;
import lt.ekgame.bancho.api.packets.server.PacketChannelJoined;

public class Channel {
	
	private String channelName;
	private String channelDescription; 
	private List<Client> users = new ArrayList<>();
	
	public Channel(String channel, String description) {
		this.channelName = "#" + channel;
		this.channelDescription = description;
	}
	
	public String getName() {
		return channelName;
	}

	public String getDescription() {
		return channelDescription;
	}
	
	public void join(Client client) {
		if (!users.contains(client)) {
			users.add(client);
			client.sendPacket(new PacketChatRoomInfo(channelName, channelDescription, 1));
			client.sendPacket(new PacketChannelJoined(channelName));
		}	
	}
	
	public void leave(Client client) {
		if (users.contains(client))
			users.remove(client);
	}


	public void sendMessage(Client sender, String message) {
		for (Client user : users) 
			if (user != sender)
				user.sendChatPacket(this, sender, message);
	}
}
