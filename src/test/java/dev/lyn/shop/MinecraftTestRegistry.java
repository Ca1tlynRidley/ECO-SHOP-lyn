package dev.lyn.shop;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;

/** Register a real non-vanilla test item/component before bootstrap freezes the registries. */
public final class MinecraftTestRegistry {
    public static Item modItem;
    public static DataComponentType<String> modComponent;
    public static HolderLookup.Provider lookup;
    private MinecraftTestRegistry() {}
    public static synchronized void init(){
        if(lookup!=null)return;
        SharedConstants.tryDetectVersion();
        // Standalone unit tests have no Fabric pre-freeze registration hook. Temporarily enter the
        // bootstrap phase, register test content, then let normal vanilla bootstrap finish and freeze.
        try{
            var flag=Bootstrap.class.getDeclaredField("isBootstrapped");flag.setAccessible(true);flag.setBoolean(null,true);
            try{
                var id=Identifier.parse("auction_test:custom_disc");
                modComponent=Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,Identifier.parse("auction_test:disc_url"),DataComponentType.<String>builder().persistent(Codec.STRING).build());
                modItem=Registry.register(BuiltInRegistries.ITEM,id,new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id))));
            }finally{flag.setBoolean(null,false);}
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Minecraft bootstrap test fixture changed",e);}
        Bootstrap.bootStrap();lookup=net.minecraft.data.registries.VanillaRegistries.createLookup();
        for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup))pending.apply();
    }
}
