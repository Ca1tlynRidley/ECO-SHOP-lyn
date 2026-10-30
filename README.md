# SHOP-MOD-lyn 1.3.0

Client-and-server survival shop for **Minecraft Java 26.2 / Fabric**, connected directly to **ECO-API-lyn**. This is a separate Gradle project rebuilt from the behavior of the old 1.20.1 shop. The old Desktop/shopmod project and its worlds are unchanged.

## Install

1. Use Minecraft Java **26.2**, **Fabric Loader 0.20.1+**, **Fabric API for 26.2**, and **Java 25+**.
2. Stop your server and copy `dist/1.3.0/SHOP-MOD-lyn-1.3.0.jar` into its `mods/` directory.
3. Install **ECO-API-lyn-1.2.0.jar** alongside it. PROFILE-lyn is optional; if installed, its `{money}` display automatically uses the same balances.
4. Remove the old shop JAR from the 26.2 server. Impactor is not required by this mod. Existing Impactor balances are not migrated automatically.
5. Install Fabric Loader, Fabric API for 26.2 and SHOP-MOD-lyn 1.3.0 on each player's client. ECO is server-only. Start the server and use `/shop` and `/sell` with those matching client mods installed. Plain vanilla or missing/mismatched clients are refused before world entry.

The ZIP in `dist/1.3.0/` contains the shop JAR, a ready-to-edit price file, and these instructions. The `libs/` economy JAR is a development dependency; install only one copy of ECO-API-lyn on the server.

## Client and server installation

Install SHOP-MOD-lyn 1.3.0 and Fabric API on the server and players' clients. ECO-API-lyn 1.2.0 is needed only on the dedicated server. The shop checks this server dependency at startup; its client code and connection handshake do not use ECO. Missing or mismatched SHOP clients are still refused. PROFILE-lyn and its money API integration do not need a code change. Remove older ECO/SHOP JARs when upgrading.

## Shop and sale commands

| Command | Function |
| --- | --- |
| `/shop` | Browse eight paginated categories |
| `/shop search diamond` | Find items by ID/name words |
| `/sell` | Preview eligible items in the main inventory/hotbar |
| `/sell hand` | Review selling the held stack |
| `/sell all` | Review selling all eligible main inventory/hotbar items |
| `/shopadmin reload` | OP level 2+: validate and apply prices; closes old shop menus |
| `/shopadmin tier Player gold` | OP level 2+: assign a shop cooldown tier; known UUIDs also work |

Click a shop item, choose a quantity, then confirm the purchase. The server checks both balance and inventory space. It never drops overflow purchases on the ground or charges for a failed capacity check.

The sell menu displays **copies** of items already in your inventory. Click a stack, review the quote, and confirm the sale. `/sell all` also requires confirmation. Nothing is moved into temporary chest storage, so closing/canceling the GUI leaves the items in place. All display-slot movement, shift-click, number-key swap, dragging, dropping, and creative clone clicks are blocked. The player inventory is locked while either menu is open.

Every enabled buy entry has a positive sell price below its buy price, read from the **same catalog**. Normal eggs, blue eggs, and brown eggs are explicitly included. There is no separate sell whitelist to fall out of sync. Purchases deliver ordinary items without the shop preview's name/lore.

Damaged equipment can be sold for a proportionally reduced payout (minimum one cent if it still has durability). Renamed, enchanted, container-filled, or otherwise modified items are protected from accidental sale. They stay in the inventory and show as unavailable. Armor currently equipped and offhand items are not included by `/sell all`; move ordinary items into the main inventory first if you want to sell them.

## Player auctions

`/ah` opens the Buy Now market. Click **Bidding** for the separate timed-auction page, **List Held Item** to create a listing, **My Listings** to manage listings, or **Collection** to claim items, earnings and bid refunds. A button on the `/shop` home page also opens the auction house. Purchases, bids, listings, cancellations and item collection use review screens.

Hold an item and use `/ah sell <total price> [hours]` for a buy-now listing, or `/ah auction <starting total> [hours]` for bidding. These commands open a confirmation screen. Prices are for the entire selected bundle. The GUI supports quantity, price and duration controls; exact prices can be entered using the suggested chat command.

Player auctions allow all owned vanilla and modded items, including rare items, named/enchanted gear and filled containers. Their persistent item data is preserved. Items whose mod cannot serialize them safely are refused before removal. The fixed-price `/shop` and `/sell` catalog and its exclusions are unchanged.

Auction settings are in `config/shop-mod-lyn/auctions.json`. The default blacklist is empty; add exact IDs such as `minecraft:dragon_egg` or namespace rules such as `examplemod:*`, then run `/ahadmin reload`. Bids reserve funds; sale earnings and outbid refunds are collected from **Collection**. Items and collection entries do not expire.

Online and offline-mode servers use the actual UUID supplied by the connected server player, without Mojang lookup. Keep offline usernames, capitalization and proxy UUID settings consistent. An operator can repair auction ownership after an intentional UUID change with `/ahadmin reassign <old UUID> <online player>`; this does not move existing ECO balances.

See [docs/AUCTIONS.md](docs/AUCTIONS.md) for commands, configuration and recovery details.

## Hard survival pricing

The default catalog contains **1,271 items**, including **250 item types added after 1.20.1**. New content includes pale oak, resin, copper equipment, sulfur, cinnabar, and other 26.2-era items. The `new` category groups those post-1.20.1 additions.

Prices are per **individual item**, not per stack. They are balanced as a deliberately difficult starting point against your planned **$500 daily Diamond-rank income**:

