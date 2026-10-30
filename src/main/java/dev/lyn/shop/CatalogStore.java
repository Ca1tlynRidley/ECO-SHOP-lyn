package dev.lyn.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public final class CatalogStore {
    private final Path path;
    private ShopCatalog current;
    public CatalogStore(Path path) { this.path = path; }
    public ShopCatalog current() { if(current == null) throw new IllegalStateException("Shop is unavailable. Fix prices.json and run /shopadmin reload."); return current; }
    public void load() throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) try(var input=getClass().getResourceAsStream("/default-prices.json")) { Files.write(path,java.util.Objects.requireNonNull(input).readAllBytes()); }
        if(Files.size(path)>2_000_000) throw new IllegalArgumentException("Catalog exceeds 2 MB");
        String original=Files.readString(path,StandardCharsets.UTF_8);
        var update=CatalogUpdates.prepare(original);
        ShopCatalog candidate=ShopCatalog.parse(update.json());
        for(String id:candidate.items().keySet()) if(!BuiltInRegistries.ITEM.containsKey(Identifier.parse(id))) throw new IllegalArgumentException("Unknown 26.2 item: "+id);
        if(update.changed())CatalogUpdates.persist(path,original,update.json());
        current=candidate;
    }
}
