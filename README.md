# SHOP-MOD-lyn 1.3.0

Client-and-server survival shop for **Minecraft Java 26.2 / Fabric**, connected directly to **ECO-API-lyn**. This is a separate Gradle project rebuilt from the behavior of the old 1.20.1 shop. The old Desktop/shopmod project and its worlds are unchanged.

## Requirements

-

## Commands

| Command |
| --- | --- |
| `/shop` | 
| `/shop search diamond` | 
| `/sell` |
| `/sell hand` | 
| `/sell all` | 
| `/shopadmin reload` | 
| `/shopadmin tier Player gold` | 

## Auction

| Command 
| --- | --- |
| `/ah` | 
| `/ah bids` | 
| `/ah sell <total price> [hours]` | 
| `/ah auction <starting total> [hours]` | 
| `/ah mine` | 
| `/ah inbox` | 
| `/ah search <text>` |
| `/ah bid <listing UUID> <amount>` | 
| `/ah help` | 
| `/ahadmin reload` | 
| `/ahadmin cancel <listing UUID>` | 
| `/ahadmin reassign <old UUID> <online player>` | 

Listing prices apply to the entire selected bundle. `[hours]` is optional. Purchases, bids and listings require confirmation.

Auctions support owned vanilla and modded items whose data can be saved safely. Settings and blacklist rules are stored in `config/shop-mod-lyn/auctions.json`.



See [docs/AUCTIONS.md](docs/AUCTIONS.md) for commands, configuration and recovery details.

## Prices

Prices are per **individual item**, not per stack. They are balanced as a deliberately difficult starting point against your planned **$500 daily Diamond-rank income**:

| Item | Buy | Sell |
| --- | ---: | ---: |
| Diamond | $5,000.00 | $25.00 |
| Emerald | $7,500.00 | $0.25 |
| Diamond chestplate | $48,000.00 | $5.00 |
| Iron ingot | $150.00 | $0.50 |
| Gold ingot | $250.00 | $0.75 |
| Ordinary egg | $15.00 | $0.02 |

## Rare-item exclusions

The shop and `/sell` exclude **all netherite-related items**, ancient debris, dragon eggs/heads, end portal frames, elytra, maces/heavy cores, tridents, nether stars/beacons, enchanted golden apples, rare boss/structure rewards, player/mob heads, spawn eggs, creative/admin blocks, and other explicitly listed progression rewards. Trim templates, pottery sherds, and music discs are excluded too. End progression items and shulker storage are omitted from the starter catalog.

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

```powershell
.\gradlew.bat build
```

The install JAR is `build/libs/SHOP-MOD-lyn-1.3.0.jar`; source JAR is next to it. `tools/package.ps1` assembles `dist/1.3.0/`. The original code is MIT; see LICENSE. No Minecraft JARs are distributed in this project.

`Trades` uses ECO-API-lyn's `withdraw()`/`deposit()` results, not `setBalance()` or a scoreboard bridge. UUIDs identify accounts. Operations run on the Minecraft server thread, recheck quoted inventory contents, check totals using integer cents, and persist player inventory after applying the trade. Failed money operations leave item counts unchanged. Inventory-application exceptions attempt checked compensation and record incidents in the world at `shop-mod-lyn/incidents.log` for operator reconciliation.

The economy ledger and Minecraft player data are separate persistent stores. A sudden process/OS failure between their writes is not a fully atomic cross-system transaction. Keep world/economy backups and test on a copy of your world before production. Do not retry an uncertain trade manually without checking the ledger/incident reference.



