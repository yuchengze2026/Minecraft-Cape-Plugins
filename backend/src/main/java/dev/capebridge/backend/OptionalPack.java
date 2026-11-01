package dev.capebridge.backend;

final class OptionalPack {
    private OptionalPack() {}
    static boolean unavailable(String status){
        return switch(status){
            case "DECLINED","FAILED_DOWNLOAD","FAILED_RELOAD","INVALID_URL","DISCARDED" -> true;
            default -> false;
        };
    }
    static boolean visible(boolean wearerReady,boolean viewerReady){return wearerReady&&viewerReady;}
}
