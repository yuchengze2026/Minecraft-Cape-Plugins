package dev.capebridge.backend;

/** Small separation from the outer skin/armour, with a stationary rearward opening. */
final class CapeFitting {
    // A 30-degree turn interpolates the shoulder offset along a chord. With armour,
    // .008 left less than .002 blocks of clearance at mid-tick; reserve >1/8 pixel.
    static float clearance(float configured){return CapeCloth.clamp(configured,.015f,.03f);}
    static float lift(float movingAngle,boolean crouched){return Math.max(crouched?18f:6f,movingAngle);}
    static boolean visibleTo(boolean owner,boolean preview){return !preview||owner;}
}
