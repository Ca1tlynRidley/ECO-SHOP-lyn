package dev.lyn.shop;

import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

public record ShopCatalog(int maxPurchase, Map<String, Integer> sellCooldownSeconds, Map<String, Product> items) {
    public static final long MAX_UNIT_PRICE = 1_000_000_000_000L;
    public static final Set<String> CATEGORIES = Set.of("blocks", "ingredients", "gear", "food", "redstone", "nature", "utility", "new");
    public record Product(String id, String category, long buy, long sell) {}
    public ShopCatalog { sellCooldownSeconds = Map.copyOf(sellCooldownSeconds); items = Map.copyOf(items); }
    public List<Product> category(String category) { return items.values().stream().filter(p -> p.category().equals(category)).sorted(Comparator.comparing(Product::id)).toList(); }
    public static ShopCatalog parse(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > 2_000_000) throw new IllegalArgumentException("Catalog exceeds 2 MB");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("schemaVersion").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("Unsupported catalog version");
            int max = root.get("maxPurchase").getAsBigDecimal().intValueExact();
            if (max < 1 || max > 2304) throw new IllegalArgumentException("maxPurchase must be 1..2304");
            Map<String,Integer> cooldowns = new HashMap<>();
            for (var e : root.getAsJsonObject("sellCooldownSeconds").entrySet()) {
                int seconds = e.getValue().getAsBigDecimal().intValueExact();
                if (!e.getKey().matches("[a-z0-9_-]{1,24}") || seconds < 0 || seconds > 86400) throw new IllegalArgumentException("Invalid sale cooldown");
                cooldowns.put(e.getKey(),seconds);
            }
            if (!cooldowns.containsKey("iron")) throw new IllegalArgumentException("iron is the required default tier");
            Map<String,Product> items = new TreeMap<>();
            for (var entry : root.getAsJsonObject("items").entrySet()) {
                String id = entry.getKey();
                if (!CatalogPolicy.allowed(id)) throw new IllegalArgumentException("Excluded rare/admin/modded item: " + id);
                JsonObject e = entry.getValue().getAsJsonObject();
                if (e.has("enabled") && !e.get("enabled").getAsBoolean()) continue;
                String category = e.get("category").getAsString();
                if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("Unknown category for " + id);
                long buy = price(e.get("buy")), sell = price(e.get("sell"));
                if (sell >= buy) throw new IllegalArgumentException(id + ": sell must be lower than buy");
                items.put(id, new Product(id,category,buy,sell));
            }
            if (items.isEmpty()) throw new IllegalArgumentException("Catalog has no enabled items");
            return new ShopCatalog(max,cooldowns,items);
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid shop catalog: " + ex.getMessage(),ex); }
    }
    private static long price(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !value.getAsString().matches("[0-9]{1,11}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Prices must be quoted dollar amounts, such as \"25.50\"");
        long cents = new BigDecimal(value.getAsString()).movePointRight(2).longValueExact();
        if (cents < 1 || cents > MAX_UNIT_PRICE) throw new IllegalArgumentException("Unit price is out of range");
        return cents;
    }
}
