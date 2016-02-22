package lt.ekgame.bancho.server;

public class AlterBot extends Client {

	public AlterBot(BanchoServer server) {
		super("", "AlterBot", 0, server);
	}

	@Override
	public boolean validateToken(String token) {
		return false; // Disallow login as bot
	}

	@Override
	public void sendChatPacket(Channel channel, Client user, String message) {
		String response = null;
		message = message.trim();
		if (message.startsWith("!")) {
			String command = message.substring(1);
			String[] args = command.split(" ");
			if (args.length > 0) {
				if (args[0].equals("test")) {
					response = "Great success!";
				}
			}
		}
		
		if (response != null) {
			if (channel != null) {
				sendMessage(channel, response);
			}
			else {
				sendMessage(user, response);
			}
		}
	}
	
	
}
