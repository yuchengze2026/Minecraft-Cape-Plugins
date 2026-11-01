package dev.capebridge.common;

/** Shared layout for the four articulated strips and their resource-pack models. */
public final class CapeMesh {
    public static final int PARTS = 4;
    public static final float LENGTH = 1f / PARTS;
    public static final float WIDTH = .625f;
    public static final float THICKNESS = 1f / 16;
    public static final int SELF_COLOR = 0x0D17E3;
    public static final int DEFAULT_WINGS = 1;
    private CapeMesh() {}
    public static int attachedModel(int base){
        if(base<1)throw new IllegalArgumentException("Invalid cape model");
        return Math.addExact(Math.multiplyExact(base,16),7);
    }
    public static int smoothModel(int base,int profile){
        if(base<1||profile<0||profile>=6)throw new IllegalArgumentException("Invalid smooth cape model");
        return Math.addExact(Math.multiplyExact(base,16),8+profile);
    }
    public static int model(int base, int part) {
        if (base < 1 || part < 0 || part >= PARTS) throw new IllegalArgumentException("Invalid cloth model");
        return Math.addExact(Math.multiplyExact(base, 16), part + 1);
    }
    public static int wingModel(int base,int side) {
        if(base<1||side<0||side>1)throw new IllegalArgumentException("Invalid wing model");
        return Math.addExact(Math.multiplyExact(base,16),5+side);
    }
}
