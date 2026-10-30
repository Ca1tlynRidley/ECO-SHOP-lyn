# Gameplay checks on a test world

Install SHOP-MOD-lyn, ECO-API-lyn, and Fabric API on a Fabric 26.2 test server using Java 25+. Connect with Fabric 26.2 clients carrying Fabric API and SHOP-MOD-lyn 1.2.0, without ECO installed. Verify this client can join. Vanilla clients, clients missing SHOP, and old SHOP versions must be refused. Verify the dedicated server refuses to start the shop without ECO 1.2.0 or newer. Keep backups; accept Minecraft's EULA yourself when setting up a new server.

1. Join to create an account. Give it funds with `/eco set YourName 100000`.
2. Browse each `/shop` category and page. Search diamond, pale oak, sulfur, and cinnabar.
3. Buy an egg, blue egg, brown egg, and a diamond. Check quantities and exact balance changes.
4. Use `/sell hand` and confirm. Check the purchased eggs/diamond are accepted at their lower prices.
5. Use `/sell all`, cancel it, and verify every original item remains in place. Repeat and confirm to verify exactly the quoted eligible items are sold.
6. Fill the inventory. Confirm a purchase that cannot fit; check there is no charge, item overwrite, or dropped overflow. Test tools and partially filled matching stacks too.
7. Try shift-click, number keys, drag, double-click, drop, outside clicks, creative clone, and rapid double confirmation. Neither display items nor real items should duplicate or disappear.
8. Try named/enchanted equipment and filled containers; verify protection. Try a damaged ordinary pickaxe and check a reduced payout.
9. Verify excluded rare items cannot be bought or sold and URLMusicDisc items do not appear.
10. Check Iron's 120-second sale cooldown. Assign Gold/Diamond and check 90/45 seconds. Restart and verify rank/cooldown persistence. Failed purchases/sales must not create a successful-sale cooldown.
11. Change an egg price and reload. Check both menus update. Try sell >= buy, an invalid ID, and a forbidden item; reload must reject them and retain the previous catalog.
12. As a non-operator, verify `/shopadmin` cannot reload or grant tiers. Verify shop balance changes appear in PROFILE-lyn within its normal refresh interval.
13. Stop/restart and verify inventory and balances. Review economy transaction reasons and the shop incident log if a failure occurred.
