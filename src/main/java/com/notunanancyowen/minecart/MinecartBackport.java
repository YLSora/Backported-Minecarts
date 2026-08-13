package com.notunanancyowen.minecart;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Objects;

@Mod(MinecartBackport.MOD_ID)
public final class MinecartBackport {
    public static final String MOD_ID = "minecart_backport";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String NETWORK_VERSION = "1";

    public static final GameRules.Key<GameRules.IntegerValue> MINECART_MAX_SPEED = GameRules.register(
            "minecartMaxSpeed",
            GameRules.Category.MISC,
            GameRules.IntegerValue.create(8)
    );

    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            Objects.requireNonNull(ResourceLocation.tryBuild(MOD_ID, "main")),
            () -> NETWORK_VERSION,
            NETWORK_VERSION::equals,
            NETWORK_VERSION::equals
    );

    public MinecartBackport() {
        NETWORK.messageBuilder(MoveMinecartAlongTrackS2CPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MoveMinecartAlongTrackS2CPacket::encode)
                .decoder(MoveMinecartAlongTrackS2CPacket::decode)
                .consumerMainThread(MoveMinecartAlongTrackS2CPacket::handle)
                .add();
        LOGGER.info("Backported modern minecart movement");
    }
}
