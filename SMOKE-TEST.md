# Gameplay checks on a test world

Install SHOP-MOD-lyn, ECO-API-lyn, and Fabric API on a Fabric 26.2 test server using Java 25+. Connect with Fabric 26.2 clients carrying Fabric API and SHOP-MOD-lyn 1.3.0, without ECO installed. Verify this client can join. Vanilla clients, clients missing SHOP, and old SHOP versions must be refused. Verify the dedicated server refuses to start the shop without ECO 1.2.0 or newer. Keep backups; accept Minecraft's EULA yourself when setting up a new server.

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

14. Connect two matching SHOP 1.3.0 clients to an offline-mode test server. Keep the exact same username/case when reconnecting. Verify balances, listings, bids and collection ownership survive reconnect and server restart. A different server-issued UUID must never inherit another player's collection by matching their name.
15. Open `/ah` and switch between Buy Now, Bidding, List Held Item, My Listings and Collection. Test pagination, sorting, search, back, refresh and close. Verify no displayed items can be moved with shift-click, number keys, drag, drop, double-click or clone clicks.
16. Hold a named, enchanted or damaged item; create a buy-now listing, adjust quantity/price/duration, cancel the review, and verify nothing was removed. Confirm a listing and verify only the selected quantity is removed. Use exact decimal prices through `/ah sell`.
17. With the relevant item mods installed, list a real modded item (including URLMusicDisc if used), plus a filled container containing a modded item. Buy and collect them. Compare names, enchantments, durability, custom components, contents and actual mod behavior before and after server restart. The automated test uses a registered test mod item; it does not substitute for this check with your own item mods.
18. Try an auction-listed netherite item or dragon egg. It should be tradable in `/ah` by default, but remain excluded from the fixed-price catalog. Add its ID or namespace to the auction blacklist and reload; new listings should be refused without removing the held item. Existing listings can be cancelled by `/ahadmin cancel`.
19. Buy with enough money, without enough money, and twice rapidly from stale screens. Verify at most one buyer is charged and only that buyer gets a collection entitlement. Fill the buyer's inventory: collection must refuse and retain the item until space is made. Collect it once and confirm it cannot be collected again.
20. Place a bid, raise your own bid, and outbid it using the other player. The first bid reserves the full amount; raising your own bid reserves only the increase. The outbid player collects their refund from Collection. Reject self-bids, self-purchases, too-small bids and stale bid confirmations.
21. Advance a test auction toward expiry and bid within the anti-snipe window; check the extension. Restart with an expired bid auction. The winner should have one item entitlement and the seller one earnings entitlement. Expired unbid and buy-now listings should return one item entitlement to their seller.
22. Log the seller out before a purchase or auction expiry, then reconnect and collect earnings. Test collection at the ECO balance cap; failed transfers must retain the collection entry. Confirm listing limits and duration limits.
23. On an expendable copy, interrupt a listing, payment or claim between the prepared journal and completion. The market must pause with a pending reference instead of replaying the edit or payment. Review `/ahadmin pending`, auction/economy backups and player data; resolve the verified outcome as documented in docs/AUCTIONS.md. Never run failure experiments on the production world.
24. Verify non-operators cannot use `/ahadmin`. On a copied world, test `/ahadmin reassign` after a deliberate offline UUID change. Only the specified UUID's auction records should move; ordinary ECO balances should stay on their existing accounts.
25. Stop and start the server normally. Verify the fixed-price shop, sale cooldowns, PROFILE balance display and existing price configuration still work. Keep your existing prices.json when upgrading.

26. Upgrade using an old prices.json without a Totem entry. Verify a backup is created and only the missing Totem entry and catalog revision are added. Buy a Totem for 300.00 and sell it for 1.00; check exact balances and Gear placement. Repeat with an existing custom-priced or disabled Totem entry; it must be preserved. Remove the entry after migration, reload, and verify it stays removed.