| Item | Buy | Sell |
| --- | ---: | ---: |
| Diamond | $5,000.00 | $25.00 |
| Emerald | $7,500.00 | $0.25 |
| Diamond chestplate | $48,000.00 | $5.00 |
| Iron ingot | $150.00 | $0.50 |
| Gold ingot | $250.00 | $0.75 |
| Ordinary egg | $15.00 | $0.02 |

Diamond costs ten days of the $500 allowance before other earnings. Farmable items usually return cents to slow automatic-farm inflation. Diamond/emerald blocks use matching nine-unit prices. These are editable defaults, not a guarantee against every possible recipe, villager, farm, or other-mod money loop; tune them against your actual server economy.

## Rare-item exclusions

The shop and `/sell` exclude **all netherite-related items**, ancient debris, dragon eggs/heads, end portal frames, elytra, maces/heavy cores, tridents, totems, nether stars/beacons, enchanted golden apples, rare boss/structure rewards, player/mob heads, spawn eggs, creative/admin blocks, and other explicitly listed progression rewards. Trim templates, pottery sherds, and music discs are excluded too. End progression items and shulker storage are omitted from the starter catalog.

The fixed-price catalog accepts only vanilla `minecraft:` identifiers. **URLMusicDisc and other modded items are not included in `/shop` or `/sell`; they can be traded through `/ah`.** The exact policy is readable in `src/main/java/dev/lyn/shop/CatalogPolicy.java`. Excluded items cannot be added through prices.json without intentionally changing that policy and rebuilding. Diamonds and emeralds are allowed because they were explicitly requested.

## Edit prices without rebuilding

The server creates **`config/shop-mod-lyn/prices.json`** at first start. A matching sample is in this project's `config/prices.json`. Prices use quoted currency-unit strings with up to two decimal places, never floating point values:

```json
{
  "schemaVersion": 1,
  "maxPurchase": 2304,
  "sellCooldownSeconds": {"iron": 120, "gold": 90, "diamond": 45},
  "items": {
    "minecraft:egg": {
      "category": "food",
      "buy": "15.00",
      "sell": "0.02",
      "enabled": true
    }
  }
}
```

This short example illustrates the schema; replacing the whole file with it would leave only eggs. Edit entries inside the full generated file, save, and run **`/shopadmin reload`**. Both buy and sell must be at least `0.01`, and sell must be strictly below buy. Unknown item IDs, forbidden items, invalid categories, fractional cents, and invalid limits reject the entire reload while retaining the working catalog.

Set `enabled` to false to remove an item from both buying and selling. Add other allowed vanilla 26.2 items using the same schema. Categories are `blocks`, `ingredients`, `gear`, `food`, `redstone`, `nature`, `utility`, and `new`. `maxPurchase` is a number between 1 and 2304; actual capacity may be much smaller for tools or an already-filled inventory. A spreadsheet-friendly list is provided in `config/PRICE-LIST.csv`.

## Rank cooldown integration

The default tier is **iron**. Successful sales start a persisted cooldown: **Iron 120 s**, **Gold 90 s**, **Diamond 45 s**. Failed transactions and empty sales do not use a cooldown. Purchases have no rank cooldown. Configure these values in prices.json; custom tier names are supported too.

Assign with `/shopadmin tier Player diamond` from an operator account or console. Rank and last-sale timestamps live inside the selected world at `shop-mod-lyn/player-tiers.json`, with a previous snapshot backup. Your rank-management mod can execute this command when granting/revoking a rank. Players cannot assign themselves a rank.

This implements **shop cooldown benefits only**. Daily money grants, real-money purchases, homes, mystery boxes, chat tags, Discord roles, RTP, and EmojiFul are outside this shop. Your planned daily grant system can use ECO-API-lyn's checked `deposit()` operation. No automatic daily payout is introduced here.

## Developer notes

Open this folder in IntelliJ IDEA as a **Gradle** project and select Java 25+. The Gradle wrapper and `libs/ECO-API-lyn-1.2.0.jar` are included:

```powershell
.\gradlew.bat build
```

The install JAR is `build/libs/SHOP-MOD-lyn-1.3.0.jar`; source JAR is next to it. `tools/package.ps1` assembles `dist/1.3.0/`. The original code is MIT; see LICENSE. No Minecraft JARs are distributed in this project.

`Trades` uses ECO-API-lyn's `withdraw()`/`deposit()` results, not `setBalance()` or a scoreboard bridge. UUIDs identify accounts. Operations run on the Minecraft server thread, recheck quoted inventory contents, check totals using integer cents, and persist player inventory after applying the trade. Failed money operations leave item counts unchanged. Inventory-application exceptions attempt checked compensation and record incidents in the world at `shop-mod-lyn/incidents.log` for operator reconciliation.

The economy ledger and Minecraft player data are separate persistent stores. A sudden process/OS failure between their writes is not a fully atomic cross-system transaction. Keep world/economy backups and test on a copy of your world before production. Do not retry an uncertain trade manually without checking the ledger/incident reference.

## Validation

Automated checks bootstrap the actual Minecraft 26.2 item registries/components and verify every catalog product exists and a fresh purchased item has the correct sell value. Other checks cover exclusions, eggs, expensive gems, exact prices, full inventories, stack merging, unstackable gear, damaged-item calculations, cooldown restart persistence, and failed tier-state writes. The access widener is validated by Loom.

Before production, run the multiplayer checks in `SMOKE-TEST.md`. A live client gameplay session has not been completed in this workspace.
