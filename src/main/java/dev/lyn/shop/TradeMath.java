package dev.lyn.shop;

import java.util.*;

/** Pure purchase planning. A failed plan never consumes money or overwrites player items. */
public final class TradeMath {
    private TradeMath() {}
    public static long total(long unit, int quantity, int maximum) {
        if (unit < 1 || quantity < 1 || quantity > maximum) throw new IllegalArgumentException("Invalid trade quantity");
        return Math.multiplyExact(unit,quantity);
    }
    public static int[] placement(int[] counts, boolean[] compatible, int stackLimit, int quantity) {
        if (counts.length != compatible.length || stackLimit < 1 || quantity < 1) throw new IllegalArgumentException("Invalid inventory plan");
        int[] result = counts.clone(); int remaining = quantity;
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < result.length && remaining > 0; slot++) {
            boolean empty = result[slot] == 0;
            if (pass == 0 && (empty || !compatible[slot]) || pass == 1 && !empty) continue;
            int added = Math.min(Math.max(0,stackLimit-result[slot]),remaining); result[slot] += added; remaining -= added;
        }
        return remaining == 0 ? result : null;
    }
    public static long damagedSell(long price, int damage, int maximumDamage) {
        if (maximumDamage <= 0) return price;
        if (damage < 0 || damage >= maximumDamage) return 0;
        return Math.max(1, Math.multiplyExact(price,(long)maximumDamage-damage)/maximumDamage);
    }
}
