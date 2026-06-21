package com.redandwhitefox.pocketbeacon.network;

import com.redandwhitefox.pocketbeacon.PocketBeacon;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenBeaconTrinketPayload() implements CustomPayload {
    public static final Id<OpenBeaconTrinketPayload> ID = new Id<OpenBeaconTrinketPayload>(Identifier.of(PocketBeacon.MOD_ID, "open_gui"));
    public static final PacketCodec<RegistryByteBuf, OpenBeaconTrinketPayload> CODEC = PacketCodec.unit(new OpenBeaconTrinketPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
