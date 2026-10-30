package dev.lyn.shop;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class PlayerTiers {
    private record State(Map<String,String> tiers,Map<String,Long> lastSales) {}
    private final Path path;
    private State state;
    public PlayerTiers(Path path) throws IOException {
        this.path=path;Files.createDirectories(path.getParent());
        if(Files.exists(path)) {
            try { state=new GsonBuilder().create().fromJson(Files.readString(path),State.class);if(state==null||state.tiers==null||state.lastSales==null)throw new IllegalArgumentException("Missing tier data"); }
            catch(RuntimeException e){throw new IOException("Invalid player tier data; restore a backup rather than resetting it",e);}
        } else {state=new State(new HashMap<>(),new HashMap<>());save();}
    }
    public String tier(UUID player,ShopCatalog catalog){String value=state.tiers.getOrDefault(player.toString(),"iron");return catalog.sellCooldownSeconds().containsKey(value)?value:"iron";}
    public long remaining(UUID player,ShopCatalog catalog,long now){return Math.max(0,state.lastSales.getOrDefault(player.toString(),0L)+catalog.sellCooldownSeconds().get(tier(player,catalog))*1000L-now);}
    public void assign(UUID player,String tier,ShopCatalog catalog)throws IOException{
        if(!catalog.sellCooldownSeconds().containsKey(tier))throw new IllegalArgumentException("Unknown tier: "+tier);
        String old=state.tiers.put(player.toString(),tier);
        try{save();}catch(IOException e){if(old==null)state.tiers.remove(player.toString());else state.tiers.put(player.toString(),old);throw e;}
    }
    public long reserve(UUID player,long now)throws IOException{
        long old=state.lastSales.getOrDefault(player.toString(),0L);state.lastSales.put(player.toString(),now);
        try{save();return old;}catch(IOException e){state.lastSales.put(player.toString(),old);throw e;}
    }
    public void cancel(UUID player,long previous)throws IOException{state.lastSales.put(player.toString(),previous);save();}
    private void save()throws IOException{
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(state));
        if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
