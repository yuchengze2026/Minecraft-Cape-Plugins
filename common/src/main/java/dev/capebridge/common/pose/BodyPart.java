package dev.capebridge.common.pose;

/** Parent labels describe the exported logical skeleton, not a claim about native ModelPart parenting. */
public enum BodyPart {
    HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;
    public String parent() { return this == BODY ? "ROOT" : "BODY"; }
}
