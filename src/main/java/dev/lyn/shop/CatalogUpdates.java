package dev.lyn.shop;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** One-time addition of the new default item, preserving an operator's existing entries. */
public final class CatalogUpdates {
    private static final int REVISION=1;
    private static final String TOTEM="minecraft:totem_of_undying";
    public record Update(String json,boolean changed) {}
    private CatalogUpdates() {}
    public static Update prepare(String original)throws IOException{
        var root=JsonParser.parseString(original).getAsJsonObject();
        int revision=0;
        if(root.has("catalogRevision")){
            var field=root.get("catalogRevision");
            if(!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("catalogRevision must be a nonnegative integer");
            revision=field.getAsBigDecimal().intValueExact();
            if(revision<0)throw new IllegalArgumentException("catalogRevision must be nonnegative");
        }
        if(revision>=REVISION)return new Update(original,false);
        var items=root.getAsJsonObject("items");
        if(items==null)throw new IllegalArgumentException("Catalog items are missing");
        if(!items.has(TOTEM)){
            try(var stream=CatalogUpdates.class.getResourceAsStream("/default-prices.json")){
                if(stream==null)throw new IOException("Default price catalog is missing");
                var defaults=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                items.add(TOTEM,defaults.getAsJsonObject("items").getAsJsonObject(TOTEM).deepCopy());
            }
        }
        root.addProperty("catalogRevision",REVISION);
        return new Update(new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n",true);
    }
    public static void persist(Path file,String original,String updated)throws IOException{
        if(!Files.readString(file,StandardCharsets.UTF_8).equals(original))throw new IOException("Prices changed during catalog upgrade. Save your edits and reload again.");
        Path backup=file.resolveSibling(file.getFileName()+".pre-totem.bak");
        if(Files.exists(backup)&&!Files.isRegularFile(backup))throw new IOException("Catalog backup path is not a regular file: "+backup);
        Path temporary=file.resolveSibling(file.getFileName()+".totem-update.tmp");
        byte[] data=updated.getBytes(StandardCharsets.UTF_8);
        try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            var buffer=ByteBuffer.wrap(data);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        if(!Files.readString(file,StandardCharsets.UTF_8).equals(original))throw new IOException("Prices changed during catalog upgrade. Reload again.");
        if(!Files.exists(backup))Files.copy(file,backup);
        Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
}
