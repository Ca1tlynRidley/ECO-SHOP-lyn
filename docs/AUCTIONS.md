# Player auction house

SHOP-MOD-lyn 1.3.0 adds a player market alongside the fixed-price shop. Install SHOP 1.3.0 and Fabric API on the server and clients. ECO-API-lyn 1.2.0 or newer is needed only on the server. Keep existing prices.json when upgrading and replace older SHOP JARs rather than keeping two versions.

## Using the menus

`/ah` opens Buy Now. The bottom row contains Buy Now, Bidding, List Held Item, My Listings, Collection, Refresh, previous/next page and Close buttons. Buy Now and Bidding open separate market pages. Browse 36 listings per page, sort by newest, price or ending time, and left-click an item to review its seller, complete bundle price and remaining time. Every purchase and bid requires confirmation. The `/shop` home page also has an Auction House button.

To sell, hold an item and select List Held Item. Switch between Buy Now and Bidding, adjust the bundle quantity, total price and duration, then confirm. The menu removes only the confirmed quantity after saving an operation journal. For an exact price, click the command button or use one of the commands below; both listing commands open a review menu before removing an item. The displayed price is for the entire bundle, not each item.

Buy-now purchases go into Collection. Bids reserve money in an internal ECO escrow account. When outbid, collect your refund from Collection. Raising your own leading bid charges only the increase. When a timed auction ends, its winner receives an item collection entry and its seller receives an earnings entry. An expired unbid or buy-now listing returns the item to its seller's Collection. Sellers and bidders may be offline when these changes occur. Timers use real elapsed time; expired listings settle on the next startup after downtime.

Collection items and money do not expire. Click the emerald at the top to claim money; click an item, review it and confirm collection. A full inventory leaves the item in Collection. A failed money transfer, including reaching ECO's balance limit, leaves the money entry available. Items are never dropped to make room. A completed purchase cannot be undone by the seller. Sellers can cancel an active listing only before it receives a bid; an operator can cancel with refunds using `/ahadmin cancel`.

## Commands

| Command | Purpose |
| --- | --- |
| `/ah` | Buy-now market |
| `/ah bids` | Separate bidding market |
| `/ah mine` | Your active listings |
| `/ah inbox` | Item collection, sale earnings and outbid refunds |
| `/ah search <text>` | Search buy-now item IDs, names and sellers; the query carries over when switching to Bidding |
| `/ah sell <total price> [hours]` | Prepare a buy-now listing for the held stack |
| `/ah auction <starting total> [hours]` | Prepare a bidding auction for the held stack |
| `/ah bid <listing UUID> <amount>` | Open a review screen for an exact bid |
| `/ah help` | Command help |
| `/ahadmin reload` | Operator: validate/reload auction configuration and close open auction menus |
| `/ahadmin cancel <listing UUID>` | Operator: cancel, return the item, and queue any bid refund |
| `/ahadmin reassign <old UUID> <online player>` | Operator: repair auction ownership after a verified UUID change |
| `/ahadmin pending` | Operator: inspect an unfinished external operation |
| `/ahadmin resolve <operation UUID> commit` | Operator: accept the planned book state after verifying the external effect |
| `/ahadmin resolve <operation UUID> abort` | Operator: retain the prior book state after verifying/reconciling the external effect |

Prices accept up to two decimal places. For example, `/ah sell 125.50 24` opens a review menu with a total price of 125.50 and a 24-hour duration. A listing UUID is shown in its review tooltip and in the persisted auction data.

## Modded and rare items

The auction house accepts any registered item with a complete, persistent ItemStack representation. Its rules are separate from the fixed-price catalog: netherite, dragon eggs, custom music discs and other owned rare/modded items are allowed by default. `/shop` and `/sell` keep their existing exclusions and prices.

Stacks are encoded with Minecraft's registry-aware ItemStack codec into binary NBT. Counts, names, lore, enchantments, damage, typed custom NBT, persistent mod components and filled-container contents are preserved. The original stack is stored separately from GUI previews. A round-trip check refuses items whose mods cannot save all components before anything is removed. Keep the relevant item mods installed; removing a mod or its component definitions can make its saved auction items temporarily unavailable. The saved data is retained so the item can be recovered after restoring the mod. Clients need the item mods on the sides those mods normally require.

The codec tests register a real test item and a persistent component under a non-vanilla namespace. Test your actual item mods, including URLMusicDisc if used, using SMOKE-TEST.md before production.

