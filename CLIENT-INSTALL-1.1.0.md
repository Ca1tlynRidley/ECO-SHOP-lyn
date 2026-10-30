# Client-required releases: ECO-API-lyn and SHOP-MOD-lyn 1.1.0

Install the **same 1.1.0 JARs on the Fabric 26.2 server and every player's Fabric 26.2 client**, alongside Fabric API for 26.2. Use Java 25+.

- `ECO-API-lyn-1.1.0.jar`
- `SHOP-MOD-lyn-1.1.0.jar` (when the server uses the shop)
- Fabric API for 26.2

PROFILE-lyn remains server-only. Remove older copies of ECO/SHOP from the mods folder when replacing them; do not install two versions of the same mod. Existing balances and price configurations are compatible and do not need to be reset. Back them up before upgrading.

The server verifies each mod during Minecraft's configuration phase, before the player enters the world. A plain vanilla client, missing client receiver, old/mismatched version, wrong protocol/challenge, or missing reply fails the check and displays installation instructions. Matching clients reply automatically. Each challenge has a fresh per-connection nonce and a 25-second timeout; unvalidated connections cannot finish configuration. Singleplayer/integrated servers use the same installed client mods.

This checks a compatible client protocol, not the origin of a file. It cannot prove someone downloaded from Modrinth rather than another source. Publish the JARs on your own Modrinth projects and point players to those pages. Mark ECO and SHOP as **required on client and server**, and declare ECO-API-lyn as a **required dependency** of SHOP-MOD-lyn. PROFILE-lyn's own environment remains server-only.

The handshake exchanges a protocol number, mod version, and random connection challenge. The server remains authoritative for prices, payments, and inventory changes.

Automated validation covers matching/mismatched versions, wrong nonce/protocol, late/duplicate replies, timeout behavior, packet codecs, artifact metadata, and the existing shop/economy logic. Complete the multiplayer smoke tests before using the release in production; an actual connected-client handshake session has not yet been exercised here.
