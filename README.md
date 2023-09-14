# Ring Message

Silly mod to enable "Group Chats" by having a "Ring of Members". Everyone will pass the messages to the next online member until all got the message. So each sent message is 1 dm.

If you receive a message, you'll automatically pass it along unless you were the last memeber in the ring.

Current the order of the ring is random each time.

## No Chat Reports encryption support

The offical [NoChatReports Mod](https://github.com/Aizistral-Studios/No-Chat-Reports) is supported as well my [custom fork](https://github.com/EnderKill98/No-Chat-Reports) which adds a lot of quality of live and cool features (recommended).

## How to use

Get the [latest release](https://github.com/EnderKill98/RingMessage/releases) or compile it from source. The mod should currently work on `1.20` and `1.20.1`.

You can now use the command `$rmsg` (or long: `$ringmsg`) ingame. That should be caught by this mod and show you the syntax.

The most important command is probably `$rmsg members set ...`. Members can be comma and/or space seperated. The casing should be correct. All members should have all other members.

**Important**: Offline members are automatically excluded. But online members are assumed the to have the mod installed and setup correctly. If not they could break/stop the ring at a random point. Using inconsistent/unsupported encryption (at least when the sender used them) can also lead to the ring to be interrupted if someone can't decrypt the message.

You can then send messages using `$rmsg send your message...` or with the alias `$ your message ...`.

Have fun!
