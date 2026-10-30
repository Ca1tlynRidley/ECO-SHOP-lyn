package dev.lyn.shop;

import java.util.Set;

/** Keep rare rewards earned through gameplay, and never list modded/creative-only items. */
public final class CatalogPolicy {
    private CatalogPolicy() {}
    private static final Set<String> EXCLUDED = Set.of(
        "air", "cave_air", "void_air", "bedrock", "barrier", "light", "debug_stick", "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
        "structure_block", "structure_void", "jigsaw", "spawner", "trial_spawner", "vault", "end_portal_frame", "reinforced_deepslate", "budding_amethyst", "test_block", "test_instance_block",
        "dragon_egg", "dragon_head", "player_head", "wither_skeleton_skull", "skeleton_skull", "zombie_head", "creeper_head", "piglin_head", "elytra", "mace", "heavy_core", "trident",
        "nether_star", "beacon", "enchanted_golden_apple", "totem_of_undying", "heart_of_the_sea", "conduit", "dragon_breath", "end_crystal", "ancient_debris", "sniffer_egg", "turtle_egg",
        "trial_key", "ominous_trial_key", "sponge", "wet_sponge", "knowledge_book", "written_book", "enchanted_book", "filled_map", "potion", "splash_potion", "lingering_potion", "tipped_arrow",
        "suspicious_stew", "ominous_bottle", "suspicious_sand", "suspicious_gravel", "frogspawn", "breeze_rod", "echo_shard", "recovery_compass", "nautilus_shell", "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar", "purpur_slab", "purpur_stairs", "chorus_flower", "chorus_fruit", "popped_chorus_fruit", "end_rod", "shulker_shell", "shulker_box"
    );
    public static boolean allowed(String id) {
        if (id == null || !id.matches("minecraft:[a-z0-9_]+")) return false;
        String path = id.substring(10);
        return !EXCLUDED.contains(path) && !path.contains("netherite") && !path.endsWith("_spawn_egg")
            && !path.startsWith("music_disc_") && !path.endsWith("_pottery_sherd") && !path.endsWith("_armor_trim_smithing_template")
            && !path.endsWith("_shulker_box") && !path.startsWith("infested_") && !path.contains("sherd");
    }
}
