package dev.lyn.shop.auction;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;

/** Auction policy is independent of the fixed-price shop catalog. */
public record AuctionConfig(boolean enabled, int maxListingsPerPlayer, int maxActiveListings,
                            int defaultBuyNowHours, int defaultAuctionHours, int maxDurationHours,
                            long defaultPriceCents, long minimumBidIncrementCents, int bidIncrementPercent,
                            int antiSnipeSeconds, int maxItemBytes, List<String> blacklist) {
    private static final Pattern RULE=Pattern.compile("[a-z0-9_.-]+:(?:\\*|[a-z0-9_./-]+)");
    public AuctionConfig {
        blacklist=List.copyOf(blacklist);
        if(maxListingsPerPlayer<1||maxListingsPerPlayer>1000||maxActiveListings<1||maxActiveListings>10000
            ||maxDurationHours<1||maxDurationHours>720||defaultBuyNowHours<1||defaultBuyNowHours>maxDurationHours
            ||defaultAuctionHours<1||defaultAuctionHours>maxDurationHours||defaultPriceCents<1
            ||defaultPriceCents>dev.lyn.eco.api.Money.MAX_BALANCE||minimumBidIncrementCents<1
            ||minimumBidIncrementCents>dev.lyn.eco.api.Money.MAX_BALANCE||bidIncrementPercent<0||bidIncrementPercent>100
            ||antiSnipeSeconds<0||antiSnipeSeconds>3600||maxItemBytes<1024||maxItemBytes>4*1024*1024
            ||blacklist.stream().anyMatch(rule->rule==null||!RULE.matcher(rule).matches()))
            throw new IllegalArgumentException("Invalid auction configuration");
    }
    public static AuctionConfig defaults(){return new AuctionConfig(true,20,5000,48,24,72,1000,100,5,120,1024*1024,List.of());}
    public boolean allows(String id){return !blacklist.contains(id)&&blacklist.stream().noneMatch(rule->rule.endsWith(":*")&&id.startsWith(rule.substring(0,rule.length()-1)));}
    public static AuctionConfig load(Path file)throws IOException{
        Files.createDirectories(file.getParent());
        var gson=new GsonBuilder().setPrettyPrinting().create();
        if(!Files.exists(file)){var defaults=defaults();Files.writeString(file,gson.toJson(defaults));return defaults;}
        try{var config=gson.fromJson(Files.readString(file),AuctionConfig.class);if(config==null)throw new IllegalArgumentException("Empty auction config");return config;}
        catch(RuntimeException e){throw new IOException("Invalid auction configuration: "+file,e);}
    }
}
