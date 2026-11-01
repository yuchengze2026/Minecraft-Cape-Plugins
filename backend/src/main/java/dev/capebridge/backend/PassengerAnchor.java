package dev.capebridge.backend;

/** Client-side player mount origin changed in 1.20.2, independently of cape geometry. */
final class PassengerAnchor {
    private PassengerAnchor() {}
    static float height(double playerHeight, boolean modern) {
        return (float)(playerHeight * (modern ? 1 : .75));
    }
    static float viewerOffset(double playerHeight,boolean serverModern,boolean viewerModern){
        return height(playerHeight,serverModern)-height(playerHeight,viewerModern);
    }
}
