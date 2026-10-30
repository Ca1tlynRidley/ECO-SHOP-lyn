# ECO-API-lyn 1.2.0

ECO is now a dedicated-server-only mod. Install ECO-API-lyn-1.2.0.jar in the server's mods folder alongside Fabric API for Minecraft 26.2. Replace the old ECO JAR rather than keeping two versions.

Players do not need ECO installed. There is no ECO client entrypoint or ECO installation handshake. Existing balance data and configuration files use the same format, so no reset is needed.

The existing PROFILE-lyn JAR continues to use the same public money API. No changes or commits to the PROFILE repository are needed. When building PROFILE against ECO 1.2.0, use .\gradlew.bat build "-Peco_api_version=1.2.0" after publishing ECO to Maven local. This selects the new artifact without editing PROFILE's build files.

For shop servers, use SHOP-MOD-lyn 1.2.0 on both the server and clients. Its client no longer depends on ECO. The server checks that ECO 1.2.0 or newer is installed before initializing the shop. SHOP still requires its own matching client version.

Server installation: Fabric API, ECO-API-lyn, PROFILE-lyn if used, SHOP-MOD-lyn if used.

Player installation: SHOP-MOD-lyn and Fabric API if playing on a shop server. ECO and PROFILE are not required on the client. An ECO/PROFILE server without SHOP accepts ordinary vanilla 26.2 clients.

Automated tests cover the economy, server-only metadata, and SHOP's independent client classes. A live multiplayer connection check remains necessary before production use.