## Configuration

Edit `config/shop-mod-lyn/auctions.json`, then run `/ahadmin reload`. A sample is included at `config/auctions.json` in the source project.

```json
{
  "enabled": true,
  "maxListingsPerPlayer": 20,
  "maxActiveListings": 5000,
  "defaultBuyNowHours": 48,
  "defaultAuctionHours": 24,
  "maxDurationHours": 72,
  "defaultPriceCents": 1000,
  "minimumBidIncrementCents": 100,
  "bidIncrementPercent": 5,
  "antiSnipeSeconds": 120,
  "maxItemBytes": 1048576,
  "blacklist": []
}
```

The default starting price is 10.00. The default bid increment is the greater of 1.00 or 5% of the highest bid, rounded up to a cent. A bid in the final 120 seconds extends the end to 120 seconds after that bid. Set antiSnipeSeconds to zero to disable extensions. There are no listing fees or auction taxes in this release.

Set enabled to false to prevent new listings while allowing existing listings and collections to finish. Blacklist entries can be exact IDs such as `minecraft:dragon_egg` or namespace rules such as `examplemod:*`. They block new listings; use the admin cancellation command to remove an existing listing and return any escrowed items/funds. The size limit is checked during saving and loading; lowering it can temporarily prevent collection of previously accepted larger items until the limit is restored. Invalid configuration is rejected and never replaces a working in-memory configuration.

## Offline-mode identities

Ownership uses `ServerPlayer.getUUID()` for every listing, bid and collection entry. No Mojang account lookup or online-player UUID conversion is used. This works with the UUIDs actually supplied by either online-mode or offline-mode servers. Offline sellers' payments are queued by their stored UUID.

On offline-mode servers, username changes, capitalization changes and changes to proxy/UUID forwarding can produce a different UUID. Keep those settings consistent. Names are display labels, never proof that two UUIDs belong to the same person. Auction ownership is not automatically merged by name.

After verifying an intentional UUID change, an operator can run `/ahadmin reassign <old UUID> <online player>`. The target must be connected; its actual server UUID is used. This moves the specified UUID's auction listings, active bids and collection entries in one saved update. It refuses changes that would create a self-bid. It does not move ordinary ECO balances or any other mod's player data; reconcile those separately if you changed server identity settings. Reassignments are logged.

## Persistence and recovery

Auction state is stored in `<world>/shop-mod-lyn/auctions.json`, alongside a previous snapshot `.bak`. This world file contains escrowed items, bids, collection entries and an operation journal. It is different from the configuration file with the same basename. Back up the entire world, including ECO accounts and playerdata, together. Do not edit or reset the world auction file while the server is running.

Money uses checked ECO transfers to and from an internal `LynAuctionHouse` service account. Do not spend, reset or administratively withdraw its balance. It backs outstanding bid and collection obligations. Its UUID is stored in the world auction file. A missing or underfunded escrow account causes startup to refuse the auction house rather than recreating players' money.

Before listing an item, buying, bidding, claiming an item or claiming money, the house durably records the intended change. After a successful payment or verified player-inventory save, it commits the corresponding auction book update. Player inventory changes are read back from the saved player file and flushed before the auction operation completes. State-only changes, such as expiry, cancellation and ownership reassignment, are saved atomically.

ECO, playerdata and the auction book are separate files, so a process crash or write failure between their updates can leave an uncertain operation. The auction house pauses if such an operation is found; it does not blindly repeat a charge, delivery or removal. The player receives a reference. Other fixed-price shop functions remain independent.

For recovery, run `/ahadmin pending` and inspect the journal's actor, type, details, prior book and planned `after` book. Match payment operations against ECO's transaction history and server logs; match LIST or CLAIM_ITEM operations against the player's current and saved inventory. Operation references and successful economy transaction IDs are logged. Reconcile any partially applied effect before resolving. For example, if a verified BUY transfer succeeded, commit its pending book state so the buyer receives the item entitlement and the seller receives earnings. If it definitely never occurred, abort to retain the original listing. For a listing or item claim, commit only when the intended inventory change was verified; otherwise restore/reconcile the inventory before choosing abort. Neither resolve command repeats an external payment or inventory edit. The pending reference must match exactly, and escrow must cover the selected book state.

Run the live multiplayer checks in SMOKE-TEST.md on a copied world, including your server's offline login setup and actual item mods. Automated tests do not replace that check.
