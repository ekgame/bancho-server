# Bancho Server
A very basic custom osu!Bancho server implementation, codename AlterBancho.

This was made to better understand Bancho protocol for the [bancho-client](https://github.com/ekgame/bancho-client) project.

To make things easyer, this server uses [Spark Framework](http://sparkjava.com/) to run an HTTP server.

# Features
This server is very incomplete, but it has some basic functionality:
* Logging in (with any credentials)
* Basic chat channels and chatting
* Basic bot that responds to `!test` command.

# Depencencies
Most of the dependency management is done with Maven. There is only one library that you will need to reference manually:
* [Bancho API](https://github.com/ekgame/bancho-api) - the commons API used for packet parsing.
