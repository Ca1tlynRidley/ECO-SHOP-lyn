package dev.lyn.shop.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lyn.eco.api.Money;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable escrow book. An unfinished external operation is never automatically replayed. */
public final class AuctionStore {
    public enum Kind { BUY_NOW, BIDDING }
    public record Lot(String data,String itemId,String name,int count) {
        public Lot {Objects.requireNonNull(data);Objects.requireNonNull(itemId);Objects.requireNonNull(name);
            if(data.isBlank()||data.length()>6*1024*1024||!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||count<1||count>99)throw new IllegalArgumentException("Invalid auction item");}
    }
    public record Listing(UUID id,UUID seller,String sellerName,Lot lot,Kind kind,long price,long created,long expires,
                          UUID bidder,String bidderName,long bid,int revision) {}
    public record Mail(UUID id,UUID owner,Lot lot,long cents,String reason,long created) {}
    public record Book(List<Listing> listings,List<Mail> mail) {
        public Book {listings=List.copyOf(listings);mail=List.copyOf(mail);}
    }
    public record Pending(UUID id,String kind,UUID actor,String actorName,String details,long created,Book after) {}
    public record State(int schemaVersion,UUID escrow,Book book,Pending pending) {}
    @FunctionalInterface public interface Writer {void write(Path file,String json)throws IOException;}
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_FILE_BYTES=128L*1024*1024;
    private final Path file;
    private final Writer writer;
    private State state;

    public AuctionStore(Path file)throws IOException{this(file,AuctionStore::atomicWrite);}
    public AuctionStore(Path file,Writer writer)throws IOException{
        this.file=file;this.writer=writer;Files.createDirectories(file.getParent());
        if(Files.exists(file)){
            if(Files.size(file)>MAX_FILE_BYTES)throw new IOException("Auction data exceeds size limit");
            try{state=GSON.fromJson(Files.readString(file),State.class);validate(state);}
            catch(RuntimeException e){throw new IOException("Invalid auction data; restore a verified backup, never reset it: "+file,e);}
        }else{
            if(Files.exists(backup(file)))throw new IOException("Auction data is missing but a backup exists. Restore it; refusing to reset escrow.");
            save(new State(1,UUID.randomUUID(),new Book(List.of(),List.of()),null));
        }
    }
    public State state(){return state;}
    public void update(Book book)throws IOException{requireClear();save(new State(1,state.escrow(),book,null));}
    public Pending prepare(String kind,UUID actor,String actorName,String details,long now,Book after)throws IOException{
        requireClear();Pending pending=new Pending(UUID.randomUUID(),kind,actor,actorName,details,now,after);
        save(new State(1,state.escrow(),state.book(),pending));return pending;
    }
    public void complete()throws IOException{
        if(state.pending()==null)throw new IllegalStateException("No pending auction operation");
        save(new State(1,state.escrow(),state.pending().after(),null));
    }
    public void abort()throws IOException{save(new State(1,state.escrow(),state.book(),null));}
    public void requireClear(){if(state.pending()!=null)throw new IllegalStateException("Auction house is paused for administrator review. Reference: "+state.pending().id());}
    private void save(State next)throws IOException{
        validate(next);String json=GSON.toJson(next);
        if(json.getBytes(StandardCharsets.UTF_8).length>MAX_FILE_BYTES)throw new IOException("Auction store is full; collect outstanding items before creating more listings");
        writer.write(file,json);state=next;
    }
    private static void validate(State s){
        if(s==null||s.schemaVersion()!=1||s.escrow()==null)throw new IllegalArgumentException("Invalid auction schema");
        validateBook(s.book(),s.escrow());
        if(s.pending()!=null){var p=s.pending();Objects.requireNonNull(p.id());Objects.requireNonNull(p.actor());Objects.requireNonNull(p.kind());Objects.requireNonNull(p.details());validateBook(p.after(),s.escrow());}
    }
    private static void validateBook(Book book,UUID escrow){
        Set<UUID> ids=new HashSet<>();
        for(var l:book.listings()){
            if(l.id()==null||!ids.add(l.id())||l.seller()==null||l.seller().equals(escrow)||l.sellerName()==null||l.lot()==null||l.kind()==null
                ||l.price()<1||l.price()>Money.MAX_BALANCE||l.expires()<=l.created()||l.revision()<0
                ||l.bid()<0||l.bid()>Money.MAX_BALANCE||(l.bidder()==null)!=(l.bid()==0)
                ||l.bidder()!=null&&(l.bid()<l.price()||l.bidder().equals(l.seller())||l.bidder().equals(escrow)||l.bidderName()==null)
                ||l.kind()==Kind.BUY_NOW&&l.bidder()!=null)throw new IllegalArgumentException("Invalid auction listing");
        }
        for(var m:book.mail())if(m.id()==null||!ids.add(m.id())||m.owner()==null||m.owner().equals(escrow)||m.reason()==null
            ||m.cents()<0||m.cents()>Money.MAX_BALANCE||(m.lot()==null)==(m.cents()==0))throw new IllegalArgumentException("Invalid auction collection entry");
        reserved(book);
    }
    public static long reserved(Book book){long total=0;for(var l:book.listings())total=Math.addExact(total,l.bid());for(var m:book.mail())total=Math.addExact(total,m.cents());return total;}
    private static Path backup(Path file){return file.resolveSibling(file.getFileName()+".bak");}
    public static void atomicWrite(Path file,String json)throws IOException{
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            var bytes=ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
        }
        if(Files.exists(file))Files.copy(file,backup(file),StandardCopyOption.REPLACE_EXISTING);
        Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
}
