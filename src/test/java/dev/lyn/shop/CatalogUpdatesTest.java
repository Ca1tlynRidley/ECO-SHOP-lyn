package dev.lyn.shop;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogUpdatesTest {
    @TempDir Path temporary;
    private static String oldPrices(){return "{\"schemaVersion\":1,\"maxPurchase\":32,\"sellCooldownSeconds\":{\"iron\":7},\"custom\":\"preserve me\",\"items\":{\"minecraft:egg\":{\"category\":\"food\",\"buy\":\"37.50\",\"sell\":\"0.20\",\"enabled\":true}}}";}
    @Test void addsOnlyTheMissingDefaultEntryAndRevision()throws Exception{
        var update=CatalogUpdates.prepare(oldPrices());assertTrue(update.changed());var root=JsonParser.parseString(update.json()).getAsJsonObject();
        assertEquals("preserve me",root.get("custom").getAsString());assertEquals(1,root.get("catalogRevision").getAsInt());
        var catalog=ShopCatalog.parse(update.json());assertEquals(32,catalog.maxPurchase());assertEquals(7,catalog.sellCooldownSeconds().get("iron"));
        assertEquals(3750,catalog.items().get("minecraft:egg").buy());assertEquals(30_000,catalog.items().get("minecraft:totem_of_undying").buy());assertEquals(2,catalog.items().size());
    }
    @Test void preservesExistingCustomOrDisabledTotemEntries()throws Exception{
        for(String entry:new String[]{"{\"category\":\"utility\",\"buy\":\"450.50\",\"sell\":\"2.50\",\"enabled\":true}","{\"enabled\":false}"}){
            var root=JsonParser.parseString(oldPrices()).getAsJsonObject();var value=JsonParser.parseString(entry);root.getAsJsonObject("items").add("minecraft:totem_of_undying",value);
            var updated=JsonParser.parseString(CatalogUpdates.prepare(root.toString()).json()).getAsJsonObject();assertEquals(value,updated.getAsJsonObject("items").get("minecraft:totem_of_undying"));
            ShopCatalog.parse(updated.toString());
        }
    }
    @Test void removedItemStaysRemovedAfterTheOneTimeUpdate()throws Exception{
        var root=JsonParser.parseString(oldPrices()).getAsJsonObject();root.addProperty("catalogRevision",1);
        var update=CatalogUpdates.prepare(root.toString());assertFalse(update.changed());assertEquals(root.toString(),update.json());
    }
    @Test void writesAnExactBackupBeforeReplacingTheValidatedCatalog()throws Exception{
        Path file=temporary.resolve("prices.json");Files.writeString(file,oldPrices());var update=CatalogUpdates.prepare(oldPrices());
        CatalogUpdates.persist(file,oldPrices(),update.json());assertEquals(oldPrices(),Files.readString(temporary.resolve("prices.json.pre-totem.bak")));assertEquals(update.json(),Files.readString(file));
    }
    @Test void refusesToOverwriteConcurrentOperatorEditsOrFailedTemporaryWrites()throws Exception{
        Path file=temporary.resolve("prices.json");var update=CatalogUpdates.prepare(oldPrices());Files.writeString(file,"operator edits");
        assertThrows(IOException.class,()->CatalogUpdates.persist(file,oldPrices(),update.json()));assertEquals("operator edits",Files.readString(file));
        Files.writeString(file,oldPrices());Files.createDirectory(temporary.resolve("prices.json.totem-update.tmp"));
        assertThrows(IOException.class,()->CatalogUpdates.persist(file,oldPrices(),update.json()));assertEquals(oldPrices(),Files.readString(file));
        Path second=temporary.resolve("second-prices.json");Files.writeString(second,oldPrices());Files.createDirectory(temporary.resolve("second-prices.json.pre-totem.bak"));
        assertThrows(IOException.class,()->CatalogUpdates.persist(second,oldPrices(),update.json()));assertEquals(oldPrices(),Files.readString(second));
    }
}
